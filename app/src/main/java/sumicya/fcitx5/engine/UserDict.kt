package sumicya.fcitx5.engine

import android.content.Context
import java.io.File

/**
 * How often the user picked each word. One line per word, "word\tcount",
 * rewritten from scratch on save: a few thousand short lines is nothing, and a
 * corrupted dictionary must never lose more than the last session.
 */
class UserDict(context: Context) {

    private val file = File(context.filesDir, FILE)
    private val counts = LinkedHashMap<String, Int>()
    private var dirty = false

    init {
        if (file.exists()) {
            file.readLines().forEach { line ->
                val parts = line.split('\t')
                if (parts.size == 2) {
                    val count = parts[1].toIntOrNull() ?: return@forEach
                    counts[parts[0]] = count
                }
            }
        }
    }

    fun count(word: String): Int = counts[word] ?: 0

    fun bump(word: String) {
        counts[word] = count(word) + 1
        dirty = true
    }

    fun clear() {
        counts.clear()
        dirty = true
        save()
    }

    fun save() {
        if (!dirty) return
        val tmp = File(file.parentFile, "$FILE.tmp")
        try {
            tmp.printWriter().use { out ->
                for ((word, count) in counts) out.print("$word\t$count\n")
            }
            if (tmp.renameTo(file)) dirty = false
        } catch (e: Exception) {
            tmp.delete()
        }
    }

    companion object {
        private const val FILE = "user.txt"

        /** Anything the user picked outranks anything they did not. */
        const val PICKED_BONUS = 70_000
        const val REPEAT_BONUS = 1_000
    }
}
