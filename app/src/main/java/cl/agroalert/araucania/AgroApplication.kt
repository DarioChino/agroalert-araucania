package cl.agroalert.araucania

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class AgroApplication : Application() {
    private lateinit var ui: SharedPreferences
    val node = NodeConnection()
    lateinit var credentials: Credentials; private set
    var role: Role? = null
    /** Modo oscuro opcional, elegido por la persona usuaria y persistente. */
    val darkMode: Boolean get() = ui.getBoolean("dark", false)
    override fun onCreate() {
        super.onCreate()
        ui = getSharedPreferences("ui", Context.MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        credentials = Credentials(this)
    }
    fun setDarkMode(value: Boolean) { ui.edit().putBoolean("dark", value).apply() }
    fun logout() { node.disconnect("Sesión cerrada"); role = null }
}
