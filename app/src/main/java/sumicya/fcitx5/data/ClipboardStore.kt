package sumicya.fcitx5.data

import android.content.ClipboardManager
import android.content.Context
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plain text clipboard history. The system only keeps the latest clip, so the
 * history is whatever we managed to read while the keyboard was on screen.
 *
 * ponytail: no cap on purpose, the file stays small next to the dictionary and
 * an old clip you can still find beats a tidy limit.
 */
class ClipboardStore(context: Context) {

    private val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val file = File(context.filesDir, FILE)
    private val items = ArrayList<String>()
    private val writer = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private val lock = Any()

    init {
        if (file.exists()) {
            // streamed: readLines() would hold every clip twice, once as a line
            // of the file and once unescaped
            file.useLines { lines ->
                lines.forEach { if (it.isNotEmpty()) items.add(ClipLines.unescape(it)) }
            }
        }
    }

    fun all(): List<String> = items

    /** Fold the current system clip into the history. */
    fun refresh() {
        val text = manager.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.text?.toString()
            ?.takeIf { it.isNotBlank() } ?: return
        if (items.firstOrNull() == text) return
        items.remove(text)
        items.add(0, text)
        save()
    }

    fun clear() {
        if (items.isEmpty()) return
        items.clear()
        // queued writes hold the old snapshot, and would put it all back
        generation.incrementAndGet()
        writeAll(items)
    }

    /**
     * The write goes to a thread: a history with one long clip in it is
     * megabytes, and rewriting all of it on the keyboard's main thread is what
     * makes the clipboard stutter. The list it walks is a snapshot — the strings
     * are immutable, so the main thread can keep adding to the real one.
     */
    private fun save() {
        val snapshot = items.toList()
        val mine = generation.incrementAndGet()
        writer.submit { if (generation.get() == mine) writeAll(snapshot) }
    }

    private fun writeAll(snapshot: List<String>) = synchronized(lock) {
        val tmp = File(file.parentFile, "$FILE.tmp")
        try {
            tmp.printWriter().use { out ->
                for (item in snapshot) out.println(ClipLines.escape(item))
            }
            tmp.renameTo(file)
        } catch (e: Exception) {
            tmp.delete()
        }
    }

    private companion object {
        const val FILE = "clip.txt"
    }
}
