package sumicya.fcitx5.engine

import android.content.Context

/**
 * Simplified -> traditional substitution, one character at a time.
 *
 * The table is the OpenCC STCharacters dictionary (Apache-2.0), vendored in
 * scripts/ and packed into the asset by scripts/build_dict.py.
 */
object Trad {

    private val map = HashMap<Char, Char>()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        try {
            context.assets.open(FILE).bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.length >= 3) map[line[0]] = line[2]
                }
            }
        } catch (e: Exception) {
            // no table shipped: simplified stays simplified
        }
        loaded = true
    }

    /** ponytail: character by character, so no phrase level disambiguation. */
    fun convert(text: String): String {
        if (!loaded || map.isEmpty()) return text
        var changed = false
        val out = StringBuilder(text.length)
        for (c in text) {
            val t = map[c]
            if (t != null) {
                out.append(t)
                changed = true
            } else {
                out.append(c)
            }
        }
        return if (changed) out.toString() else text
    }

    private const val FILE = "st.txt"
}
