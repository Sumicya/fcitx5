package sumicya.fcitx5.data

/**
 * The clipboard history is a plain text file, one clip per line, so the
 * newlines inside a clip have to be escaped on the way out and put back on the
 * way in. Without this, a copied paragraph comes back as one clip per line.
 */
object ClipLines {

    fun escape(text: String) = text.replace("\\", "\\\\").replace("\n", "\\n")

    fun unescape(line: String): String {
        val out = StringBuilder(line.length)
        var i = 0
        while (i < line.length) {
            if (line[i] == '\\' && i + 1 < line.length) {
                when (line[i + 1]) {
                    'n' -> out.append('\n')
                    '\\' -> out.append('\\')
                    else -> out.append(line[i + 1])
                }
                i += 2
            } else {
                out.append(line[i])
                i++
            }
        }
        return out.toString()
    }
}
