package cl.agroalert.araucania

import android.content.Context
import android.content.SharedPreferences

/**
 * Almacenamiento temporal para sincronizar con el nodo al restablecerse la
 * conexión: cola de órdenes que no alcanzaron confirmación mientras el enlace
 * estaba caído. Persiste en el teléfono (SharedPreferences).
 */
object Buffer {
    private const val MAX = 5
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("buffer", Context.MODE_PRIVATE)
    }

    /** Encola una orden pendiente de confirmación; true si quedó guardada. */
    fun queue(command: Char): Boolean {
        if (!::prefs.isInitialized) return false
        val cola = (prefs.getString("ordenes", "") ?: "").filter { it in "10A" }
        if (cola.length >= MAX) return false
        prefs.edit().putString("ordenes", cola + command).apply()
        return true
    }

    fun pending(): Int {
        if (!::prefs.isInitialized) return 0
        return (prefs.getString("ordenes", "") ?: "").length
    }

    /** Vacía la cola y devuelve las órdenes para reenviarlas al reconectar. */
    fun drain(): List<Char> {
        if (!::prefs.isInitialized) return emptyList()
        val cola = (prefs.getString("ordenes", "") ?: "").toList()
        if (cola.isNotEmpty()) prefs.edit().putString("ordenes", "").apply()
        return cola
    }
}
