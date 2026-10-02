package sumicya.fcitx5

import android.content.Context

object Prefs {

    private const val FILE = "prefs"
    const val HEIGHT_MIN = 70
    const val HEIGHT_MAX = 130

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun haptic(context: Context) = prefs(context).getBoolean("haptic", true)

    /** Serve traditional characters instead of simplified ones. */
    fun traditional(context: Context) = prefs(context).getBoolean("traditional", false)

    /** Keyboard height as a percentage of the default, clamped on read. */
    fun heightPercent(context: Context) =
        prefs(context).getInt("height", 100).coerceIn(HEIGHT_MIN, HEIGHT_MAX)

    /** null = follow system night mode. */
    fun dark(context: Context): Boolean? = when (prefs(context).getString("theme", "system")) {
        "dark" -> true
        "light" -> false
        else -> null
    }
}
