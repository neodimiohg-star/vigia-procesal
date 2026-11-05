package co.vigiaprocesal.app.trabajo

import android.content.Context

class Ajustes(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    /** Cada cuántas horas se revisan los procesos en segundo plano. */
    var horas: Int
        get() = prefs.getInt("horas", 6)
        set(valor) {
            prefs.edit().putInt("horas", valor).apply()
        }

    /** Revisar solo cuando hay Wi-Fi (red sin límite de datos). */
    var soloWifi: Boolean
        get() = prefs.getBoolean("solo_wifi", false)
        set(valor) {
            prefs.edit().putBoolean("solo_wifi", valor).apply()
        }
}
