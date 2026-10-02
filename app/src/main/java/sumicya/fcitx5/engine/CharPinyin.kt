package sumicya.fcitx5.engine

import android.content.Context

/**
 * Character -> readings, so a phrase the user coined can be turned into pinyin
 * and typed like any dictionary word.
 */
object CharPinyin {

    private val map = HashMap<Char, Array<String>>()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        try {
            context.assets.open(FILE).bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.length < 3) continue
                    map[line[0]] = line.substring(2).split(" ").toTypedArray()
                }
            }
        } catch (e: Exception) {
            // no table: coined phrases stay in the panel but cannot be typed
        }
        loaded = true
    }

    /** The readings of [text], or null if any character is unknown. */
    fun pinyinOf(text: String): List<String>? {
        if (!loaded || map.isEmpty()) return null
        val out = ArrayList<String>(text.length)
        for (c in text) {
            val readings = map[c] ?: return null
            if (readings.isEmpty()) return null
            out.add(readings[0])
        }
        return out
    }

    private const val FILE = "py.txt"
}
