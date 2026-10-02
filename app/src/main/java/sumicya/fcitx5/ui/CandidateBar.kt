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
    private val page = IconView(context, Icon.CHEVRON_RIGHT).apply {
        contentDescription = "下一页候选"
        setOnClickListener { flip() }
    }

    init {
        orientation = LinearLayout.HORIZONTAL
        scroll.addView(row, ViewGroup.LayoutParams(WRAP, MATCH))
        addView(scroll, LinearLayout.LayoutParams(0, MATCH, 1f))
        addView(page, LinearLayout.LayoutParams(Theme.dp(context, Theme.TOUCH), MATCH))
        Theme.clickable(page)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updatePageButton()
    }

    fun setCandidates(items: List<String>) {
        row.removeAllViews()
        items.forEachIndexed { index, item -> row.addView(itemView(item, index)) }
        scroll.scrollTo(0, 0)
        updatePageButton()
    }

    private fun updatePageButton() {
        val right = canScrollRight()
        val left = scroll.scrollX > 0
        page.icon = if (right) Icon.CHEVRON_RIGHT else Icon.CHEVRON_LEFT
        page.contentDescription = if (right) "下一页候选" else "上一页候选"
        page.visibility = if (right || left) View.VISIBLE else View.GONE
    }

    private fun canScrollRight() = row.width > scroll.width + scroll.scrollX

    private fun flip() {
        if (canScrollRight()) scroll.smoothScrollBy((scroll.width * PAGE).toInt(), 0)
        else scroll.smoothScrollTo(0, 0)
        post { updatePageButton() }
    }

    private fun itemView(text: String, index: Int) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.BODY_LARGE)
        gravity = Gravity.CENTER
        // the first candidate is what space commits, so it is marked
        if (index == 0) setTypeface(typeface, Typeface.BOLD)
        val pad = Theme.dp(context, 12f)
        setPadding(pad, 0, pad, 0)
        setOnClickListener { onPick(index) }
        Theme.clickable(this)
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val PAGE = 0.8f
    }
}
