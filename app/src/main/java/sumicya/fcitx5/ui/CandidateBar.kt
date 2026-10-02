package sumicya.fcitx5.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Candidates, scrolled sideways, with one button that pages through them.
 *
 * ponytail: no page counter and no measured page size, the arrow just scrolls
 * by most of a screen and turns around at the end.
 */
class CandidateBar(
    context: Context,
    private val onPick: (Int) -> Unit,
) : LinearLayout(context) {

    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
    }
    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val page = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        setOnClickListener { flip() }
    }

    init {
        orientation = LinearLayout.HORIZONTAL
        scroll.addView(row, ViewGroup.LayoutParams(WRAP, MATCH))
        addView(scroll, LinearLayout.LayoutParams(0, MATCH, 1f))
        addView(page, LinearLayout.LayoutParams(WRAP, MATCH))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updatePageButton()
    }

    fun setCandidates(items: List<String>) {
        row.removeAllViews()
        items.forEachIndexed { index, item ->
            if (index > 0) row.addView(divider())
            row.addView(itemView(item, index), LinearLayout.LayoutParams(WRAP, MATCH))
        }
        scroll.scrollTo(0, 0)
        updatePageButton()
    }

    private fun updatePageButton() {
        val text = when {
            canScrollRight() -> "▸"
            scroll.scrollX > 0 -> "◂"
            else -> ""
        }
        page.text = text
        page.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        page.setPadding(dp(10), 0, dp(10), 0)
    }

    private fun canScrollRight() = row.width > scroll.width + scroll.scrollX

    private fun flip() {
        if (canScrollRight()) scroll.smoothScrollBy((scroll.width * PAGE).toInt(), 0)
        else scroll.smoothScrollTo(0, 0)
        post { updatePageButton() }
    }

    private fun divider() = View(context).apply { setBackgroundColor(DIVIDER) }

    private fun itemView(text: String, index: Int) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        gravity = Gravity.CENTER
        // the first candidate is what space commits, so it is marked
        if (index == 0) setTypeface(typeface, Typeface.BOLD)
        val p = dp(12)
        setPadding(p, 0, p, 0)
        setOnClickListener { onPick(index) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val DIVIDER = 0x20000000.toInt()
        const val PAGE = 0.8f
    }
}
