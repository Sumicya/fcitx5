package sumicya.fcitx5.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ClipboardPanel(context: Context, private val onPick: (String) -> Unit) : LinearLayout(context) {

    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        orientation = LinearLayout.VERTICAL
        addView(ScrollView(context).apply { addView(list) },
            LinearLayout.LayoutParams(MATCH, MATCH))
    }

    fun setItems(items: List<String>) {
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "还没有复制过文字"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(14), dp(14), dp(14), dp(14))
            })
            return
        }
        for (item in items) {
            list.addView(row(item))
            list.addView(View(context).apply { setBackgroundColor(DIVIDER) },
                LinearLayout.LayoutParams(MATCH, 1))
        }
    }

    private fun row(item: String) = TextView(context).apply {
        text = item.replace('\n', ' ')
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        maxLines = 2
        gravity = Gravity.CENTER_VERTICAL
        setTypeface(typeface, Typeface.DEFAULT_BOLD.style)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setOnClickListener { onPick(item) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val DIVIDER = 0x20000000.toInt()
    }
}
