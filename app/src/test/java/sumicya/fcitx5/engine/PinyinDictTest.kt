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
    fun commonCharactersRankFirst() {
        val dict = dict()
        assertEquals("DICTDEBUG first for wo", "我", dict.lookup(listOf("wo")).firstOrNull()?.word)
        assertEquals("DICTDEBUG first for de", "的", dict.lookup(listOf("de")).firstOrNull()?.word)
        assertEquals("DICTDEBUG first for ni", "你", dict.lookup(listOf("ni")).firstOrNull()?.word)
    }

    @Test
    fun candidatesAreOrderedByScore() {
        val dict = dict()
        for (syllable in listOf("wo", "de", "ni", "yi", "hao")) {
            val entries = dict.lookup(listOf(syllable))
            // 40 is the packer's per-key cap, so a bigger list means the count
            // or the offsets are being decoded wrong
            assertTrue("DICTDEBUG $syllable has ${entries.size} entries", entries.size in 1..40)
            val scores = entries.map { it.score }
            assertEquals("DICTDEBUG $syllable scores", scores.sortedDescending(), scores)
            println("DICTDEBUG $syllable -> ${entries.take(6).map { "${it.word}:${it.score}" }}")
        }
    }
}
