package sumicya.fcitx5.engine

import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Temporary: is abbreviated pinyin already in the dictionary? Deleted after reading. */
class ProbeTest {

    @Test
    fun probe() {
        val file = File("src/main/assets/pinyin.dict")
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val dict = PinyinDict.parse(buffer) ?: error("bad dict")
        val syl = dict.syllables
        println("DICTDEBUG probe initials=" + ('A'..'Z').joinToString("") { "$it:${syl.idOf(it.toString())}" })
        for (pair in listOf(
            listOf("Y", "H"), listOf("N", "H"), listOf("W", "M"), listOf("S", "R", "F"),
            listOf("Y", "H", "D"), listOf("N", "H", "M")
        )) {
            val words = dict.lookup(pair).take(8).joinToString("") { it.word }
            println("DICTDEBUG probe [${pair.joinToString(" ")}] -> ${if (words.isEmpty()) "(nothing)" else words}")
        }
        for (s in listOf("wei", "you", "wen", "yi", "shi")) {
            println("DICTDEBUG probe full[$s] -> " + dict.lookup(listOf(s)).take(6).joinToString("") { it.word })
        }
    }
}
