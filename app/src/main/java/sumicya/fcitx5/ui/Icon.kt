package sumicya.fcitx5.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Material Symbols shapes, stroked.
 *
 * The app ships no icon font, and the unicode glyphs these replace (⇧ ⌫ ⏎ ☰ ★ ▸)
 * are drawn by whatever font the device happens to have: they change weight and
 * width between devices and on some of them turn into emoji or tofu. Every path
 * here is laid out in the 24x24 box the symbols use and scaled to the size the
 * caller asks for, so the icons are the same shape everywhere.
 */
enum class Icon {
    BACKSPACE, SHIFT, ENTER, MENU, STAR, CLOSE, CHEVRON_LEFT, CHEVRON_RIGHT;

    /** Lay the symbol out in the 24x24 box the Material symbols are drawn in. */
    private fun build(out: Path) {
        when (this) {
            BACKSPACE -> {
                out.moveTo(2.5f, 12f)
                out.lineTo(8.5f, 5.2f)
                out.lineTo(21f, 5.2f)
                out.lineTo(21f, 18.8f)
                out.lineTo(8.5f, 18.8f)
                out.close()
                out.moveTo(12.5f, 9.5f)
                out.lineTo(17f, 14f)
                out.moveTo(17f, 9.5f)
                out.lineTo(12.5f, 14f)
            }
            SHIFT -> {
                out.moveTo(12f, 3.5f)
                out.lineTo(4.5f, 11.5f)
                out.lineTo(8f, 11.5f)
                out.lineTo(8f, 19.5f)
                out.lineTo(16f, 19.5f)
                out.lineTo(16f, 11.5f)
                out.lineTo(19.5f, 11.5f)
                out.close()
            }
            ENTER -> {
                out.moveTo(21f, 7.5f)
                out.lineTo(21f, 12f)
                out.lineTo(5.5f, 12f)
                out.moveTo(9.5f, 8f)
                out.lineTo(5.5f, 12f)
                out.lineTo(9.5f, 16f)
            }
            MENU -> {
                out.moveTo(3f, 6f)
                out.lineTo(21f, 6f)
                out.moveTo(3f, 12f)
                out.lineTo(21f, 12f)
                out.moveTo(3f, 18f)
                out.lineTo(21f, 18f)
            }
            CLOSE -> {
                out.moveTo(5f, 5f)
                out.lineTo(19f, 19f)
                out.moveTo(19f, 5f)
                out.lineTo(5f, 19f)
            }
            CHEVRON_LEFT -> {
                out.moveTo(14f, 6f)
                out.lineTo(8.5f, 12f)
                out.lineTo(14f, 18f)
            }
            CHEVRON_RIGHT -> {
                out.moveTo(10f, 6f)
                out.lineTo(15.5f, 12f)
                out.lineTo(10f, 18f)
            }
            STAR -> star(out)
        }
    }

    /** Draw the symbol centred on (cx, cy) at [size] dp. */
    fun draw(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val scale = size / BOX
        val path = Path()
        build(path)
        path.transform(
            Matrix().apply {
                setScale(scale, scale)
                postTranslate(cx - size / 2f, cy - size / 2f)
            }
        )
        paint.strokeWidth = STROKE * scale
        paint.pathEffect = CornerPathEffect(ROUNDING * scale)
        canvas.drawPath(path, paint)
    }

    private companion object {
        /** The Material symbol canvas, and the stroke of the outlined style. */
        const val BOX = 24f
        const val STROKE = 2f
        /** Corner rounding, so hand drawn corners read like the symbols. */
        const val ROUNDING = 1.4f

        fun star(out: Path) {
            val cx = 12f
            // a five pointed star is taller above its centre than below, so the
            // centre sits a little low to make the shape look centred
            val cy = 12.9f
            val outer = 9.5f
            val inner = 4.1f
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outer else inner
                val a = Math.toRadians(-90.0 + i * 36.0)
                val x = cx + (r * cos(a)).toFloat()
                val y = cy + (r * sin(a)).toFloat()
                if (i == 0) out.moveTo(x, y) else out.lineTo(x, y)
            }
            out.close()
        }
    }
}

/** One symbol, centred, in whatever size the layout gives it. */
class IconView(context: Context, icon: Icon) : View(context) {

    var icon: Icon = icon
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val tint = Theme.color(
        context,
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        0xFF625B71.toInt(), 0xFFCAC4D0.toInt()
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = tint
        icon.draw(canvas, width / 2f, height / 2f, min(width, height) * 0.5f, paint)
    }
}
