package cl.agroalert.araucania

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class AgroApplication : Application() {
    private lateinit var ui: SharedPreferences
    val node = NodeConnection()
    lateinit var credentials: Credentials; private set
    /** Sesión en memoria: una sola clase de cuenta, sin perfiles. */
    var loggedIn = false
    /** Modo oscuro opcional, elegido por la persona usuaria y persistente. */
    val darkMode: Boolean get() = ui.getBoolean("dark", false)
    override fun onCreate() {
        super.onCreate()
        ui = getSharedPreferences("ui", Context.MODE_PRIVATE)
        Bitacora.init(this)
        Buffer.init(this)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        credentials = Credentials(this)
    }
    fun setDarkMode(value: Boolean) { ui.edit().putBoolean("dark", value).apply() }
    fun logout() {
        node.disconnect("Sesión cerrada")
        if (BuildConfig.CLOUD_ENABLED) CloudStore.signOut()
        loggedIn = false
    }
}
