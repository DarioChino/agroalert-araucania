package cl.agroalert.araucania

import android.content.Context
import android.content.SharedPreferences

/**
 * Cola de órdenes pendientes de confirmación. Con Firebase activo vive en
 * Firestore; si no, en SharedPreferences del teléfono.
 */
object Buffer {
    private const val MAX = 5
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (BuildConfig.CLOUD_ENABLED) return
        prefs = context.getSharedPreferences("buffer", Context.MODE_PRIVATE)
    }

    /** Encola una orden pendiente de confirmación; true si quedó guardada. */
    fun queue(command: Char): Boolean {
        if (BuildConfig.CLOUD_ENABLED) return runCatching { CloudStore.queue(command) }.getOrDefault(false)
        if (!::prefs.isInitialized) return false
        val cola = (prefs.getString("ordenes", "") ?: "").filter { it in "10A" }
        if (cola.length >= MAX) return false
        prefs.edit().putString("ordenes", cola + command).apply()
        return true
    }

    fun pending(): Int {
        if (BuildConfig.CLOUD_ENABLED) return runCatching { CloudStore.pending() }.getOrDefault(0)
        if (!::prefs.isInitialized) return 0
        return (prefs.getString("ordenes", "") ?: "").length
    }

    /** Vacía la cola y devuelve las órdenes para reenviarlas al reconectar. */
    fun drain(): List<Char> {
        if (BuildConfig.CLOUD_ENABLED) return runCatching { CloudStore.drain() }.getOrDefault(emptyList())
        if (!::prefs.isInitialized) return emptyList()
        val cola = (prefs.getString("ordenes", "") ?: "").toList()
        if (cola.isNotEmpty()) prefs.edit().putString("ordenes", "").apply()
        return cola
    }
}
