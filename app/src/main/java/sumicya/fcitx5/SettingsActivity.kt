package sumicya.fcitx5

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import sumicya.fcitx5.data.ClipboardStore
import sumicya.fcitx5.data.UserPhrases
import sumicya.fcitx5.engine.UserDict

/**
 * Settings in the shape upstream fcitx5-android uses: the same categories in
 * the same order, and the standard Material row, so anyone arriving from
 * upstream finds things where they expect them.
 */
class SettingsActivity : Activity() {

    private class Page(val title: String, val items: List<Item>)

    private sealed class Item {
        class Link(val title: String, val summary: String, val target: () -> Page) : Item()
        class Toggle(val title: String, val summary: String, val on: Boolean, val set: (Boolean) -> Unit) : Item()
        class Scale(
            val title: String, val summary: (Int) -> String,
            val value: Int, val min: Int, val max: Int, val set: (Int) -> Unit
        ) : Item()
        class Action(val title: String, val summary: String, val run: () -> Unit) : Item()
        class Choice(val title: String, val chosen: Boolean, val pick: () -> Unit) : Item()
        class Info(val title: String, val summary: String) : Item()
    }

    private val stack = ArrayDeque<Page>()
    private lateinit var titleView: TextView
    private lateinit var back: ImageButton
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(bar(), LinearLayout.LayoutParams(MATCH, WRAP))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
        if (stack.isEmpty()) stack.addLast(index())
        render()
    }

    @Suppress("MissingSuperCall", "DEPRECATION")
    override fun onBackPressed() {
        if (stack.size > 1) {
            stack.removeLast()
            render()
        } else {
            super.onBackPressed()
        }
    }

    private fun bar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        back = ImageButton(this@SettingsActivity).apply {
            val a = obtainStyledAttributes(intArrayOf(android.R.attr.homeAsUpIndicator))
            setImageDrawable(a.getDrawable(0))
            a.recycle()
            background = null
            setOnClickListener {
                stack.removeLast()
                render()
            }
        }
        addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        titleView = TextView(this@SettingsActivity).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(colorOf(android.R.attr.textColorPrimary))
            setPadding(dp(4), dp(12), dp(16), dp(12))
        }
        addView(titleView, LinearLayout.LayoutParams(0, WRAP, 1f))
    }

    private fun render() {
        val page = stack.last()
        titleView.text = page.title
        back.visibility = if (stack.size > 1) View.VISIBLE else View.INVISIBLE
        list.removeAllViews()
        for (item in page.items) list.addView(rowOf(item))
    }

    private fun rowOf(item: Item): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            minimumHeight = dp(56)
            val ripple = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            setBackgroundResource(ripple.resourceId)
        }
        when (item) {
            is Item.Link -> {
                row.addView(twoLines(item.title, item.summary), LinearLayout.LayoutParams(0, WRAP, 1f))
                row.setOnClickListener { stack.addLast(item.target()); render() }
            }
            is Item.Toggle -> {
                row.addView(twoLines(item.title, item.summary), LinearLayout.LayoutParams(0, WRAP, 1f))
                row.addView(Switch(this).apply {
                    isChecked = item.on
                    setOnCheckedChangeListener { _, value -> item.set(value) }
                }, LinearLayout.LayoutParams(WRAP, WRAP))
                row.setOnClickListener { }
            }
            is Item.Scale -> {
                val value = TextView(this).apply {
                    text = item.summary(item.value)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(colorOf(android.R.attr.textColorSecondary))
                }
                val column = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(this@SettingsActivity).apply {
                        text = item.title
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setTextColor(colorOf(android.R.attr.textColorPrimary))
                    })
                    addView(value)
                    addView(SeekBar(this@SettingsActivity).apply {
                        max = item.max - item.min
                        progress = item.value - item.min
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(bar: SeekBar, v: Int, fromUser: Boolean) {
                                val next = v + item.min
                                value.text = item.summary(next)
                                if (fromUser) item.set(next)
                            }
                            override fun onStartTrackingTouch(bar: SeekBar) = Unit
                            override fun onStopTrackingTouch(bar: SeekBar) = Unit
                        })
                    }, LinearLayout.LayoutParams(MATCH, WRAP))
                }
                row.addView(column, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            is Item.Action -> {
                row.addView(twoLines(item.title, item.summary), LinearLayout.LayoutParams(0, WRAP, 1f))
                row.setOnClickListener { item.run() }
            }
            is Item.Choice -> {
                row.addView(TextView(this).apply {
                    text = item.title
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(colorOf(android.R.attr.textColorPrimary))
                    setPadding(dp(8), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                if (item.chosen) row.addView(TextView(this).apply {
                    text = "✓"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTextColor(colorOf(android.R.attr.colorAccent))
                }, LinearLayout.LayoutParams(WRAP, WRAP))
                row.setOnClickListener { item.pick(); render() }
            }
            is Item.Info -> {
                row.addView(twoLines(item.title, item.summary), LinearLayout.LayoutParams(0, WRAP, 1f))
                row.isClickable = false
                row.background = null
            }
        }
        return row
    }

    private fun twoLines(title: String, summary: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@SettingsActivity).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colorOf(android.R.attr.textColorPrimary))
        })
        if (summary.isNotEmpty()) addView(TextView(this@SettingsActivity).apply {
            text = summary
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(colorOf(android.R.attr.textColorSecondary))
        })
    }

    /* ========== pages ========== */

    private fun index() = Page(getString(R.string.settings_title), listOf(
        Item.Action(getString(R.string.enable_ime), getString(R.string.enable_ime_summary)) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        },
        Item.Link(getString(R.string.cat_input_method), getString(R.string.cat_input_method_summary), ::inputMethod),
        Item.Link(getString(R.string.cat_keyboard), getString(R.string.cat_keyboard_summary), ::keyboard),
        Item.Link(getString(R.string.cat_candidates), getString(R.string.cat_candidates_summary), ::candidates),
        Item.Link(getString(R.string.cat_clipboard), getString(R.string.cat_clipboard_summary), ::clipboard),
        Item.Link(getString(R.string.cat_theme), getString(R.string.cat_theme_summary), ::themePage),
        Item.Link(getString(R.string.cat_advanced), getString(R.string.cat_advanced_summary), ::advanced),
        Item.Link(getString(R.string.cat_about), getString(R.string.cat_about_summary), ::about),
    ))

    private fun inputMethod() = Page(getString(R.string.cat_input_method), listOf(
        Item.Toggle(
            getString(R.string.pref_traditional), getString(R.string.pref_traditional_summary),
            Prefs.traditional(this)
        ) { value -> getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("traditional", value).apply() },
        Item.Info(getString(R.string.info_dict), getString(R.string.info_dict_summary)),
        Item.Info(getString(R.string.info_freq), getString(R.string.info_freq_summary)),
    ))

    private fun keyboard() = Page(getString(R.string.cat_keyboard), listOf(
        Item.Toggle(
            getString(R.string.pref_haptic), getString(R.string.pref_haptic_summary),
            Prefs.haptic(this)
        ) { value -> getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("haptic", value).apply() },
        Item.Scale(
            getString(R.string.pref_height_title),
            { value -> getString(R.string.height_percent, value) },
            Prefs.heightPercent(this), Prefs.HEIGHT_MIN, Prefs.HEIGHT_MAX
        ) { value -> getSharedPreferences("prefs", MODE_PRIVATE).edit().putInt("height", value).apply() },
        Item.Info(getString(R.string.pref_height_summary), ""),
    ))

    private fun candidates() = Page(getString(R.string.cat_candidates), listOf(
        Item.Toggle(
            getString(R.string.pref_digit_pick), getString(R.string.pref_digit_pick_summary),
            Prefs.digitPick(this)
        ) { value -> getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("digit_pick", value).apply() },
        Item.Info(getString(R.string.candidate_ranking), getString(R.string.candidate_ranking_summary)),
    ))

    private fun clipboard() = Page(getString(R.string.cat_clipboard), listOf(
        Item.Info(getString(R.string.clipboard_unlimited), getString(R.string.clipboard_unlimited_summary)),
        Item.Action(getString(R.string.clear_clipboard), getString(R.string.clear_clipboard_summary)) {
            confirm { ClipboardStore(this).clear() }
        },
    ))

    private fun themePage() = Page(getString(R.string.cat_theme), listOf(
        Item.Choice(getString(R.string.theme_system), Prefs.theme(this) == "system") { Prefs.setTheme(this, "system") },
        Item.Choice(getString(R.string.theme_light), Prefs.theme(this) == "light") { Prefs.setTheme(this, "light") },
        Item.Choice(getString(R.string.theme_dark), Prefs.theme(this) == "dark") { Prefs.setTheme(this, "dark") },
    ))

    private fun advanced() = Page(getString(R.string.cat_advanced), listOf(
        Item.Action(getString(R.string.clear_learned), getString(R.string.clear_learned_summary)) {
            confirm { UserDict(this).clear() }
        },
        Item.Action(getString(R.string.clear_phrases), getString(R.string.clear_phrases_summary)) {
            confirm { UserPhrases(this).clear() }
        },
        Item.Info(getString(R.string.info_data_dir), getString(R.string.info_data_dir_summary)),
    ))

    private fun about() = Page(getString(R.string.cat_about), listOf(
        Item.Info(getString(R.string.info_version), versionName()),
        Item.Info(getString(R.string.info_licence), getString(R.string.info_licence_summary)),
    ))

    private fun confirm(run: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_title)
            .setMessage(R.string.confirm_clear)
            .setNegativeButton(R.string.confirm_no, null)
            .setPositiveButton(R.string.confirm_yes) { _, _ -> run() }
            .show()
    }

    private fun versionName() = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private fun colorOf(attr: Int): ColorStateList {
        val a = obtainStyledAttributes(intArrayOf(attr))
        val color = a.getColorStateList(0) ?: ColorStateList.valueOf(
            if (attr == android.R.attr.textColorSecondary) Color.GRAY else Color.BLACK
        )
        a.recycle()
        return color
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
