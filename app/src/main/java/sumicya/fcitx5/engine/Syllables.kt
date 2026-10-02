package sumicya.fcitx5.engine

/** Pinyin syllables shipped inside the dictionary. Ids are 1-based, 0 is padding. */
class Syllables(private val names: List<String>) {

    private val ids = HashMap<String, Int>(names.size * 2).apply {
        names.forEachIndexed { index, name -> put(name, index + 1) }
    }

    val size: Int get() = names.size

    /** Syllables starting at [from], longest first. */
    fun matchesAt(text: String, from: Int): List<Match> {
        val out = ArrayList<Match>(4)
        val limit = minOf(text.length, from + MAX_LENGTH)
        for (end in limit downTo from + 1) {
            val id = ids[text.substring(from, end)] ?: continue
            out.add(Match(id, end))
        }
        return out
    }

    fun name(id: Int): String = names.getOrElse(id - 1) { "" }

    /** 0 when the syllable is unknown. */
    fun idOf(name: String): Int = ids[name] ?: 0

    data class Match(val id: Int, val end: Int)

    companion object {
        const val MAX_LENGTH = 6
        const val MAX_PER_WORD = 7
        const val SYLLABLE_BITS = 9
        const val SYLLABLE_MASK = 0x1FFL
    }
}
