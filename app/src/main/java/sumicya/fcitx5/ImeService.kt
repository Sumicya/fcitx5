package sumicya.fcitx5

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import sumicya.fcitx5.engine.Engine
import sumicya.fcitx5.keyboard.Key
import sumicya.fcitx5.keyboard.KeyboardView
import sumicya.fcitx5.ui.CandidateBar

class ImeService : InputMethodService() {

    private val engine = Engine()
    private var candidates: List<String> = emptyList()
    private lateinit var keyboard: KeyboardView
    private lateinit var candidateBar: CandidateBar

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        candidateBar = CandidateBar(this) { text ->
            commit(text)
            engine.clear()
            updateCandidates()
        }
        keyboard = KeyboardView(this).apply { listener = keyListener }
        root.addView(candidateBar, LinearLayout.LayoutParams(MATCH, dp(44)))
        root.addView(keyboard, LinearLayout.LayoutParams(MATCH, dp(216)))
        updateCandidates()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        engine.clear()
        updateCandidates()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        engine.clear()
        currentInputConnection?.finishComposingText()
        super.onFinishInputView(finishingInput)
    }

    private val keyListener = object : KeyboardView.Listener {

        override fun onKey(key: Key, gesture: KeyboardView.Gesture) {
            when (key.type) {
                Key.Type.DELETE -> onDelete(gesture)
                Key.Type.ENTER -> onEnter()
                Key.Type.SPACE -> if (gesture == KeyboardView.Gesture.TAP) {
                    if (engine.isComposing()) pick(0) else commit(" ")
                }
                Key.Type.SHIFT -> keyboard.shift = !keyboard.shift
                Key.Type.LAYER -> keyboard.toggleLayer()
                Key.Type.MODE -> toggleMode()
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
                currentInputConnection?.setComposingText(engine.preeditText(), 1)
                updateCandidates()
                return
            }
            CN_PUNCTUATION[c]?.let {
                commit(it.toString())
                return
            }
        }
        commit(text)
    }

    private fun pick(index: Int) {
        val word = candidates.getOrNull(index) ?: return
        commit(word)
        engine.clear()
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

    private fun updateCandidates() {
        candidates = engine.candidates()
        candidateBar.setCandidates(candidates)
    }

    private fun onDelete(gesture: KeyboardView.Gesture) {
        if (engine.isComposing()) {
            engine.backspace()
            val ic = currentInputConnection
            if (engine.isComposing()) ic?.setComposingText(engine.preeditText(), 1) else ic?.finishComposingText()
            updateCandidates()
            return
        }
        if (gesture == KeyboardView.Gesture.SWIPE_LEFT) deleteWord() else currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun onEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnter = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnter && action != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(action)
        } else {
            commit("\n")
        }
    }

    private fun toggleMode() {
        engine.chinese = !engine.chinese
        keyboard.modeLabel = if (engine.chinese) "中" else "英"
        if (!engine.chinese && engine.isComposing()) {
            val pending = engine.preeditText()
            engine.clear()
            commit(pending)
        }
    }

    /** ponytail: cursor offset is derived from the text before the cursor, capped at 4096 chars. */
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val CN_PUNCTUATION = mapOf(
            ',' to '，', '.' to '。', '?' to '？', '!' to '！',
            ':' to '：', ';' to '；', '(' to '（', ')' to '）',
        )
    }
}
