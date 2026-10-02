package sumicya.fcitx5.engine

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Read-only view over the dictionary packed by scripts/build_dict.py:
 * magic(8) | count u32 | dataStart u32 | syllableCount u16 | syllable table |
 * keys u64 * count | dataOffsets u32 * count | data blob
 *
 * The key array is sorted, so a lookup is one binary search; keys are compared
 * unsigned because the packed value is an u64.
 */
class PinyinDict private constructor(
    private val buffer: ByteBuffer,
    val syllables: Syllables,
    private val count: Int,
    private val keysStart: Int,
    private val offsetsStart: Int,
    private val dataStart: Int,
) {

    data class Entry(val word: String, val score: Int)

    fun keyOf(ids: IntArray, length: Int): Long {
        var key = 0L
        for (i in 0 until Syllables.MAX_PER_WORD) {
            key = key shl Syllables.SYLLABLE_BITS
            if (i < length) key = key or (ids[i].toLong() and Syllables.SYLLABLE_MASK)
        }
        return key
    }

    /** Convenience for tests and tools: look up by syllable names, e.g. "ni", "hao". */
    fun lookup(words: List<String>): List<Entry> {
        val ids = IntArray(Syllables.MAX_PER_WORD)
        val length = minOf(words.size, Syllables.MAX_PER_WORD)
        for (i in 0 until length) ids[i] = syllables.idOf(words[i])
        return if (ids[0] == 0) emptyList() else lookup(keyOf(ids, length))
    }

    fun lookup(key: Long): List<Entry> {
        var lo = 0
        var hi = count - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = java.lang.Long.compareUnsigned(buffer.getLong(keysStart + mid * 8), key)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return entriesAt(buffer.getInt(offsetsStart + mid * 4))
            }
        }
        return emptyList()
    }

    /** Temporary: dumps the raw bytes a lookup lands on. */
    internal fun debugLookup(words: List<String>): String {
        val ids = IntArray(Syllables.MAX_PER_WORD)
        val length = minOf(words.size, Syllables.MAX_PER_WORD)
        for (i in 0 until length) ids[i] = syllables.idOf(words[i])
        val key = keyOf(ids, length)
        var lo = 0
        var hi = count - 1
        var mid = -1
        while (lo <= hi) {
            mid = (lo + hi) ushr 1
            val cmp = java.lang.Long.compareUnsigned(buffer.getLong(keysStart + mid * 8), key)
            if (cmp < 0) lo = mid + 1 else if (cmp > 0) hi = mid - 1 else break
        }
        if (mid < 0) return "not found"
        val offset = buffer.getInt(offsetsStart + mid * 4)
        val start = dataStart + offset
        val hex = (0 until 12).joinToString(" ") { "%02x".format(buffer.get(start + it)) }
        return "key=%016x index=%d/%d offset=%d start=%d keysStart=%d offsetsStart=%d dataStart=%d bytes=[%s]".format(
            key, mid, count, offset, start, keysStart, offsetsStart, dataStart, hex)
    }

    private fun entriesAt(offset: Int): List<Entry> {
        val view = buffer.duplicate()
        view.position(dataStart + offset)
        val total = view.short.toInt() and 0xFFFF
        val out = ArrayList<Entry>(total)
        repeat(total) {
            val length = view.get().toInt() and 0xFF
            val bytes = ByteArray(length)
            view.get(bytes)
            out.add(Entry(String(bytes, Charsets.UTF_8), view.short.toInt() and 0xFFFF))
        }
        return out
    }

    companion object {
        private const val ASSET = "pinyin.dict"
        private val MAGIC = byteArrayOf('F'.code.toByte(), 'C'.code.toByte(), 'P'.code.toByte(),
            'Y'.code.toByte(), 'D'.code.toByte(), 'I'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())

        fun load(context: Context): PinyinDict? = try {
            val file = File(context.filesDir, ASSET)
            if (!file.exists()) copy(context, file)
            open(file) ?: run {
                copy(context, file)
                open(file)
            }
        } catch (e: Exception) {
            null
        }

        private fun copy(context: Context, file: File) {
            context.assets.open(ASSET).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }

        private fun open(file: File): PinyinDict? = parse(FileInputStream(file).use { stream ->
            stream.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        }.order(ByteOrder.LITTLE_ENDIAN))

        /** Visible for tests: [buffer] must be little endian and positioned at 0. */
        internal fun parse(buffer: ByteBuffer): PinyinDict? {
            val magic = ByteArray(8)
            buffer.duplicate().get(magic)
            if (!magic.contentEquals(MAGIC)) return null

            val count = buffer.getInt(8)
            val dataStart = buffer.getInt(12)
            val syllableCount = buffer.getShort(16).toInt() and 0xFFFF
            var p = 18
            val names = ArrayList<String>(syllableCount)
            repeat(syllableCount) {
                val length = buffer.get(p).toInt() and 0xFF
                p += 1
                val bytes = ByteArray(length)
                for (i in 0 until length) bytes[i] = buffer.get(p + i)
                p += length
                names.add(String(bytes, Charsets.US_ASCII))
            }
            val keysStart = (p + 7) and 7.inv()
            val offsetsStart = keysStart + 8 * count
            return PinyinDict(buffer, Syllables(names), count, keysStart, offsetsStart, dataStart)
        }
    }
}
