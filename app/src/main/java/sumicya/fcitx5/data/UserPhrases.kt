package sumicya.fcitx5.data

import android.content.Context
import sumicya.fcitx5.engine.CharPinyin
import java.io.File

/**
 * Phrases the user saved, remembered with their pinyin so they can be typed
 * even though no dictionary contains them.
 */
class UserPhrases(private val context: Context) {

    private class Phrase(val text: String, val pinyin: List<String>)

    private val file = File(context.filesDir, FILE)
    private val items = ArrayList<Phrase>()

    init {
        if (file.exists()) {
            for (line in file.readLines()) {
                if (line.isBlank()) continue
                items.add(Phrase(line, emptyList()))
            }
            reindex()
        }
    }

    /** The readings table is only worth reading once something needs it. */
    private fun reindex() {
        if (items.isEmpty()) return
        CharPinyin.load(context)
        for (i in items.indices) {
            items[i] = Phrase(items[i].text, CharPinyin.pinyinOf(items[i].text) ?: emptyList())
        }
    }

    fun all(): List<String> = items.map { it.text }

    fun add(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || items.any { it.text == clean }) return
        CharPinyin.load(context)
        items.add(0, Phrase(clean, CharPinyin.pinyinOf(clean) ?: emptyList()))
        save()
    }

    fun remove(text: String) {
        if (items.removeAll { it.text == text }) save()
    }

    /** Phrases whose whole reading is exactly [syllables]. */
    fun match(syllables: List<String>): List<String> =
        items.filter { it.pinyin.isNotEmpty() && it.pinyin == syllables }.map { it.text }

    fun clear() {
        items.clear()
        save()
    }

    private fun save() {
        val tmp = File(file.parentFile, "$FILE.tmp")
        try {
            tmp.printWriter().use { out ->
                for (item in items) out.println(item.text)
            }
            tmp.renameTo(file)
        } catch (e: Exception) {
            tmp.delete()
        }
    }

    companion object {
        private const val FILE = "phrases.txt"
    }
}
