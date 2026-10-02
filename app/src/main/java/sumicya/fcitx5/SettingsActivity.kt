package sumicya.fcitx5

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(Button(this).apply {
            text = getString(R.string.enable_ime)
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        root.addView(checkRow(getString(R.string.pref_haptic), prefs.getBoolean("haptic", true)) {
            prefs.edit().putBoolean("haptic", it).apply()
        })
        root.addView(
            checkRow(getString(R.string.pref_traditional), prefs.getBoolean("traditional", false)) {
                prefs.edit().putBoolean("traditional", it).apply()
            }
        )
        root.addView(
            checkRow(
                getString(R.string.pref_force_dark),
                prefs.getString("theme", "system") == "dark"
            ) {
                prefs.edit().putString("theme", if (it) "dark" else "system").apply()
            }
        )
        setContentView(root)
    }

    private fun checkRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(CheckBox(this@SettingsActivity).apply {
                text = title
                isChecked = checked
                setOnCheckedChangeListener { _, value -> onChange(value) }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
