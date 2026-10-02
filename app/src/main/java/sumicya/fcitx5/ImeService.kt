package sumicya.fcitx5

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import sumicya.fcitx5.data.ClipboardStore
import sumicya.fcitx5.engine.PinyinEngine
import sumicya.fcitx5.keyboard.Key
import sumicya.fcitx5.keyboard.KeyboardView
import sumicya.fcitx5.ui.CandidateBar
import sumicya.fcitx5.ui.Panel

class ImeService : InputMethodService() {

    private val engine by lazy { PinyinEngine(this) }
    private val clipboard by lazy { ClipboardStore(this) }
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
            onPick = { commit(it); showPanel(false) },
            onClose = { showPanel(false) },
        ).apply {
            onPin = { engine.phrases.add(it); showPhrases() }
            onRemove = { engine.phrases.remove(it); showPhrases() }
        }
        root.addView(candidateBar, LinearLayout.LayoutParams(MATCH, dp(44f)))
        root.addView(keyboard, LinearLayout.LayoutParams(MATCH, keyboardHeight()))
        root.addView(panel, LinearLayout.LayoutParams(MATCH, keyboardHeight()))
        panel.visibility = View.GONE
        updateCandidates()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        engine.clear()
        engine.traditional = Prefs.traditional(this)
        fieldOverridden = false
        applyMode(info)
        showPanel(false)
        clipboard.refresh()
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
            if (engine.isComposing() && c.isDigit() && c != '0') {
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
        currentInputConnection?.commitText(word, 1)
        syncComposing()
        updateCandidates()
    }

    private fun commit(text: String) {
        val ic = currentInputConnection ?: return
        if (engine.isComposing()) {
            ic.finishComposingText()
            engine.clear()
        }
        ic.commitText(text, 1)
    }

    private fun syncComposing() {
        val ic = currentInputConnection ?: return
        if (engine.isComposing()) ic.setComposingText(engine.preeditText(), 1) else ic.finishComposingText()
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
        if (gesture == KeyboardView.Gesture.SWIPE_LEFT) deleteWord() else currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        // enter commits the raw pinyin: the user wanted letters, not a candidate
        if (engine.isComposing()) {
            val pending = engine.preeditText()
            engine.clear()
            ic.finishComposingText()
            ic.commitText(pending, 1)
            updateCandidates()
            return
        }
        val info = currentInputEditorInfo
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnter = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnter && action != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
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
        if (n > 0) ic.deleteSurroundingText(n, 0)
    }

    private fun keyboardHeight(): Int {
        val base = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 180 else 216
        return dp(base * Prefs.heightPercent(this) / 100f)
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val CN_PUNCTUATION = mapOf(
            ',' to '，', '.' to '。', '?' to '？', '!' to '！',
            ':' to '：', ';' to '；', '(' to '（', ')' to '）',
        )
    }
}
