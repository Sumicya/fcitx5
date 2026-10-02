package sumicya.fcitx5.ui

import android.content.Context
import android.graphics.Typeface
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
    private val tabViews = mutableListOf<TextView>()
    private var group = 0

    init {
        orientation = LinearLayout.VERTICAL
        addView(
            HorizontalScrollView(context).apply { addView(tabs) },
            LinearLayout.LayoutParams(MATCH, Theme.dp(context, Theme.TOUCH))
        )
        addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        buildTabs()
    }

    private fun buildTabs() {
        tabs.removeAllViews()
        tabViews.clear()
        Emoji.groups.forEachIndexed { index, g ->
            val view = TextView(context).apply {
                text = g.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.BODY_MEDIUM)
                gravity = Gravity.CENTER
                val pad = Theme.dp(context, 12f)
                setPadding(pad, 0, pad, 0)
                setOnClickListener { select(index) }
                Theme.clickable(this)
            }
            tabViews.add(view)
            tabs.addView(view, LinearLayout.LayoutParams(WRAP, MATCH))
        }
        select(group)
    }

    private fun select(index: Int) {
        group = index
        for ((i, view) in tabViews.withIndex()) {
            view.setTypeface(view.typeface, if (i == index) Typeface.BOLD else Typeface.NORMAL)
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
                    LinearLayout.LayoutParams(0, Theme.dp(context, Theme.TOUCH), 1f)
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
        setTextSize(TypedValue.COMPLEX_UNIT_SP, EMOJI_SIZE)
        gravity = Gravity.CENTER
        // the glyph is the whole content, so it is also what gets read out
        contentDescription = emoji
        setOnClickListener { onPick(emoji) }
        Theme.clickable(this)
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val COLUMNS = 8
        const val EMOJI_SIZE = 24f
    }
}
