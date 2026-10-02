package sumicya.fcitx5

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import sumicya.fcitx5.data.ClipboardStore
import sumicya.fcitx5.data.UserPhrases
import sumicya.fcitx5.engine.UserDict

/**
 * Settings in the shape upstream fcitx5-android uses: the same categories in
 * the same order, built out of Material 3 Expressive components, so anyone
 * arriving from upstream finds things where they expect them.
 */
class SettingsActivity : AppCompatActivity() {

    private class Page(
        val title: String,
        val items: List<Item>,
        val button: Pair<String, () -> Unit>? = null
    )

    private sealed class Item {
        class Link(val title: String, val summary: String, val target: () -> Page) : Item()
        class Toggle(val title: String, val summary: String, val on: Boolean, val set: (Boolean) -> Unit) : Item()
        class Scale(
            val title: String, val text: (Int) -> String,
            val value: Int, val min: Int, val max: Int, val set: (Int) -> Unit
        ) : Item()
        class Action(val title: String, val summary: String, val run: () -> Unit) : Item()
        class Choice(val title: String, val chosen: Boolean, val pick: () -> Unit) : Item()
        class Info(val title: String, val summary: String) : Item()
    }

    private val stack = ArrayDeque<Page>()
    private lateinit var toolbar: MaterialToolbar
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(
            when (Prefs.theme(this)) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toolbar = MaterialToolbar(this)
        root.addView(toolbar, LinearLayout.LayoutParams(MATCH, WRAP))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
        if (stack.isEmpty()) stack.addLast(index())
        render()
    }

    @Suppress("MissingSuperCall", "DEPRECATION")
    override fun onBackPressed() {
        if (stack.size > 1) goBack() else super.onBackPressed()
    }

    private fun goBack() {
        stack.removeLast()
        render()
    }

    private fun render() {
        val page = stack.last()
        toolbar.title = page.title
        if (stack.size > 1) {
            val up = TypedValue()
            if (theme.resolveAttribute(android.R.attr.homeAsUpIndicator, up, true) && up.resourceId != 0) {
                toolbar.setNavigationIcon(up.resourceId)
            }
            toolbar.setNavigationOnClickListener { goBack() }
        } else {
            toolbar.navigationIcon = null
        }
        list.removeAllViews()
        page.button?.let { (label, run) ->
            list.addView(MaterialButton(this).apply {
                text = label
                setOnClickListener { run() }
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(dp(16), dp(16), dp(16), dp(8))
            })
        }
        page.items.forEachIndexed { i, item ->
            if (i > 0 || page.button != null) list.addView(MaterialDivider(this))
            list.addView(rowOf(item))
        }
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
                row.addView(twoLines(item.title, item.summary))
                row.setOnClickListener { stack.addLast(item.target()); render() }
            }
            is Item.Toggle -> {
                row.addView(twoLines(item.title, item.summary))
                val toggle = MaterialSwitch(this).apply {
                    isChecked = item.on
                    setOnCheckedChangeListener { _, value -> item.set(value) }
                }
                row.addView(toggle, LinearLayout.LayoutParams(WRAP, WRAP))
                row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
            }
            is Item.Scale -> {
                val value = MaterialTextView(this).apply {
                    text = item.text(item.value)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                }
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(MaterialTextView(this@SettingsActivity).apply {
                        text = item.title
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                    })
                    addView(value)
                    addView(Slider(this@SettingsActivity).apply {
                        valueFrom = item.min.toFloat()
                        valueTo = item.max.toFloat()
                        value = item.value.toFloat()
                        stepSize = 1f
                        addOnChangeListener { _, v, fromUser ->
                            if (fromUser) {
                                value.text = item.text(v.toInt())
                                item.set(v.toInt())
                            }
                        }
                    }, LinearLayout.LayoutParams(MATCH, WRAP))
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            is Item.Action -> {
                row.addView(twoLines(item.title, item.summary))
                row.setOnClickListener { item.run() }
            }
            is Item.Choice -> {
                row.addView(MaterialTextView(this).apply {
                    text = item.title
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                if (item.chosen) row.addView(MaterialTextView(this).apply {
                    text = "✓"
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                }, LinearLayout.LayoutParams(WRAP, WRAP))
                row.setOnClickListener { item.pick(); render() }
            }
            is Item.Info -> {
                row.addView(twoLines(item.title, item.summary))
                row.isClickable = false
                row.background = null
            }
        }
        return row
    }

    private fun twoLines(title: String, summary: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(MaterialTextView(this@SettingsActivity).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        })
        if (summary.isNotEmpty()) addView(MaterialTextView(this@SettingsActivity).apply {
            text = summary
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        })
    }.apply { layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }

    /* ========== pages ========== */

    private fun index() = Page(
        getString(R.string.settings_title),
        listOf(
            Item.Link(getString(R.string.cat_input_method), getString(R.string.cat_input_method_summary), ::inputMethod),
            Item.Link(getString(R.string.cat_keyboard), getString(R.string.cat_keyboard_summary), ::keyboard),
            Item.Link(getString(R.string.cat_candidates), getString(R.string.cat_candidates_summary), ::candidates),
            Item.Link(getString(R.string.cat_clipboard), getString(R.string.cat_clipboard_summary), ::clipboard),
            Item.Link(getString(R.string.cat_theme), getString(R.string.cat_theme_summary), ::themePage),
            Item.Link(getString(R.string.cat_advanced), getString(R.string.cat_advanced_summary), ::advanced),
            Item.Link(getString(R.string.cat_about), getString(R.string.cat_about_summary), ::about),
        ),
        getString(R.string.enable_ime) to {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
    )

    private fun inputMethod() = Page(getString(R.string.cat_input_method), listOf(
        Item.Toggle(
            getString(R.string.pref_traditional), getString(R.string.pref_traditional_summary),
            Prefs.traditional(this)
        ) { value -> edit("traditional", value) },
        Item.Info(getString(R.string.info_dict), getString(R.string.info_dict_summary)),
        Item.Info(getString(R.string.info_freq), getString(R.string.info_freq_summary)),
    ))

    private fun keyboard() = Page(getString(R.string.cat_keyboard), listOf(
        Item.Toggle(
            getString(R.string.pref_haptic), getString(R.string.pref_haptic_summary),
            Prefs.haptic(this)
        ) { value -> edit("haptic", value) },
        Item.Scale(
            getString(R.string.pref_height_title),
            { value -> getString(R.string.height_percent, value) },
            Prefs.heightPercent(this), Prefs.HEIGHT_MIN, Prefs.HEIGHT_MAX
        ) { value -> edit("height", value) },
        Item.Info(getString(R.string.pref_height_summary), ""),
    ))

    private fun candidates() = Page(getString(R.string.cat_candidates), listOf(
        Item.Toggle(
            getString(R.string.pref_digit_pick), getString(R.string.pref_digit_pick_summary),
            Prefs.digitPick(this)
        ) { value -> edit("digit_pick", value) },
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
        Item.Info(getString(R.string.theme_note), getString(R.string.theme_note_summary)),
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

    private fun edit(key: String, value: Boolean) {
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }

    private fun edit(key: String, value: Int) {
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putInt(key, value).apply()
    }

    private fun confirm(run: () -> Unit) {
        MaterialAlertDialogBuilder(this)
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
