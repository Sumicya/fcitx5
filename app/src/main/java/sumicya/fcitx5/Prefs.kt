package sumicya.fcitx5

import android.content.Context

object Prefs {

    private const val FILE = "prefs"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun haptic(context: Context) = prefs(context).getBoolean("haptic", true)

    /** Serve traditional characters instead of simplified ones. */
    fun traditional(context: Context) = prefs(context).getBoolean("traditional", false)

    /** null = follow system night mode. */
    fun dark(context: Context): Boolean? = when (prefs(context).getString("theme", "system")) {
        "dark" -> true
        "light" -> false
        else -> null
    }
}
