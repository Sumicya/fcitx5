package sumicya.fcitx5.engine

import android.content.Context

/**
 * Composition state on top of [PinyinDict].
 *
 * Typed letters are split into syllable sequences; every sequence that covers
 * the whole input (or, failing that, the longest prefix that has matches) is
 * looked up and the results are merged by score.
 */
class PinyinEngine(context: Context) {

    data class Candidate(val word: String, val consumed: Int)

    var chinese: Boolean = true

    /** Serve candidates in traditional characters. */
    var traditional: Boolean = false
        set(value) {
            field = value
            refresh()
        }

    private val dict = PinyinDict.load(context)
    private val user = UserDict(context)
    private val preedit = StringBuilder()
    private var candidates: List<Candidate> = emptyList()

    init {
        Trad.load(context)
    }

    fun isComposing() = preedit.isNotEmpty()

    fun preeditText() = preedit.toString()

    fun candidates(): List<Candidate> {
        if (!traditional) return candidates
        val out = ArrayList<Candidate>(candidates.size)
        val seen = HashSet<String>()
        for (it in candidates) {
            val word = Trad.convert(it.word)
            if (seen.add(word)) out.add(Candidate(word, it.consumed))
        }
        return out
    }

    fun type(c: Char) {
        preedit.append(c.lowercaseChar())
        refresh()
    }

    fun backspace(): Boolean {
        if (preedit.isEmpty()) return false
        preedit.deleteCharAt(preedit.length - 1)
        refresh()
        return true
    }

    fun clear() {
        preedit.setLength(0)
        candidates = emptyList()
    }

    /** Commit the candidate at [index], dropping the syllables it covers. */
    fun pick(index: Int): String? {
        val candidate = candidates.getOrNull(index) ?: return null
        user.bump(candidate.word)
        preedit.delete(0, candidate.consumed)
        refresh()
        return if (traditional) Trad.convert(candidate.word) else candidate.word
    }

    fun save() = user.save()

    private fun refresh() {
        candidates = compute()
    }

    private fun compute(): List<Candidate> {
        val table = dict ?: return emptyList()
        val text = preedit.toString()
        if (text.isEmpty()) return emptyList()
        val paths = segment(table.syllables, text)
        val lengths = paths.map { it.consumed }.distinct().sortedDescending()
        for (length in lengths) {
            val result = collect(table, paths.filter { it.consumed == length }, length)
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    private fun collect(table: PinyinDict, paths: List<Path>, consumed: Int): List<Candidate> {
        val scored = ArrayList<Pair<String, Int>>(64)
        for (path in paths) {
            for (entry in table.lookup(table.keyOf(path.ids, path.length))) {
                scored.add(entry.word to entry.score + pickedBonus(entry.word))
            }
        }
        scored.sortByDescending { it.second }
        val seen = HashSet<String>()
        val out = ArrayList<Candidate>(MAX_CANDIDATES)
        for ((word, _) in scored) {
            if (seen.add(word)) out.add(Candidate(word, consumed))
            if (out.size >= MAX_CANDIDATES) break
        }
        return out
    }

    /** Every syllable split of [text], dead ends included, capped at [MAX_PATHS]. */
    private fun segment(syllables: Syllables, text: String): List<Path> {
        val out = ArrayList<Path>()
        val ids = IntArray(Syllables.MAX_PER_WORD)

        fun walk(pos: Int, depth: Int) {
            if (out.size >= MAX_PATHS) return
            val done = { out.add(Path(ids.copyOf(), depth, pos)) }
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
                            walk(match.end, depth + 1)
                        }
                    }
                }
            }
        }

        walk(0, 0)
        return out
    }

    private fun pickedBonus(word: String): Int {
        val count = user.count(word)
        return if (count == 0) 0 else UserDict.PICKED_BONUS + count * UserDict.REPEAT_BONUS
    }

    private class Path(val ids: IntArray, val length: Int, val consumed: Int)

    private companion object {
        const val MAX_CANDIDATES = 40
        const val MAX_PATHS = 64
    }
}
