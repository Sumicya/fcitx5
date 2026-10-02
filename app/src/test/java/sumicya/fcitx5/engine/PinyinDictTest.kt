package sumicya.fcitx5.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The one check the dictionary pipeline has: it reads the asset packed by
 * scripts/build_dict.py, so a broken format, a bad offset or a ranking
 * regression fails the build instead of shipping a silent empty keyboard.
 */
class PinyinDictTest {

    private fun dict(): PinyinDict {
        val file = File("src/main/assets/pinyin.dict")
        check(file.exists()) { "missing ${file.absolutePath}: run scripts/build_dict.py first" }
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        return PinyinDict.parse(buffer) ?: error("bad magic: not a packed dictionary")
    }

    @Test
    fun theWholeSyllableTableIsThere() {
        val dict = dict()
        assertEquals(444, dict.syllables.size)
        assertEquals("zhong", dict.syllables.name(dict.syllables.idOf("zhong")))
    }

    @Test
    fun commonWordsAreFound() {
        val dict = dict()
        assertTrue(dict.lookup(listOf("ni", "hao")).any { it.word == "你好" })
        assertTrue(dict.lookup(listOf("wo", "men")).any { it.word == "我们" })
        assertTrue(dict.lookup(listOf("zhong", "guo")).any { it.word == "中国" })
        assertTrue(dict.lookup(listOf("shu", "ru", "fa")).any { it.word == "输入法" })
    }

    @Test
    fun dumpSingleSyllableKeys() {
        val dict = dict()
        for (syllable in listOf("wo", "de", "ni", "hao", "yi")) {
            val entries = try {
                dict.lookup(listOf(syllable))
            } catch (e: Exception) {
                println("DICTDEBUG $syllable THREW $e")
                emptyList<PinyinDict.Entry>()
            }
            println("DICTDEBUG $syllable n=${entries.size} -> ${entries.take(10).map { "${it.word}:${it.score}" }}")
        }
    }

    @Test
    fun commonCharactersRankFirst() {
        val dict = dict()
        val top = { s: String -> dict.lookup(listOf(s)).firstOrNull()?.word }
        assertEquals("DICTDEBUG first for wo", "我", top("wo"))
        assertEquals("DICTDEBUG first for de", "的", top("de"))
        assertEquals("DICTDEBUG first for ni", "你", top("ni"))
    }

    @Test
    fun candidatesAreOrderedByScore() {
        val dict = dict()
        val scores = dict.lookup(listOf("de")).map { it.score }
        assertEquals("DICTDEBUG de scores", scores.sortedDescending(), scores)
    }
}
