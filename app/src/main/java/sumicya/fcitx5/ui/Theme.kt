package sumicya.fcitx5.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import sumicya.fcitx5.Prefs

/**
 * The Material 3 tokens the hand-built views need.
 *
 * The keyboard and the panels are drawn in code instead of being inflated, so
 * they miss the plumbing a Material component gets for free. This is that
 * plumbing: colour roles from the theme, the pressed state layer, the type
 * scale and dp maths.
 */
object Theme {

    /** The M3 type scale, in sp: only the steps this app uses. */
    const val TITLE_LARGE = 22f
    const val TITLE_MEDIUM = 16f
    const val BODY_LARGE = 16f
    const val BODY_MEDIUM = 14f
    const val BODY_SMALL = 12f

    /** The M3 minimum touch target, in dp. */
    const val TOUCH = 48f

    fun isDark(context: Context): Boolean = Prefs.dark(context)
        ?: ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES)

    /** A colour role from the theme, with the M3 baseline palette as fallback. */
    fun color(context: Context, attr: Int, lightFallback: Int, darkFallback: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) {
            tv.data
        } else if (isDark(context)) darkFallback else lightFallback
    }

    /** Divider colour: the theme's outline variant, not a hard coded grey. */
    fun outlineVariant(context: Context) = color(
        context,
        com.google.android.material.R.attr.colorOutlineVariant,
        0xFFCAC4D0.toInt(), 0xFF444746.toInt()
    )

    /**
     * M3 feedback for a tap: a ripple in the theme's highlight colour, clipped
     * to the view. Material's own components press with the same colour.
     */
    fun ripple(context: Context): Drawable {
        val tv = TypedValue()
        val tint = if (context.theme.resolveAttribute(android.R.attr.colorControlHighlight, tv, true)) {
            tv.data
        } else if (isDark(context)) 0x33FFFFFF else 0x1F000000
        return RippleDrawable(ColorStateList.valueOf(tint), null, ColorDrawable(0xFFFFFFFF.toInt()))
    }

    /** Give a hand-built row, cell or button the feedback Material gives one. */
    fun clickable(view: View) {
        view.background = ripple(view.context)
    }

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
