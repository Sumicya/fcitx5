package sumicya.fcitx5.engine

import android.content.Context
import sumicya.fcitx5.data.UserPhrases

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

    /** Phrases the user saved; they are candidates even though no dictionary has them. */
    val phrases = UserPhrases(context)

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
        val paths = Segmenter.split(table.syllables, text)
        val lengths = paths.map { it.consumed }.distinct().sortedDescending()
        for (length in lengths) {
            val group = paths.filter { it.consumed == length }
            val coined = coinedPhrases(table, group, length)
            val result = collect(table, group, length)
            if (coined.isEmpty() && result.isEmpty()) continue
            return (coined + result).distinctBy { it.word }.take(MAX_CANDIDATES)
        }
        return emptyList()
    }

    /** Saved phrases are not in the dictionary, so they are matched by hand. */
    private fun coinedPhrases(table: PinyinDict, paths: List<Segmenter.Path>, consumed: Int): List<Candidate> {
        val out = ArrayList<Candidate>()
        for (path in paths) {
            // a phrase the user coined is typed in full; matching it against
            // initials would put it in front of words the spelling really means
            if (path.initials > 0) continue
            val syllables = (0 until path.length).map { table.syllables.name(path.ids[it]) }
            for (phrase in phrases.match(syllables)) {
                if (out.none { it.word == phrase }) out.add(Candidate(phrase, consumed))
            }
        }
        return out
    }

    private fun collect(table: PinyinDict, paths: List<Segmenter.Path>, consumed: Int): List<Candidate> {
        val scored = ArrayList<Pair<String, Int>>(64)
        for (path in paths) {
            for (entry in table.lookup(table.keyOf(path.ids, path.length))) {
                // what the user picked is theirs to keep, so the penalty for a
                // shorthand or an initial only touches the dictionary score
                scored.add(entry.word to entry.score - path.penalty + pickedBonus(entry.word))
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

    private fun pickedBonus(word: String): Int {
        val count = user.count(word)
        return if (count == 0) 0 else UserDict.PICKED_BONUS + count * UserDict.REPEAT_BONUS
    }

    private companion object {
        const val MAX_CANDIDATES = 64
    }
}
