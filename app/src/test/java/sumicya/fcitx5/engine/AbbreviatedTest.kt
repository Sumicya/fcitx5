package sumicya.fcitx5.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Typing only the initials (yh for 银行) and the shorthand finals (ui for wei).
 *
 * The initials index is built from the dictionary at build time and the split
 * is what lets one letter stand for an initial, so both halves are checked
 * here: either one on its own leaves the keyboard mute.
 */
class AbbreviatedTest {

    @Test
    fun initialsIndexHasEverydayWords() {
        val words = dict.lookup(listOf("W", "M")).map { it.word }
        assertTrue("我们 not in $words", "我们" in words)
        val yh = dict.lookup(listOf("Y", "H")).map { it.word }
        assertTrue("neither 银行 nor 樱花 in $yh", "银行" in yh || "樱花" in yh)
        println("DICTDEBUG initials wm=${words.take(6)} yh=${yh.take(6)}")
    }

    @Test
    fun aSingleLetterSplitsAsAnInitial() {
        val names = Segmenter.split(dict.syllables, "yh").map { path ->
            (0 until path.length).map { dict.syllables.name(path.ids[it]) }
        }
        assertTrue("yh -> $names", names.any { it == listOf("Y", "H") })
    }

    @Test
    fun aFullSpellingCostsNothing() {
        val paid = Segmenter.split(dict.syllables, "nihao").filter { it.consumed == 5 }.minOf { it.penalty }
        assertTrue("nihao pays $paid", paid == 0)
    }

    @Test
    fun shorthandFinalsReachTheirSyllable() {
        val syllables = dict.syllables
        assertTrue("ui is not a syllable in the first place", syllables.idOf("ui") == 0)
        val names = Segmenter.split(syllables, "ui").filter { it.consumed == 2 }.map { path ->
            (0 until path.length).map { syllables.name(path.ids[it]) }
        }
        assertTrue("ui -> $names", names.any { it == listOf("wei") })
    }

    private companion object {
        val dict: PinyinDict by lazy {
            val file = File("src/main/assets/pinyin.dict")
            check(file.exists()) { "missing ${file.absolutePath}: run scripts/build_dict.py first" }
            val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            PinyinDict.parse(buffer) ?: error("bad magic: not a packed dictionary")
        }
    }
}
