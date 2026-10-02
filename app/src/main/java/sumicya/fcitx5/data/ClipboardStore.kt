package sumicya.fcitx5.data

import android.content.ClipboardManager
import android.content.Context
import java.io.File

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

    init {
        if (file.exists()) {
            file.readLines().forEach { if (it.isNotEmpty()) items.add(ClipLines.unescape(it)) }
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
        save()
    }

    private fun save() {
        val tmp = File(file.parentFile, "$FILE.tmp")
        try {
            tmp.printWriter().use { out ->
                for (item in items) out.println(ClipLines.escape(item))
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
