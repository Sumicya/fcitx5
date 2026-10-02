package sumicya.fcitx5.engine

/**
 * Composition state.
 *
 * ponytail: candidate lookup is filled in by the dictionary phase; until then
 * pinyin letters are only buffered, so the keyboard is testable without data.
 */
class Engine {

    var chinese: Boolean = true

    private val preedit = StringBuilder()

    fun isComposing() = preedit.isNotEmpty()

    fun type(c: Char) {
        preedit.append(c)
    }

    fun backspace(): Boolean {
        if (preedit.isEmpty()) return false
        preedit.deleteCharAt(preedit.length - 1)
        return true
    }

    fun clear() = preedit.setLength(0)

    fun preeditText(): String = preedit.toString()

    fun candidates(): List<String> = emptyList()
}
