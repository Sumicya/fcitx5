package sumicya.fcitx5.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
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
import sumicya.fcitx5.ui.Icon
import sumicya.fcitx5.ui.Theme
import kotlin.math.abs
import kotlin.math.min

/**
 * Hand-drawn keyboard in the Material 3 shape language: large corner radii,
 * pill shaped space and enter keys, and a springy press.
 *
 * The measurements follow Material 3 rather than habit: keys are 48dp tall, a
 * press adds a 10% state layer on top of the container instead of swapping the
 * container colour, letters sit on the 22sp title step, and the icons are the
 * stroked Material symbol shapes. The colours are the theme's own M3 roles, so
 * on Android 12+ the keyboard picks up the wallpaper palette like the rest of
 * the phone.
 *
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
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var radius = 0f
    private var keyTextSize = 0f
    private var fnTextSize = 0f
    private var hintTextSize = 0f
    private var gap = 0f
    private var vGap = 0f
    private var padX = 0f
    private var padY = 0f
    private var hintPad = 0f

    /** The key under the finger, for gestures. */
    private var active: Key? = null
    /** The key drawn as pressed; it outlives the touch so the spring can settle. */
    private var pressedKey: Key? = null
    private var pressProgress = 0f
    private var pressAnimator: ValueAnimator? = null

    /** The finger that owns the gesture; the others are ignored while it lasts. */
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var startX = 0f
    private var startY = 0f
    private var fired = false
    private var dragSteps = 0

    init {
        // the keys are drawn, so there is nothing for TalkBack to read: this is
        // what the keyboard announces itself as
        contentDescription = "拼音键盘"
    }

    fun toggleLayer() {
        layer = if (layer == Layer.LETTERS) Layer.SYMBOLS else Layer.LETTERS
        layoutKeys(width, height)
        invalidate()
    }

    private val rows: List<List<Key>>
        get() = if (layer == Layer.LETTERS) Keys.qwerty else Keys.symbols

    private class Palette(
        val surface: Int, val key: Int, val keyVariant: Int, val selected: Int,
        val onSurface: Int, val onSurfaceVariant: Int,
        val primary: Int, val onPrimary: Int
    )

    private var palette: Palette? = null
    private var paletteDark: Boolean? = null

    private fun palette(): Palette {
        val dark = Theme.isDark(context)
        if (palette == null || paletteDark != dark) {
            paletteDark = dark
            palette = buildPalette()
        }
        return palette!!
    }

    /** Material 3 color roles, with the M3 baseline palette as the fallback. */
    private fun buildPalette() = Palette(
        surface = role(com.google.android.material.R.attr.colorSurfaceContainerLow, 0xFFF3EDF7.toInt(), 0xFF1D1B20.toInt()),
        key = role(com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFE7E0EC.toInt(), 0xFF322F35.toInt()),
        keyVariant = role(com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFFE6E0E9.toInt(), 0xFF3B383E.toInt()),
        selected = role(com.google.android.material.R.attr.colorSecondaryContainer, 0xFFE8DEF8.toInt(), 0xFF4A4458.toInt()),
        onSurface = role(com.google.android.material.R.attr.colorOnSurface, 0xFF1D1B20.toInt(), 0xFFE6E0E9.toInt()),
        onSurfaceVariant = role(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF625B71.toInt(), 0xFFCAC4D0.toInt()),
        // colorPrimary lives in appcompat, which is not on our compile classpath;
        // the framework colorAccent is what Material themes map it to anyway
        primary = role(android.R.attr.colorAccent, 0xFF6750A4.toInt(), 0xFFD0BCFF.toInt()),
        onPrimary = role(com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF.toInt(), 0xFF381E72.toInt())
    )

    private fun role(attr: Int, lightFallback: Int, darkFallback: Int) =
        Theme.color(context, attr, lightFallback, darkFallback)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = resources.displayMetrics
        radius = 12f * d.density
        keyTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, Theme.TITLE_LARGE, d)
        fnTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, Theme.TITLE_MEDIUM, d)
        hintTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, Theme.BODY_SMALL, d)
        gap = Keys.GAP * d.density
        vGap = Keys.V_GAP * d.density
        padX = Keys.PAD_X * d.density
        padY = Keys.PAD_Y * d.density
        hintPad = 6f * d.density
        layoutKeys(w, h)
    }

    private fun layoutKeys(w: Int, h: Int) {
        rects.clear()
        val rs = rows
        if (w <= 0 || h <= 0) return
        val rowHeight = Keys.rowHeight(h.toFloat(), rs.size, padY, vGap)
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
            val pressed = key === pressedKey
            val latched = key.type == Key.Type.SHIFT && shift
            val accent = key.type == Key.Type.ENTER
            val scale = if (pressed) 1f - PRESS_DEPTH * pressProgress else 1f
            canvas.save()
            if (scale != 1f) canvas.scale(scale, scale, r.centerX(), r.centerY())

            keyPaint.color = when {
                accent -> p.primary
                latched -> p.selected
                key.type == Key.Type.CHAR -> p.key
                else -> p.keyVariant
            }
            // MD3 Expressive: the wide keys are pills, the rest are soft squares
            val rr = if (key.type == Key.Type.SPACE || accent) r.height() / 2f else radius
            canvas.drawRoundRect(r, rr, rr, keyPaint)

            // M3 state layers: a press overlays 10% of the content colour on the
            // container, it does not swap the container for another one
            if (pressed && pressProgress > 0f) {
                keyPaint.color = if (accent) p.onPrimary else p.onSurface
                keyPaint.alpha = (STATE_LAYER * pressProgress).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(r, rr, rr, keyPaint)
                keyPaint.alpha = 0xFF
            }

            val icon = iconOf(key)
            if (icon != null) {
                iconPaint.color = if (accent) p.onPrimary else p.onSurface
                icon.draw(canvas, r.centerX(), r.centerY(), iconSize(r), iconPaint)
            } else {
                val label = labelOf(key)
                if (label.isNotBlank()) {
                    textPaint.color = if (accent) p.onPrimary else p.onSurface
                    textPaint.textSize = if (key.type == Key.Type.CHAR) keyTextSize else fnTextSize
                    val fm = textPaint.fontMetrics
                    canvas.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
                }
            }

            val hint = if (key.type == Key.Type.CHAR) key.swipe else null
            if (hint != null && !pressed && !latched) {
                hintPaint.color = p.onSurfaceVariant
                hintPaint.textSize = hintTextSize
                canvas.drawText(hint, r.right - hintPad, r.top + hintPad - hintPaint.fontMetrics.ascent, hintPaint)
            }
            canvas.restore()
        }
    }

    /** A 24dp symbol, as long as the key is big enough to hold one. */
    private fun iconSize(r: RectF): Float {
        val d = resources.displayMetrics.density
        return min(24f * d, min(r.height() * 0.5f, r.width() * 0.7f))
    }

    private fun iconOf(key: Key): Icon? = when (key.type) {
        Key.Type.DELETE -> Icon.BACKSPACE
        Key.Type.SHIFT -> Icon.SHIFT
        Key.Type.ENTER -> Icon.ENTER
        Key.Type.PANEL -> Icon.MENU
        else -> null
    }

    private fun labelOf(key: Key): String = when (key.type) {
        Key.Type.MODE -> modeLabel
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
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // a second finger while one is already down gets no key of its
                // own: two thumbs type faster than one gesture can follow, and
                // the alternative is dropping the first finger's key instead
                if (pointerId != MotionEvent.INVALID_POINTER_ID) return true
                val index = event.actionIndex
                val key = hit(event.getX(index), event.getY(index)) ?: return true
                pointerId = event.getPointerId(index)
                active = key
                startX = event.getX(index)
                startY = event.getY(index)
                fired = false
                dragSteps = 0
                haptic()
                animatePress(true)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return true
                val key = active ?: return true
                val dx = event.getX(index) - startX
                val dy = event.getY(index) - startY
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

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) != pointerId) return true
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
        pointerId = MotionEvent.INVALID_POINTER_ID
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
        /** M3 pressed state layer: 10% of the content colour. */
        const val STATE_LAYER = 26f
    }
}
