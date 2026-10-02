package sumicya.fcitx5.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import sumicya.fcitx5.Prefs
import kotlin.math.abs

/**
 * Hand-drawn keyboard: monochrome, no shadows, one accent color.
 * Tap types the key, swiping up types its secondary symbol (no long press).
 */
class KeyboardView(context: Context) : View(context) {

    enum class Gesture { TAP, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT }

    interface Listener {
        fun onKey(key: Key, gesture: Gesture)
        /** Horizontal drag on the space bar: move the cursor by [chars]. */
        fun onSpaceDrag(chars: Int)
    }

    var listener: Listener? = null

    var shift: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var modeLabel: String = "中"
        set(value) {
            field = value
            invalidate()
        }

    private enum class Layer { LETTERS, SYMBOLS }

    private var layer = Layer.LETTERS
    private val rects = mutableListOf<Pair<Key, RectF>>()

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var radius = 0f
    private var keyTextSize = 0f
    private var gap = 0f
    private var vGap = 0f
    private var padX = 0f
    private var padY = 0f

    private var active: Key? = null
    private var startX = 0f
    private var startY = 0f
    private var fired = false
    private var dragSteps = 0

    fun toggleLayer() {
        layer = if (layer == Layer.LETTERS) Layer.SYMBOLS else Layer.LETTERS
        layoutKeys(width, height)
        invalidate()
    }

    private val rows: List<List<Key>>
        get() = if (layer == Layer.LETTERS) Keys.qwerty else Keys.symbols

    private val isDark: Boolean
        get() = Prefs.dark(context)

    /** The theme's accent, so the keyboard follows dynamic color too. */
    private val accent: Int by lazy {
        val value = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.colorAccent, value, true)) {
            value.data
        } else {
            Color.rgb(0x1A, 0x73, 0xE8)
        }
    }
            ?: ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = resources.displayMetrics
        radius = 4f * d.density
        keyTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18f, d)
        gap = 3f * d.density
        vGap = 6f * d.density
        padX = 3f * d.density
        padY = 4f * d.density
        layoutKeys(w, h)
    }

    private fun layoutKeys(w: Int, h: Int) {
        rects.clear()
        val rs = rows
        if (w <= 0 || h <= 0) return
        val rowHeight = (h - padY * 2 - vGap * (rs.size - 1)) / rs.size
        var y = padY
        for (row in rs) {
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val usable = w - padX * 2 - gap * (row.size - 1)
            var x = padX
            for (key in row) {
                val kw = usable * (key.weight / total)
                rects.add(key to RectF(x, y, x + kw, y + rowHeight))
                x += kw + gap
            }
            y += rowHeight + vGap
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(if (isDark) Color.rgb(0x12, 0x12, 0x12) else Color.WHITE)
        val dark = isDark
        val current = active
        for ((key, r) in rects) {
            val pressed = key === current || (key.type == Key.Type.SHIFT && shift)
            keyPaint.color = when {
                pressed -> if (dark) Color.rgb(0x3C, 0x3C, 0x3C) else Color.rgb(0xD6, 0xD8, 0xDA)
                dark -> Color.rgb(0x26, 0x26, 0x26)
                else -> Color.rgb(0xF1, 0xF1, 0xF1)
            }
            canvas.drawRoundRect(r, radius, radius, keyPaint)

            val label = labelOf(key)
            if (label.isNotBlank()) {
                textPaint.color = if (key.type == Key.Type.ENTER) accent else if (dark) Color.rgb(0xEC, 0xEC, 0xEC) else Color.rgb(0x20, 0x21, 0x24)
                textPaint.textSize = if (key.type == Key.Type.CHAR) keyTextSize else keyTextSize * 0.85f
                val fm = textPaint.fontMetrics
                canvas.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
            }

            val hint = if (key.type == Key.Type.CHAR) key.swipe else null
            if (hint != null && !pressed) {
                hintPaint.color = if (dark) Color.rgb(0x8A, 0x8A, 0x8A) else Color.rgb(0x9A, 0x9A, 0x9A)
                hintPaint.textSize = keyTextSize * 0.6f
                canvas.drawText(hint, r.right - 8f * resources.displayMetrics.density, r.top + 16f * resources.displayMetrics.density, hintPaint)
            }
        }
    }

    private fun labelOf(key: Key): String = when (key.type) {
        Key.Type.MODE -> modeLabel
        Key.Type.PANEL -> "☰"
        Key.Type.LAYER -> if (layer == Layer.LETTERS) "?123" else "ABC"
        Key.Type.SPACE -> ""
        else -> key.label
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val key = hit(event.x, event.y) ?: return true
                active = key
                startX = event.x
                startY = event.y
                fired = false
                dragSteps = 0
                haptic()
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val key = active ?: return true
                val dx = event.x - startX
                val dy = event.y - startY
                if (key.type == Key.Type.SPACE) {
                    val step = (abs(dx) / (16f * resources.displayMetrics.density)).toInt()
                    val signed = if (dx >= 0) step else -step
                    if (signed != dragSteps) {
                        listener?.onSpaceDrag(signed - dragSteps)
                        dragSteps = signed
                    }
                    return true
                }
                val threshold = 24f * resources.displayMetrics.density
                if (!fired && (abs(dx) > threshold || abs(dy) > threshold)) {
                    fired = true
                    active = null
                    val gesture = if (abs(dx) > abs(dy)) {
                        if (dx < 0) Gesture.SWIPE_LEFT else Gesture.SWIPE_RIGHT
                    } else {
                        if (dy < 0) Gesture.SWIPE_UP else Gesture.SWIPE_DOWN
                    }
                    listener?.onKey(key, gesture)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                val key = active
                if (key != null && !fired) listener?.onKey(key, Gesture.TAP)
                clearTouch()
            }

            MotionEvent.ACTION_CANCEL -> clearTouch()
        }
        return true
    }

    private fun clearTouch() {
        active = null
        fired = false
        dragSteps = 0
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hit(x: Float, y: Float): Key? =
        rects.firstOrNull { (_, r) -> r.contains(x, y) }?.first

    private fun haptic() {
        if (Prefs.haptic(context)) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private companion object {
    }
}
