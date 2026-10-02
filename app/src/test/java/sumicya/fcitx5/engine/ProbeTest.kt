package sumicya.fcitx5.engine

import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Temporary: what the packed dictionary actually knows. Deleted after reading. */
class ProbeTest {

    @Test
    fun probe() {
        val file = File("src/main/assets/pinyin.dict")
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val dict = PinyinDict.parse(buffer) ?: error("bad dict")
        val syl = dict.syllables
        println("PROBE count=${syl.size}")
        println("PROBE all=" + (1..syl.size).joinToString(" ") { syl.name(it) })
        for (s in listOf("ui", "iu", "un", "u", "wei", "uei", "yh", "y", "h", "sh", "zh", "ch", "ng")) {
            val id = syl.idOf(s)
            val sample = if (id == 0) "-" else dict.lookup(listOf(s)).take(4).joinToString("") { it.word }
            println("PROBE [$s] id=$id sample=$sample")
        }
    }
}
