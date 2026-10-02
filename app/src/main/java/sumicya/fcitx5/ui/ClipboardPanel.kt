package sumicya.fcitx5.ui

import android.content.Context
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
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = "剪贴板  返回"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER_VERTICAL
            val p = (12 * resources.displayMetrics.density + 0.5f).toInt()
            setPadding(p, p, p, p)
            setOnClickListener { onClose() }
        }, LinearLayout.LayoutParams(MATCH, WRAP))
        addView(ScrollView(context).apply { addView(list) },
            LinearLayout.LayoutParams(MATCH, 0, 1f))
    }

    fun setItems(items: List<String>) {
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "还没有复制过文字"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                val p = (12 * resources.displayMetrics.density + 0.5f).toInt()
                setPadding(p, p, p, p)
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
        val p = (12 * resources.displayMetrics.density + 0.5f).toInt()
        setPadding(p, p, p, p)
        setOnClickListener { onPick(item) }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val DIVIDER = 0x20000000
    }
}
