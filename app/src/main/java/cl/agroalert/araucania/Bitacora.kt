package cl.agroalert.araucania

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bitácora de campo: eventos del nodo (conexión, órdenes, cambios de riesgo,
 * desconexión). Con Firebase activo viven en Firestore; si no, en el teléfono.
 * Basada en la bitácora propuesta por Sebastián Torres, portada con su crédito.
 */
object Bitacora {
    private const val MAX = 60
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (BuildConfig.CLOUD_ENABLED) return
        prefs = context.getSharedPreferences("bitacora", Context.MODE_PRIVATE)
    }

    /** Agrega un evento al inicio; conserva los últimos MAX. Seguro desde cualquier hilo. */
    fun log(text: String) {
        if (BuildConfig.CLOUD_ENABLED) { CloudStore.logEvent(text); return }
        if (!::prefs.isInitialized) return
        val time = SimpleDateFormat("dd-MM HH:mm", Locale.getDefault()).format(Date())
        val lines = ArrayDeque(recent())
        lines.addFirst("$time · $text")
        while (lines.size > MAX) lines.removeLast()
        // Las tramas nunca contienen saltos de línea, así que uno sirve de separador.
        prefs.edit().putString("entries", lines.joinToString("\n")).apply()
    }

    fun recent(): List<String> {
        if (BuildConfig.CLOUD_ENABLED) return runCatching { CloudStore.recentEvents() }.getOrDefault(emptyList())
        if (!::prefs.isInitialized) return emptyList()
        return prefs.getString("entries", "")?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }
}
