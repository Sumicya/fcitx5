package sumicya.fcitx5.keyboard

import org.junit.Assert.assertTrue
import org.junit.Test
import sumicya.fcitx5.ui.Theme

/**
 * The keyboard is drawn by hand, so nothing in it gets measured: this is the
 * one measurement that matters, the 48dp touch target Material asks for. A
 * padding or a gap that creeps back in fails here instead of on a thumb.
 */
class KeysTest {

    @Test
    fun rowsAreATouchTargetTall() {
        val rows = Keys.qwerty.size
        val portrait = Keys.rowHeight(Keys.HEIGHT_PORTRAIT, rows)
        val landscape = Keys.rowHeight(Keys.HEIGHT_LANDSCAPE, rows)
        println(String.format("KEYDEBUG rows=%d portrait=%.2fdp landscape=%.2fdp", rows, portrait, landscape))
        assertTrue(
            "a key row is ${portrait}dp, Material asks for ${Theme.TOUCH}dp",
            portrait >= Theme.TOUCH
        )
    }
}
