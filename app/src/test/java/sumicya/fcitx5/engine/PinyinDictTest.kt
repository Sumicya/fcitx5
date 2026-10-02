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
        val converted = text.map { map[it] ?: it }.joinToString("")
        println("TRADDEBUG lines=${file.readLines().size} map=${map.size} converted=$converted")
        assertEquals("我們來說時間頭發漢字語輸入法愛心", converted)
    }

    /**
     * The corpus frequencies in essay.txt are what decide the order here, so
     * the everyday character has to come out first, not merely near it.
     */
    @Test
    fun corpusFrequenciesDecideTheOrder() {
        val dict = dict()
        val first = { s: String -> dict.lookup(listOf(s)).firstOrNull()?.word }
        assertEquals("DICTDEBUG wo -> ${dict.lookup(listOf("wo")).take(4)}", "我", first("wo"))
        assertEquals("DICTDEBUG de -> ${dict.lookup(listOf("de")).take(4)}", "的", first("de"))
        assertEquals("DICTDEBUG ni -> ${dict.lookup(listOf("ni")).take(4)}", "你", first("ni"))
        assertEquals("DICTDEBUG yi -> ${dict.lookup(listOf("yi")).take(4)}", "一", first("yi"))
        assertEquals("DICTDEBUG hao -> ${dict.lookup(listOf("hao")).take(4)}", "好", first("hao"))
        assertEquals("DICTDEBUG shi -> ${dict.lookup(listOf("shi")).take(4)}", "是", first("shi"))
        assertEquals("DICTDEBUG bu -> ${dict.lookup(listOf("bu")).take(4)}", "不", first("bu"))
    }

    @Test
    fun commonWordsComeFirst() {
        val dict = dict()
        val first = { s: List<String> -> dict.lookup(s).firstOrNull()?.word }
        assertEquals("DICTDEBUG nihao -> ${dict.lookup(listOf("ni", "hao")).take(4)}", "你好", first(listOf("ni", "hao")))
        assertEquals("DICTDEBUG women -> ${dict.lookup(listOf("wo", "men")).take(4)}", "我们", first(listOf("wo", "men")))
    }

    @Test
    fun characterReadingsCoverEverydayText() {
        val file = File("src/main/assets/py.txt")
        check(file.exists()) { "missing ${file.absolutePath}: run scripts/build_dict.py first" }
        val map = HashMap<Char, List<String>>()
        for (line in file.readLines()) {
            if (line.length >= 3) map[line[0]] = line.substring(2).split(" ")
        }
        // 26.7k: the single character entries of dict_sc.txt, minus the ones
        // whose reading is more than one syllable
        assertTrue("PYDEBUG ${map.size} characters", map.size > 25000)
        for (char in "我们来说时间头发汉字语输入法爱心") {
            assertTrue("PYDEBUG missing $char", char in map)
        }
        assertTrue("PYDEBUG ni -> ${map['你']}", "ni" in (map['你'] ?: emptyList()))
        assertTrue("PYDEBUG hao -> ${map['好']}", "hao" in (map['好'] ?: emptyList()))
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
