package sumicya.fcitx5.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A copied paragraph used to come back as one clipboard entry per line. These
 * two checks are what keeps the history file honest.
 */
class ClipLinesTest {

    @Test
    fun aClipSurvivesAFileRoundTrip() {
        val clips = listOf(
            "one line", "a\nb\nc", "back\\slash", "trailing\n", "\n\n",
            "混合 中文\nand english", "tab\tand \\n literal"
        )
        for (text in clips) {
            assertEquals(text, ClipLines.unescape(ClipLines.escape(text)))
        }
    }

    @Test
    fun oneClipStaysOnOneLine() {
        val line = ClipLines.escape("first\nsecond")
        assertFalse(line.contains('\n'))
        assertEquals("first\nsecond", ClipLines.unescape(line))
    }
}
