package sumicya.fcitx5.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a clipboard row is allowed to hold.
 *
 * The bug this guards against is not a wrong string but a whole article reaching
 * a TextView, which costs a measure pass over every character for every row.
 */
class PreviewTest {

    @Test
    fun aShortClipIsShownAsItIs() {
        assertEquals("你好", Preview.of("你好"))
    }

    @Test
    fun newlinesCollapseIntoOneGap() {
        assertEquals("a b", Preview.of("a\nb"))
        assertEquals("a b", Preview.of("a\r\nb"))
        assertEquals("a b", Preview.of("a\n\n\n\tb"))
        assertEquals("abc", Preview.of("\n\nabc"))
    }

    @Test
    fun aLongClipIsCutShort() {
        val shown = Preview.of("字".repeat(50_000))
        assertEquals(Preview.MAX, shown.length)
        assertEquals("字".repeat(Preview.MAX), shown)
    }
}
