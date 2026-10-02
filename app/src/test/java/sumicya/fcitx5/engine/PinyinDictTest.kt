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
    fun traditionalTableCoversEverydayCharacters() {
        val file = File("src/main/assets/st.txt")
        check(file.exists()) { "missing ${file.absolutePath}: run scripts/build_dict.py first" }
        val map = HashMap<Char, Char>()
        for (line in file.readLines()) {
            if (line.length >= 3) map[line[0]] = line[2]
        }
        val text = "我们来说时间头发汉字语输入法爱心"
        assertEquals("我們來說時間頭髮漢字語輸入法愛心", text.map { map[it] ?: it }.joinToString(""))
    }

    /**
     * The base ranking has no corpus behind it, so it is only required to put
     * the obvious character near the front; picking candidates teaches the
     * user dictionary the rest.
     */
    @Test
    fun commonCharactersRankNearTheFront() {
        val dict = dict()
        val front = { s: String -> dict.lookup(listOf(s)).take(3).map { it.word } }
        assertTrue("DICTDEBUG wo -> ${front("wo")}", "我" in front("wo"))
        assertTrue("DICTDEBUG de -> ${front("de")}", "的" in front("de"))
        assertTrue("DICTDEBUG ni -> ${front("ni")}", "你" in front("ni"))
        assertTrue("DICTDEBUG yi -> ${front("yi")}", "一" in front("yi"))
        assertTrue("DICTDEBUG hao -> ${front("hao")}", "好" in front("hao"))
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
