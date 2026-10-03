package sumicya.fcitx5.ui

/**
 * What a row shows for a clip or a phrase.
 *
 * ponytail: the clip itself is never handed to a TextView. A copied article runs
 * to tens of thousands of characters and TextView measures every one of them to
 * lay out two lines — for every row, every time the panel is built. Two lines
 * only ever show a couple of hundred, so that is all the row ever holds.
 */
object Preview {

    fun of(text: String, max: Int = MAX): String {
        val end = minOf(text.length, max)
        val out = StringBuilder(end)
        for (i in 0 until end) {
            val c = text[i]
            // a paragraph's worth of newlines is one gap on screen, not a row of
            // them, and the row does not start with one
            if (c == '\n' || c == '\r' || c == '\t') {
                if (out.isNotEmpty() && out[out.length - 1] != ' ') out.append(' ')
            } else {
                out.append(c)
            }
        }
        return out.toString()
    }

    const val MAX = 200
}
