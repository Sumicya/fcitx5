package sumicya.fcitx5

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
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
import sumicya.fcitx5.ui.Icon
import sumicya.fcitx5.ui.IconView
import sumicya.fcitx5.ui.Theme

/**
 * Settings as one list that opens downwards: a category expands in place, more
 * than one at a time, and nothing is pushed onto a back stack.
 *
 * Back therefore means one thing only — close what is open, then leave. Since
 * Android 16 the system runs the back gesture itself and never calls
 * [onBackPressed], so the callback is registered for as long as something is
 * open and dropped when nothing is, which leaves the exit animation to the
 * system. The window is also drawn behind the status and navigation bars
 * since Android 15, so the insets are applied here.
 */
class SettingsActivity : AppCompatActivity() {

    private class Group(
        val key: String,
        val title: Int,
        val summary: Int,
        val items: SettingsActivity.() -> List<Item>
    )

    private sealed class Item {
        class Toggle(val title: String, val summary: String, val on: Boolean, val set: (Boolean) -> Unit) : Item()
        class Scale(
            val title: String, val text: (Int) -> String,
            val value: Int, val min: Int, val max: Int, val set: (Int) -> Unit
        ) : Item()
        class Action(val title: String, val summary: String, val run: () -> Unit) : Item()
        class Choice(val title: String, val chosen: Boolean, val pick: () -> Unit) : Item()
        class Info(val title: String, val summary: String) : Item()
    }

    private val groups = listOf(
        Group("input_method", R.string.cat_input_method, R.string.cat_input_method_summary, SettingsActivity::inputMethod),
        Group("keyboard", R.string.cat_keyboard, R.string.cat_keyboard_summary, SettingsActivity::keyboardPage),
        Group("candidates", R.string.cat_candidates, R.string.cat_candidates_summary, SettingsActivity::candidates),
        Group("clipboard", R.string.cat_clipboard, R.string.cat_clipboard_summary, SettingsActivity::clipboard),
        Group("theme", R.string.cat_theme, R.string.cat_theme_summary, SettingsActivity::themePage),
        Group("advanced", R.string.cat_advanced, R.string.cat_advanced_summary, SettingsActivity::advanced),
        Group("about", R.string.cat_about, R.string.cat_about_summary, SettingsActivity::about),
    )

    private lateinit var toolbar: MaterialToolbar
    private lateinit var list: LinearLayout
    private val expanded = LinkedHashSet<String>()
    private val chevrons = HashMap<String, IconView>()
    private var backCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        applyNightMode()
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList(STATE_EXPANDED)?.let { expanded.addAll(it) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // the window is drawn behind the bars, so the strip behind the status
        // bar is ours to paint
        root.setBackgroundColor(
            Theme.color(this, com.google.android.material.R.attr.colorSurface, 0xFFFEF7FF.toInt(), 0xFF141218.toInt())
        )
        toolbar = MaterialToolbar(this)
        root.addView(toolbar, LinearLayout.LayoutParams(MATCH, WRAP))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            applyInsets(root, insets)
            insets
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_EXPANDED, ArrayList(expanded))
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            backCallback = null
        }
        super.onDestroy()
    }

    /** Status bar on top, navigation bar below: nothing else pads for them. */
    @Suppress("DEPRECATION")
    private fun applyInsets(root: View, insets: WindowInsets) {
        val top: Int
        val bottom: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            top = insets.getInsets(WindowInsets.Type.statusBars()).top
            bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            top = insets.systemWindowInsetTop
            bottom = insets.systemWindowInsetBottom
        }
        root.setPadding(0, top, 0, bottom)
    }

    private fun render() {
        chevrons.clear()
        toolbar.title = getString(R.string.settings_title)
        list.removeAllViews()
        list.addView(MaterialButton(this).apply {
            text = getString(R.string.enable_ime)
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            setMargins(dp(16), dp(16), dp(16), dp(8))
        })
        for (group in groups) {
            list.addView(MaterialDivider(this))
            list.addView(headerOf(group))
            if (group.key in expanded) {
                group.items(this).forEach { list.addView(rowOf(it, group.key)) }
            }
        }
        syncBackCallback()
    }

    private fun headerOf(group: Group) = LinearLayout(this).apply {
        tag = group.key
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        minimumHeight = dp(56)
        val ripple = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        setBackgroundResource(ripple.resourceId)
        addView(
            twoLines(
                getString(group.title),
                getString(group.summary),
                com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
            ),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        val chevron = IconView(this@SettingsActivity, Icon.CHEVRON_RIGHT).apply {
            // a chevron pointing right, stood on its head: Material's expand arrow
            rotation = if (group.key in expanded) 90f else 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        chevrons[group.key] = chevron
        addView(chevron, LinearLayout.LayoutParams(dp(48), dp(48)))
        setOnClickListener { toggle(group) }
    }

    private fun toggle(group: Group) {
        val open = group.key !in expanded
        if (open) expanded.add(group.key) else expanded.remove(group.key)
        chevrons[group.key]?.animate()?.rotation(if (open) 90f else 0f)?.setDuration(200)?.start()
        val header = list.findViewWithTag<View>(group.key) ?: return
        if (open) {
            val at = list.indexOfChild(header) + 1
            group.items(this).forEachIndexed { index, item ->
                list.addView(rowOf(item, group.key).apply {
                    alpha = 0f
                    animate().alpha(1f).setDuration(150).start()
                }, at + index)
            }
        } else {
            for (i in list.childCount - 1 downTo 0) {
                if (list.getChildAt(i).tag == rowTag(group.key)) list.removeViewAt(i)
            }
        }
        syncBackCallback()
    }

    /** Close every open category. Returns false when there was nothing to close. */
    private fun collapseAll(): Boolean {
        if (expanded.isEmpty()) return false
        expanded.clear()
        render()
        return true
    }

    @Suppress("MissingSuperCall", "DEPRECATION")
    override fun onBackPressed() {
        if (!collapseAll()) super.onBackPressed()
    }

    /**
     * Android 16 runs the back gesture in the system and stops calling
     * [onBackPressed], so the callback has to be registered — but only while
     * something is open, or the system would lose its exit animation.
     */
    private fun syncBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val wanted = expanded.isNotEmpty()
        if (wanted && backCallback == null) {
            val callback = OnBackInvokedCallback { collapseAll() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        } else if (!wanted && backCallback != null) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback!!)
            backCallback = null
        }
    }

    private fun rowOf(item: Item, group: String): View {
        val row = LinearLayout(this).apply {
            tag = rowTag(group)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            minimumHeight = dp(56)
            val ripple = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            setBackgroundResource(ripple.resourceId)
        }
        when (item) {
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
                val valueText = MaterialTextView(this).apply {
                    text = item.text(item.value)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                }
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(MaterialTextView(this@SettingsActivity).apply {
                        text = item.title
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                    })
                    addView(valueText)
                    addView(Slider(this@SettingsActivity).apply {
                        valueFrom = item.min.toFloat()
                        valueTo = item.max.toFloat()
                        value = item.value.toFloat()
                        stepSize = 1f
                        addOnChangeListener { _, v, fromUser ->
                            if (fromUser) {
                                valueText.text = item.text(v.toInt())
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

    private fun twoLines(
        title: String,
        summary: String,
        appearance: Int = com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(MaterialTextView(this@SettingsActivity).apply {
            text = title
            setTextAppearance(appearance)
        })
        if (summary.isNotEmpty()) addView(MaterialTextView(this@SettingsActivity).apply {
            text = summary
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        })
    }.apply { layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }

    /* ========== categories ========== */

    private fun inputMethod(): List<Item> = listOf(
        Item.Toggle(
            getString(R.string.pref_traditional), getString(R.string.pref_traditional_summary),
            Prefs.traditional(this)
        ) { value -> edit("traditional", value) },
    )

    private fun keyboardPage(): List<Item> = listOf(
        Item.Toggle(
            getString(R.string.pref_haptic), getString(R.string.pref_haptic_summary),
            Prefs.haptic(this)
        ) { value -> edit("haptic", value) },
        Item.Scale(
            getString(R.string.pref_height_title),
            { value -> getString(R.string.height_percent, value) },
            Prefs.heightPercent(this), Prefs.HEIGHT_MIN, Prefs.HEIGHT_MAX
        ) { value -> edit("height", value) },
    )

    private fun candidates(): List<Item> = listOf(
        Item.Toggle(
            getString(R.string.pref_digit_pick), getString(R.string.pref_digit_pick_summary),
            Prefs.digitPick(this)
        ) { value -> edit("digit_pick", value) },
    )

    private fun clipboard(): List<Item> = listOf(
        Item.Action(getString(R.string.clear_clipboard), getString(R.string.clear_clipboard_summary)) {
            confirm { ClipboardStore(this).clear() }
        },
    )

    private fun themePage(): List<Item> = listOf(
        Item.Choice(getString(R.string.theme_system), Prefs.theme(this) == "system") {
            Prefs.setTheme(this, "system")
            applyNightMode()
        },
        Item.Choice(getString(R.string.theme_light), Prefs.theme(this) == "light") {
            Prefs.setTheme(this, "light")
            applyNightMode()
        },
        Item.Choice(getString(R.string.theme_dark), Prefs.theme(this) == "dark") {
            Prefs.setTheme(this, "dark")
            applyNightMode()
        },
        Item.Info(getString(R.string.theme_note), getString(R.string.theme_note_summary)),
    )

    private fun advanced(): List<Item> = listOf(
        Item.Action(getString(R.string.clear_learned), getString(R.string.clear_learned_summary)) {
            confirm { UserDict(this).clear() }
        },
        Item.Action(getString(R.string.clear_phrases), getString(R.string.clear_phrases_summary)) {
            confirm { UserPhrases(this).clear() }
        },
        Item.Info(getString(R.string.info_data_dir), getString(R.string.info_data_dir_summary)),
    )

    private fun about(): List<Item> = listOf(
        Item.Info(getString(R.string.info_version), versionName()),
        Item.Info(getString(R.string.info_licence), getString(R.string.info_licence_summary)),
    )

    /** The keyboard and this screen follow the same setting, right away. */
    private fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            when (Prefs.theme(this)) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

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
        const val STATE_EXPANDED = "expanded"

        fun rowTag(group: String) = "row:$group"
    }
}
