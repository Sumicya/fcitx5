package sumicya.fcitx5.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** The ☰ panel: clipboard history and emoji, one row of tabs to switch. */
class Panel(
    context: Context,
    private val onPick: (String) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val clip = ClipboardPanel(context) { onPick(it) }
    private val emoji = EmojiPanel(context) { onPick(it) }
    private val tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private var tab = 0

    init {
        orientation = LinearLayout.VERTICAL
        tabs.addView(tabView("剪贴板", 0), LinearLayout.LayoutParams(WRAP, MATCH))
        tabs.addView(tabView("表情", 1), LinearLayout.LayoutParams(WRAP, MATCH))
        tabs.addView(TextView(context).apply {
            text = "返回"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { onClose() }
        }, LinearLayout.LayoutParams(MATCH, MATCH))
        addView(tabs, LinearLayout.LayoutParams(MATCH, dp(44)))
        addView(FrameLayout(context).apply {
            addView(clip, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(emoji, FrameLayout.LayoutParams(MATCH, MATCH))
        }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        select(0)
    }

    fun setClips(items: List<String>) = clip.setItems(items)

    private fun tabView(name: String, index: Int) = TextView(context).apply {
        text = name
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        setTypeface(typeface, if (index == tab) Typeface.BOLD else Typeface.NORMAL)
        setOnClickListener { select(index) }
    }

    private fun select(index: Int) {
        tab = index
        clip.visibility = if (index == 0) View.VISIBLE else View.GONE
        emoji.visibility = if (index == 1) View.VISIBLE else View.GONE
        for (i in 0 until tabs.childCount) {
            (tabs.getChildAt(i) as? TextView)?.setTypeface(
                typeface, if (i == index) Typeface.BOLD else Typeface.NORMAL
            )
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
