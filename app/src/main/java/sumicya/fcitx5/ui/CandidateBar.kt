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

class CandidateBar(
    context: Context,
    private val onPick: (Int) -> Unit,
) : HorizontalScrollView(context) {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    init {
        isHorizontalScrollBarEnabled = false
        addView(row, ViewGroup.LayoutParams(WRAP, MATCH))
    }

    fun setCandidates(items: List<String>) {
        row.removeAllViews()
        items.forEachIndexed { index, item ->
            if (index > 0) row.addView(divider())
            row.addView(itemView(item, index), LinearLayout.LayoutParams(WRAP, MATCH))
        }
        scrollTo(0, 0)
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(DIVIDER)
    }

    private fun itemView(text: String, index: Int) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        gravity = Gravity.CENTER
        // the first candidate is what space commits, so it is marked
        if (index == 0) setTypeface(typeface, Typeface.BOLD)
        val p = (12 * resources.displayMetrics.density + 0.5f).toInt()
        setPadding(p, 0, p, 0)
        setOnClickListener { onPick(index) }
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val DIVIDER = 0x20000000.toInt()
    }
}
