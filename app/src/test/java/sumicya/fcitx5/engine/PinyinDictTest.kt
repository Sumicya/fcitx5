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
    fun commonCharactersRankFirst() {
        val dict = dict()
        assertEquals("我", dict.lookup(listOf("wo")).firstOrNull()?.word)
        assertEquals("的", dict.lookup(listOf("de")).firstOrNull()?.word)
        assertEquals("你", dict.lookup(listOf("ni")).firstOrNull()?.word)
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
    fun candidatesAreOrderedByScore() {
        val dict = dict()
        val scores = dict.lookup(listOf("de")).map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }
}
