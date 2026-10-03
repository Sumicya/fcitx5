package sumicya.fcitx5.engine

/**
 * Every way to split a raw pinyin string into syllables, dead ends included.
 *
 * ponytail: it is out of the engine so a test can hold it to the splits it owes
 * the user — an initial, a shorthand, a full spelling — without needing an
 * Android context.
 */
object Segmenter {

    /**
     * One split. [penalty] is what it cost in shorthands and initials, so the
     * caller can rank it below the same word spelt out in full.
     */
    class Path(
        val ids: IntArray,
        val length: Int,
        val consumed: Int,
        val penalty: Int,
        val initials: Int
    )

    fun split(syllables: Syllables, text: String, maxPaths: Int = MAX_PATHS): List<Path> {
        val out = ArrayList<Path>()
        val ids = IntArray(Syllables.MAX_PER_WORD)

        fun walk(pos: Int, depth: Int, penalty: Int, initials: Int) {
            if (out.size >= maxPaths) return
            val done = { out.add(Path(ids.copyOf(), depth, pos, penalty, initials)) }
            when {
                pos == text.length -> if (depth > 0) done()
                depth == Syllables.MAX_PER_WORD -> if (depth > 0) done()
                else -> {
                    val matches = syllables.matchesAt(text, pos)
                    if (matches.isEmpty()) {
                        if (depth > 0) done()
                    } else {
                        for (match in matches) {
                            ids[depth] = match.id
                            walk(match.end, depth + 1, penalty + match.penalty, initials + match.initials)
                        }
                    }
                }
            }
        }

        walk(0, 0, 0, 0)
        return out
    }

    const val MAX_PATHS = 256
}
