package sumicya.fcitx5.keyboard

class Key(
    val label: String,
    val type: Type = Type.CHAR,
    val code: Int = 0,
    /** Typed when the user swipes up on this key instead of tapping it. */
    val swipe: String? = null,
    val weight: Float = 1f,
) {
    enum class Type { CHAR, DELETE, ENTER, SPACE, SHIFT, LAYER, MODE, PANEL }

    fun text(shift: Boolean): String {
        if (!shift || type != Type.CHAR) return label
        val c = if (code > 0) code.toChar() else return label
        return if (c.isLetter()) c.uppercaseChar().toString() else label
    }
}

object Keys {

    /** Default keyboard height, in dp. */
    const val HEIGHT_PORTRAIT = 216f
    const val HEIGHT_LANDSCAPE = 180f

    /** Spacing, in dp: what is left of the height once the rows are 48dp. */
    const val PAD_X = 3f
    const val PAD_Y = 4f
    const val GAP = 4f
    const val V_GAP = 5f

    /**
     * Height of one of [rows] rows inside [height], in the same unit as the
     * arguments.
     *
     * ponytail: it is a function and not an expression in the view so that a
     * test can hold the keyboard to the 48dp touch target Material asks for.
     */
    fun rowHeight(height: Float, rows: Int, padY: Float = PAD_Y, vGap: Float = V_GAP) =
        (height - padY * 2 - vGap * (rows - 1)) / rows

    private fun ch(c: Char, swipe: String? = null) =
        Key(c.toString(), Key.Type.CHAR, c.code, swipe)

    private fun fn(weight: Float, label: String, type: Key.Type) =
        Key(label, type, 0, null, weight)

    private val bottom = listOf(
        fn(1.2f, "中", Key.Type.MODE), fn(1f, "", Key.Type.PANEL), ch(','),
        fn(3.2f, " ", Key.Type.SPACE), ch('.'), fn(1.3f, "", Key.Type.ENTER),
    )

    val qwerty = listOf(
        listOf(
            ch('q', "1"), ch('w', "2"), ch('e', "3"), ch('r', "4"), ch('t', "5"),
            ch('y', "6"), ch('u', "7"), ch('i', "8"), ch('o', "9"), ch('p', "0"),
        ),
        listOf(
            ch('a', "@"), ch('s', "#"), ch('d', "\$"), ch('f', "%"), ch('g', "&"),
            ch('h', "*"), ch('j', "/"), ch('k', "("), ch('l', ")"),
        ),
        listOf(
            fn(1.4f, "", Key.Type.SHIFT),
            ch('z', "~"), ch('x', "-"), ch('c', "_"), ch('v', "+"),
            ch('b', "="), ch('n', "["), ch('m', "]"),
            fn(1.4f, "", Key.Type.DELETE),
        ),
        listOf(fn(1.3f, "?123", Key.Type.LAYER)) + bottom,
    )

    val symbols = listOf(
        listOf(
            ch('1'), ch('2'), ch('3'), ch('4'), ch('5'),
            ch('6'), ch('7'), ch('8'), ch('9'), ch('0'),
        ),
        listOf(
            ch('@'), ch('#'), ch('\$'), ch('%'), ch('&'),
            ch('*'), ch('-'), ch('+'), ch('('), ch(')'),
        ),
        listOf(
            ch('~'), ch('`'), ch('|'), ch('\\'), ch('/'),
            ch(':'), ch(';'), ch('"'), ch('\''), ch('?'),
        ),
        listOf(fn(1.3f, "ABC", Key.Type.LAYER)) + bottom,
    )
}
