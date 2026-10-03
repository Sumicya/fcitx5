package sumicya.fcitx5.engine

/** Pinyin syllables shipped inside the dictionary. Ids are 1-based, 0 is padding. */
class Syllables(private val names: List<String>) {

    private val ids = HashMap<String, Int>(names.size * 2).apply {
        names.forEachIndexed { index, name -> put(name, index + 1) }
    }

    val size: Int get() = names.size

    /**
     * Syllables starting at [from], longest first.
     *
     * Three ways in, because people spell pinyin three ways:
     *
     * - the syllable itself;
     * - a shorthand they type instead of it ([ALIASES]), ui for wei and so on;
     * - a single letter standing for the initial of a syllable, which is how
     *   abbreviated pinyin is spelt: 银行 is yin'hang, and it is also typed yh.
     *   The dictionary ships the initials as the upper case syllables A..Z.
     *
     * The shorthands and the initials carry a penalty rather than being free: a
     * full spelling has to outrank them, or 牛 would lose to 你有.
     */
    fun matchesAt(text: String, from: Int): List<Match> {
        val out = ArrayList<Match>(5)
        val limit = minOf(text.length, from + MAX_LENGTH)
        for (end in limit downTo from + 1) {
            val part = text.substring(from, end)
            val exact = ids[part]
            if (exact != null) {
                out.add(Match(exact, end, 0, 0))
                continue
            }
            val alias = ALIASES[part] ?: continue
            val id = ids[alias] ?: continue
            out.add(Match(id, end, ALIAS_PENALTY, 0))
        }
        // a single letter is only an initial when it is not a syllable itself:
        // a is 啊 before it is the initial of 爱
        if (limit > from && out.none { it.end == from + 1 }) {
            val initial = ids[text.substring(from, from + 1).uppercase()]
            if (initial != null) out.add(Match(initial, from + 1, INITIAL_PENALTY, 1))
        }
        return out
    }

    fun name(id: Int): String = names.getOrElse(id - 1) { "" }

    /** 0 when the syllable is unknown. */
    fun idOf(name: String): Int = ids[name] ?: 0

    /** [Match.penalty] and [Match.initials] of one syllable of a split. */
    data class Match(val id: Int, val end: Int, val penalty: Int, val initials: Int)

    companion object {
        const val MAX_LENGTH = 6
        const val MAX_PER_WORD = 7
        const val SYLLABLE_BITS = 9
        const val SYLLABLE_MASK = 0x1FFL

        /** What a shorthand and an initial cost, on the 0..65535 score scale. */
        const val ALIAS_PENALTY = 4000
        const val INITIAL_PENALTY = 8000

        /**
         * Spellings people type that are not syllables: the shorthand finals
         * (ui for wei, iu for you, un for wen) and their full forms.
         */
        private val ALIASES = mapOf(
            "ui" to "wei", "iu" to "you", "un" to "wen", "ong" to "weng",
            "uei" to "wei", "iou" to "you", "uen" to "wen",
        )
    }
}
