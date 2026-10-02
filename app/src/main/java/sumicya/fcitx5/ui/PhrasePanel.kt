package sumicya.fcitx5.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PhrasePanel(
    context: Context,
    private val onPick: (String) -> Unit,
    private val onRemove: (String) -> Unit,
) : LinearLayout(context) {

    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        orientation = LinearLayout.VERTICAL
        addView(ScrollView(context).apply { addView(list) },
            LinearLayout.LayoutParams(MATCH, MATCH))
    }

    fun setItems(items: List<String>) {
        list.removeAllViews()
        items.forEach { item ->
            list.addView(row(item))
            list.addView(View(context).apply { setBackgroundColor(DIVIDER) },
                LinearLayout.LayoutParams(MATCH, 1))
        }
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "还没有常用短语。复制文字后，点剪贴板条目右边的 ★ 就存到这里，之后可以直接打拼音上屏。"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(14), dp(14), dp(14), dp(14))
            })
        }
    }

    private fun row(item: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(TextView(context).apply {
            text = item.replace('\n', ' ')
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 2
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(8), dp(12))
            setOnClickListener { onPick(item) }
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(TextView(context).apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { onRemove(item) }
        }, LinearLayout.LayoutParams(WRAP, WRAP))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val DIVIDER = 0x20000000.toInt()
    }
}
