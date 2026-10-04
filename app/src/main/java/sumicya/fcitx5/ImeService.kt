package sumicya.fcitx5

import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.util.Log
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import sumicya.fcitx5.data.ClipboardStore
import sumicya.fcitx5.engine.PinyinEngine
import sumicya.fcitx5.keyboard.Key
import sumicya.fcitx5.keyboard.KeyboardView
import sumicya.fcitx5.keyboard.Keys
import sumicya.fcitx5.ui.CandidateBar
import sumicya.fcitx5.ui.Panel
import sumicya.fcitx5.ui.Theme
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ImeService : InputMethodService() {

    private val engine by lazy { PinyinEngine(this) }
    private val clipboard by lazy { ClipboardStore(this) }

    /**
     * Writes to the editor, in order, without holding the keyboard's main thread.
     *
     * commitText is a blocking binder call: the app inserts the text and lays it
     * out before the call returns, so pasting a long clip froze the keyboard for
     * as long as the app took. Long commits run on [edits] instead, and [queued]
     * keeps whatever is typed while one is in flight behind it rather than
     * letting it jump ahead.
     */
    private val edits = Executors.newSingleThreadExecutor()
    private val queued = AtomicInteger()

    private fun edit(work: (InputConnection) -> Unit) {
        val ic = currentInputConnection ?: return
        if (queued.get() == 0) work(ic) else enqueue(work)
    }

    private fun enqueue(work: (InputConnection) -> Unit) {
        val ic = currentInputConnection ?: return
        queued.incrementAndGet()
        edits.execute {
            try {
                work(ic)
            } catch (_: Exception) {
                // the editor can be gone by the time this runs
            } finally {
                queued.decrementAndGet()
            }
        }
    }

    private var candidates: List<PinyinEngine.Candidate> = emptyList()
    private var wantsChinese = true
    private var fieldOverridden = false

    private lateinit var root: LinearLayout
    private lateinit var keyboard: KeyboardView
    private lateinit var candidateBar: CandidateBar
    private lateinit var panel: Panel

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        candidateBar = CandidateBar(this) { pick(it) }
        keyboard = KeyboardView(this).apply { listener = keyListener }
        panel = Panel(
            this,
            // the panel closes first: pasting a long clip blocks for a while
            onPick = { text -> showPanel(false); commit(text); updateCandidates() },
            onClose = { showPanel(false) },
        ).apply {
            onPin = { engine.phrases.add(it); showPhrases() }
            onRemove = { engine.phrases.remove(it); showPhrases() }
        }
        // the panel's tabs sit above the candidates: they are the keyboard's top
        // row while the panel is open, and the two share the slot so opening it
        // costs no height
        root.addView(panel.tabs, LinearLayout.LayoutParams(MATCH, dp(Theme.TOUCH)))
        root.addView(candidateBar, LinearLayout.LayoutParams(MATCH, dp(Theme.TOUCH)))
        root.addView(keyboard, LinearLayout.LayoutParams(MATCH, keyboardHeight()))
        root.addView(panel, LinearLayout.LayoutParams(MATCH, keyboardHeight()))
        panel.visibility = View.GONE
        panel.tabs.visibility = View.GONE
        updateCandidates()
        return root
    }

    override fun onWindowShown() {
        super.onWindowShown()
        applyHeight()
        padForNavigationBar()
    }

    /**
     * Since Android 15 the input view is drawn to the bottom edge and nothing
     * pads it: the gesture bar sits on the last row.
     *
     * Measured, not read from the insets — the framework's decor has already
     * eaten them when it did pad, and they are zero when it did not. What the
     * keyboard pays for is the part of the bar really under it, and that is
     * 48dp with three button navigation, so a capped pad leaves the last row
     * under the buttons: this is the second attempt at it, the first capped the
     * pad at 32dp and measured before the window had a size.
     */
    private fun padForNavigationBar() {
        // the window can still be settling when the first measurement runs; the
        // pad is an amount and not an adjustment, so measuring again only
        // confirms it
        if (root.isLaidOut && !root.isLayoutRequested) {
            padToNavigationBar()
            return
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                padToNavigationBar()
            }
        })
        root.postDelayed({ padToNavigationBar() }, SETTLE_MS)
    }

    private fun padToNavigationBar() {
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val bar = barHeight()
        val display = displayHeight()
        // how much room the window leaves below itself: the bar's worth means
        // the framework already kept clear, none of it means the bar is on the
        // keys, and the pad is the difference either way
        val below = display - (location[1] + root.height)
        val pad = (bar - below).coerceIn(0, bar)
        Log.d("KEYDEBUG", "inset display=$display bottom=${location[1] + root.height} bar=$bar pad=$pad")
        if (root.paddingBottom != pad) root.setPadding(0, 0, 0, pad)
    }

    /** The navigation bar, or the gesture bar where navigation is gestures. */
    private fun barHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id > 0) {
            val size = resources.getDimensionPixelSize(id)
            if (size > 0) return size
        }
        val insets = window?.window?.decorView?.rootWindowInsets ?: return dp(24f)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
    }

    @Suppress("DEPRECATION")
    private fun displayHeight(): Int {
        // the real display, system bars included: the window is placed against
        // that, not against the area an app is allowed to draw in
        val metrics = DisplayMetrics()
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return metrics.heightPixels
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        engine.clear()
        engine.traditional = Prefs.traditional(this)
        applyHeight()
        fieldOverridden = false
        applyMode(info)
        showPanel(false)
        clipboard.refresh()
        padForNavigationBar()
        updateCandidates()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        engine.clear()
        engine.save()
        currentInputConnection?.finishComposingText()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        engine.save()
        edits.shutdown()
        super.onDestroy()
    }

    private val keyListener = object : KeyboardView.Listener {

        override fun onKey(key: Key, gesture: KeyboardView.Gesture) {
            when (key.type) {
                Key.Type.DELETE -> onDelete(gesture)
                Key.Type.ENTER -> onEnter()
                Key.Type.SPACE -> if (gesture == KeyboardView.Gesture.TAP) {
                    if (candidates.isNotEmpty()) pick(0) else commit(" ")
                }
                Key.Type.SHIFT -> keyboard.shift = !keyboard.shift
                Key.Type.LAYER -> keyboard.toggleLayer()
                Key.Type.MODE -> toggleMode()
                Key.Type.PANEL -> showPanel(panel.visibility != View.VISIBLE)
                Key.Type.CHAR -> {
                    val text = if (gesture == KeyboardView.Gesture.SWIPE_UP && key.swipe != null) {
                        key.swipe!!
                    } else {
                        key.text(keyboard.shift)
                    }
                    if (gesture == KeyboardView.Gesture.TAP && keyboard.shift &&
                        text.length == 1 && text[0].isLetter()
                    ) {
                        keyboard.shift = false
                    }
                    typeText(text)
                }
            }
        }

        override fun onSpaceDrag(chars: Int) = moveCursor(chars)
    }

    private fun typeText(text: String) {
        if (engine.chinese && text.length == 1) {
            val c = text[0]
            if (c.isLetter()) {
                engine.type(c)
                syncComposing()
                updateCandidates()
                return
            }
            // digits pick a candidate while composing, the way every IME does
            if (Prefs.digitPick(this) && engine.isComposing() && c.isDigit() && c != '0') {
                pick(c - '1')
                return
            }
            CN_PUNCTUATION[c]?.let {
                commit(it.toString())
                updateCandidates()
                return
            }
        }
        commit(text)
    }

    private fun pick(index: Int) {
        val word = engine.pick(index) ?: return
        edit { it.commitText(word, 1) }
        syncComposing()
        updateCandidates()
    }

    private fun commit(text: String) {
        if (currentInputConnection == null) return
        if (engine.isComposing()) {
            edit { it.finishComposingText() }
            engine.clear()
        }
        if (text.length <= SHORT_COMMIT) {
            edit { it.commitText(text, 1) }
        } else {
            enqueue { it.commitText(text, 1) }
        }
    }

    private fun syncComposing() {
        if (!engine.isComposing()) {
            edit { it.finishComposingText() }
            return
        }
        // taken now: through the queue the composing text would be read late
        val text = engine.preeditText()
        edit { it.setComposingText(text, 1) }
    }

    private fun updateCandidates() {
        candidates = engine.candidates()
        candidateBar.setCandidates(candidates.map { it.word })
    }

    private fun onDelete(gesture: KeyboardView.Gesture) {
        if (engine.isComposing()) {
            engine.backspace()
            syncComposing()
            updateCandidates()
            return
        }
        if (gesture == KeyboardView.Gesture.SWIPE_LEFT) deleteWord() else edit { it.deleteSurroundingText(1, 0) }
    }

    private fun onEnter() {
        if (currentInputConnection == null) return
        // enter commits the raw pinyin: the user wanted letters, not a candidate
        if (engine.isComposing()) {
            val pending = engine.preeditText()
            engine.clear()
            edit { it.finishComposingText() }
            edit { it.commitText(pending, 1) }
            updateCandidates()
            return
        }
        val info = currentInputEditorInfo
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnter = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnter && action != EditorInfo.IME_ACTION_NONE) {
            edit { it.performEditorAction(action) }
        } else {
            edit { it.commitText("\n", 1) }
        }
    }

    private fun toggleMode() {
        wantsChinese = !engine.chinese
        fieldOverridden = true
        if (!wantsChinese && engine.isComposing()) {
            val pending = engine.preeditText()
            engine.clear()
            commit(pending)
        }
        engine.chinese = wantsChinese
        keyboard.modeLabel = if (engine.chinese) "中" else "英"
        updateCandidates()
    }

    private fun applyMode(info: EditorInfo?) {
        engine.chinese = wantsChinese && (fieldOverridden || !forcesEnglish(info))
        keyboard.modeLabel = if (engine.chinese) "中" else "英"
    }

    private fun forcesEnglish(info: EditorInfo?): Boolean {
        if (info == null) return false
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME ||
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI
    }

    private fun showPanel(show: Boolean) {
        panel.visibility = if (show) View.VISIBLE else View.GONE
        keyboard.visibility = if (show) View.GONE else View.VISIBLE
        // one bar or the other, never both: candidates mean nothing while the
        // panel is open, and the tabs take their place
        panel.tabs.visibility = if (show) View.VISIBLE else View.GONE
        candidateBar.visibility = if (show) View.GONE else View.VISIBLE
        if (show) {
            clipboard.refresh()
            panel.setClips(clipboard.all())
            showPhrases()
        }
    }

    private fun showPhrases() = panel.setPhrases(engine.phrases.all())

    /** ponytail: the cursor offset comes from the text before it, capped at 4096 chars. */
    private fun moveCursor(delta: Int) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(4096, 0) ?: return
        val pos = (before.length + delta).coerceAtLeast(0)
        ic.setSelection(pos, pos)
    }

    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0) ?: return
        var n = 0
        if (before.isNotEmpty() && before[before.length - 1].isLetterOrDigit()) {
            for (i in before.length - 1 downTo 0) {
                if (!before[i].isLetterOrDigit()) break
                n++
            }
        } else {
            n = 1
        }
        if (n > 0) edit { it.deleteSurroundingText(n, 0) }
    }

    /** The height is a setting, so it is applied again every time the keyboard shows. */
    private fun applyHeight() {
        val height = keyboardHeight()
        keyboard.layoutParams = keyboard.layoutParams.apply { this.height = height }
        panel.layoutParams = panel.layoutParams.apply { this.height = height }
    }

    private fun keyboardHeight(): Int {
        val base = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Keys.HEIGHT_LANDSCAPE
        } else {
            Keys.HEIGHT_PORTRAIT
        }
        return dp(base * Prefs.heightPercent(this) / 100f)
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        /** Short enough that committing it inline cannot be felt. */
        const val SHORT_COMMIT = 4096

        /** Long enough for the window to be where it is going to stay. */
        const val SETTLE_MS = 300L
        val CN_PUNCTUATION = mapOf(
            ',' to '，', '.' to '。', '?' to '？', '!' to '！',
            ':' to '：', ';' to '；', '(' to '（', ')' to '）',
        )
    }
}
