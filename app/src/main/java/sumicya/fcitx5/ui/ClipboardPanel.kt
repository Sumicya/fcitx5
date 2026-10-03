package sumicya.fcitx5.ui

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ClipboardPanel(
    context: Context,
    private val onPick: (String) -> Unit,
    private val onPin: (String) -> Unit,
) : LinearLayout(context) {

    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val outline = Theme.outlineVariant(context)
    private var shown: List<String> = emptyList()

    init {
        orientation = LinearLayout.VERTICAL
        addView(
            ScrollView(context).apply { addView(list) },
            LinearLayout.LayoutParams(MATCH, MATCH)
        )
    }

    fun setItems(items: List<String>) {
        // the panel is rebuilt every time it opens; with a long clip in the
        // history that used to mean measuring the whole thing again each time
        if (items == shown) return
        shown = items.toList()
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "还没有复制过文字"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.BODY_MEDIUM)
                val pad = Theme.dp(context, 16f)
                setPadding(pad, pad, pad, pad)
            })
            return
        }
        for (item in items) {
            list.addView(row(item))
            list.addView(View(context).apply { setBackgroundColor(outline) },
                LinearLayout.LayoutParams(MATCH, 1))
        }
    }

    private fun row(item: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = Preview.of(item)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.BODY_LARGE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Theme.dp(context, 16f), Theme.dp(context, 12f), Theme.dp(context, 8f), Theme.dp(context, 12f))
            setOnClickListener { onPick(item) }
            Theme.clickable(this)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(IconView(context, Icon.STAR).apply {
            contentDescription = "收藏这条"
            setOnClickListener { onPin(item) }
            Theme.clickable(this)
        }, LinearLayout.LayoutParams(Theme.dp(context, Theme.TOUCH), Theme.dp(context, Theme.TOUCH)))
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
