package sumicya.fcitx5.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import sumicya.fcitx5.Prefs
import kotlin.math.abs

/**
 * Hand-drawn keyboard in the Material 3 Expressive shape language: large
 * corner radii, pill shaped space and enter keys, and a springy press instead
 * of a flat highlight.
 *
 * The colors are the theme's own Material 3 roles, so on Android 12+ the
 * keyboard picks up the wallpaper palette like the rest of the phone.
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
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private var radius = 0f
    private var keyTextSize = 0f
    private var gap = 0f
    private var vGap = 0f
    private var padX = 0f
    private var padY = 0f

    /** The key under the finger, for gestures. */
    private var active: Key? = null
    /** The key drawn as pressed; it outlives the touch so the spring can settle. */
    private var pressedKey: Key? = null
    private var pressProgress = 0f
    private var pressAnimator: ValueAnimator? = null

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
            ?: ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES)

    private class Palette(
        val surface: Int, val key: Int, val keyVariant: Int, val pressed: Int,
        val onSurface: Int, val onSurfaceVariant: Int,
        val primary: Int, val onPrimary: Int
    )

    private var palette: Palette? = null
    private var paletteDark: Boolean? = null

    private fun palette(): Palette {
        val dark = isDark
        if (palette == null || paletteDark != dark) {
            paletteDark = dark
            palette = buildPalette(dark)
        }
        return palette!!
    }

    /** Material 3 color roles, with the M3 baseline palette as the fallback. */
    private fun buildPalette(dark: Boolean) = Palette(
        surface = role(com.google.android.material.R.attr.colorSurfaceContainerLow, 0xF3EDF7, 0x1D1B20),
        key = role(com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xE7E0EC, 0x322F35),
        keyVariant = role(com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xE6E0E9, 0x3B383E),
        pressed = role(com.google.android.material.R.attr.colorSecondaryContainer, 0xDAD2FB, 0x4A4458),
        onSurface = role(com.google.android.material.R.attr.colorOnSurface, 0x1D1B20, 0xE6E0E9),
        onSurfaceVariant = role(com.google.android.material.R.attr.colorOnSurfaceVariant, 0x625B71, 0xCAC4D0),
        // colorPrimary lives in appcompat, which is not on our compile classpath;
        // the framework colorAccent is what Material themes map it to anyway
        primary = role(android.R.attr.colorAccent, 0x6750A4, 0xD0BCFF),
        onPrimary = role(com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFF, 0x381E72)
    )

    private fun role(attr: Int, lightFallback: Int, darkFallback: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) {
            tv.data
        } else {
            if (isDark) darkFallback else lightFallback
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = resources.displayMetrics
        radius = 12f * d.density
        keyTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 19f, d)
        gap = 4f * d.density
        vGap = 7f * d.density
        padX = 3f * d.density
        padY = 5f * d.density
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
        val p = palette()
        canvas.drawColor(p.surface)
        for ((key, r) in rects) {
            val pressed = key === pressedKey || (key.type == Key.Type.SHIFT && shift)
            val scale = if (key === pressedKey) 1f - PRESS_DEPTH * pressProgress else 1f
            canvas.save()
            if (scale != 1f) canvas.scale(scale, scale, r.centerX(), r.centerY())

            val accent = key.type == Key.Type.ENTER
            keyPaint.color = when {
                accent -> p.primary
                pressed -> p.pressed
                key.type == Key.Type.CHAR -> p.key
                else -> p.keyVariant
            }
            // MD3 Expressive: the wide keys are pills, the rest are soft squares
            val rr = if (key.type == Key.Type.SPACE || accent) r.height() / 2f else radius
            canvas.drawRoundRect(r, rr, rr, keyPaint)

            val label = labelOf(key)
            if (label.isNotBlank()) {
                textPaint.color = if (accent) p.onPrimary else p.onSurface
                textPaint.textSize = if (key.type == Key.Type.CHAR) keyTextSize else keyTextSize * 0.85f
                val fm = textPaint.fontMetrics
                canvas.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
            }

            val hint = if (key.type == Key.Type.CHAR) key.swipe else null
            if (hint != null && !pressed) {
                hintPaint.color = p.onSurfaceVariant
                hintPaint.textSize = keyTextSize * 0.62f
                canvas.drawText(
                    hint,
                    r.right - 9f * resources.displayMetrics.density,
                    r.top + 17f * resources.displayMetrics.density,
                    hintPaint
                )
            }
            canvas.restore()
        }
    }

    private fun labelOf(key: Key): String = when (key.type) {
        Key.Type.MODE -> modeLabel
        Key.Type.PANEL -> "☰"
        Key.Type.LAYER -> if (layer == Layer.LETTERS) "?123" else "ABC"
        Key.Type.SPACE -> ""
        else -> key.label
    }

    /** Springy press: the key overshoots on the way in and settles on the way out. */
    private fun animatePress(down: Boolean) {
        pressAnimator?.cancel()
        if (down) pressedKey = active
        val animator = ValueAnimator.ofFloat(pressProgress, if (down) 1f else 0f).apply {
            duration = if (down) 180L else 130L
            interpolator = if (down) OvershootInterpolator(2.2f) else DecelerateInterpolator(1.5f)
            addUpdateListener {
                pressProgress = it.animatedValue as Float
                invalidate()
            }
            if (!down) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        pressedKey = null
                        invalidate()
                    }
                })
            }
        }
        pressAnimator = animator
        animator.start()
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
                animatePress(true)
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
                    animatePress(false)
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
        animatePress(false)
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
        /** How far a pressed key dips, as a fraction of its size. */
        const val PRESS_DEPTH = 0.06f
    }
}
