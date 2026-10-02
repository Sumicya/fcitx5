package sumicya.fcitx5.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import sumicya.fcitx5.data.Emoji

class EmojiPanel(context: Context, private val onPick: (String) -> Unit) : LinearLayout(context) {

    private val tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val body = ScrollView(context)
    private var group = 0

    init {
        orientation = LinearLayout.VERTICAL
        addView(HorizontalScrollView(context).apply { addView(tabs) },
            LinearLayout.LayoutParams(MATCH, dp(44)))
        addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        buildTabs()
    }

    private fun buildTabs() {
        tabs.removeAllViews()
        Emoji.groups.forEachIndexed { index, g ->
            tabs.addView(TextView(context).apply {
                text = g.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
                if (index == group) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setOnClickListener {
                    if (group != index) {
                        group = index
                        buildTabs()
                    }
                }
            }, LinearLayout.LayoutParams(WRAP, MATCH))
        }
        showGroup()
    }

    private fun showGroup() {
        val items = Emoji.groups[group].items
        val rowCount = (items.size + COLUMNS - 1) / COLUMNS
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until rowCount) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until COLUMNS) {
                val i = r * COLUMNS + c
                row.addView(
                    if (i < items.size) cell(items[i]) else View(context),
                    LinearLayout.LayoutParams(0, dp(48), 1f)
                )
            }
            grid.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        body.removeAllViews()
        body.addView(grid, LinearLayout.LayoutParams(MATCH, WRAP))
        body.scrollTo(0, 0)
    }

    private fun cell(emoji: String) = TextView(context).apply {
        text = emoji
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        gravity = Gravity.CENTER
        setOnClickListener { onPick(emoji) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val COLUMNS = 8
    }
}
