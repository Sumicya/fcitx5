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

/** The menu panel: clipboard history, saved phrases and emoji, one row of tabs. */
class Panel(
    context: Context,
    private val onPick: (String) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val clip = ClipboardPanel(context, onPick = { onPick(it) }, onPin = { onPin(it) })
    private val phrase = PhrasePanel(context, onPick = { onPick(it) }, onRemove = { onRemove(it) })
    private val emoji = EmojiPanel(context) { onPick(it) }
    private val tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val tabViews = mutableListOf<TextView>()
    private var tab = 0

    /** Save the clip the user starred as a phrase. */
    var onPin: (String) -> Unit = {}

    /** Forget a saved phrase. */
    var onRemove: (String) -> Unit = {}

    init {
        orientation = LinearLayout.VERTICAL
        listOf("剪贴板", "常用", "表情").forEachIndexed { index, name ->
            val view = tabView(name)
            tabViews.add(view)
            tabs.addView(view, LinearLayout.LayoutParams(WRAP, MATCH))
        }
        tabs.addView(TextView(context).apply {
            text = "返回"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.BODY_MEDIUM)
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            val pad = Theme.dp(context, 16f)
            setPadding(pad, 0, pad, 0)
            setOnClickListener { onClose() }
            Theme.clickable(this)
        }, LinearLayout.LayoutParams(MATCH, MATCH))
        addView(tabs, LinearLayout.LayoutParams(MATCH, Theme.dp(context, Theme.TOUCH)))
        addView(FrameLayout(context).apply {
            addView(clip, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(phrase, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(emoji, FrameLayout.LayoutParams(MATCH, MATCH))
        }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        select(0)
    }

    fun setClips(items: List<String>) = clip.setItems(items)

    fun setPhrases(items: List<String>) = phrase.setItems(items)

    private fun tabView(name: String) = TextView(context).apply {
        text = name
        setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.BODY_MEDIUM)
        gravity = Gravity.CENTER
        val pad = Theme.dp(context, 16f)
        setPadding(pad, 0, pad, 0)
        setOnClickListener { select(tabViews.indexOf(this)) }
        Theme.clickable(this)
    }

    private fun select(index: Int) {
        tab = index
        clip.visibility = if (index == 0) View.VISIBLE else View.GONE
        phrase.visibility = if (index == 1) View.VISIBLE else View.GONE
        emoji.visibility = if (index == 2) View.VISIBLE else View.GONE
        for ((i, view) in tabViews.withIndex()) {
            view.setTypeface(view.typeface, if (i == index) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
