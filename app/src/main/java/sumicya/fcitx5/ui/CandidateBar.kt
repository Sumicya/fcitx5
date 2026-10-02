package sumicya.fcitx5.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class CandidateBar(
    context: Context,
    private val onPick: (String) -> Unit,
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
        for (item in items) row.addView(itemView(item), LinearLayout.LayoutParams(WRAP, MATCH))
        scrollTo(0, 0)
    }

    private fun itemView(text: String) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        gravity = Gravity.CENTER
        val p = (12 * resources.displayMetrics.density + 0.5f).toInt()
        setPadding(p, 0, p, 0)
        setOnClickListener { onPick(text) }
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
