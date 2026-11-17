package cl.agroalert.araucania

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bitácora de campo persistida: los eventos del nodo (conexión, órdenes, cambios
 * de riesgo, desconexión) quedan en el teléfono aunque se cierre la app.
 * Basada en la bitácora propuesta por Sebastián Torres
 * (repo IOT-APP-ANDROID-STUDIO-AGROALERT), portada con su crédito.
 */
object Bitacora {
    private const val MAX = 60
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bitacora", Context.MODE_PRIVATE)
    }

    /** Agrega un evento al inicio; conserva los últimos MAX. Seguro desde cualquier hilo. */
    fun log(text: String) {
        if (!::prefs.isInitialized) return
        val time = SimpleDateFormat("dd-MM HH:mm", Locale.getDefault()).format(Date())
        val lines = ArrayDeque(recent())
        lines.addFirst("$time · $text")
        while (lines.size > MAX) lines.removeLast()
        // Las tramas nunca contienen saltos de línea, así que uno sirve de separador.
        prefs.edit().putString("entries", lines.joinToString("\n")).apply()
    }

    fun recent(): List<String> {
        if (!::prefs.isInitialized) return emptyList()
        return prefs.getString("entries", "")?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }
}
