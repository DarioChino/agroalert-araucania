package cl.agroalert.araucania

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import java.util.Locale

class DashboardActivity : BaseActivity() {
    private lateinit var connection: TextView
    private lateinit var source: TextView
    private lateinit var temperature: TextView
    private lateinit var humidity: TextView
    private lateinit var risk: TextView
    private lateinit var relay: TextView
    private lateinit var age: TextView
    private lateinit var banner: TextView
    private lateinit var events: TextView
    private lateinit var history: TextView
    private lateinit var panelView: LinearLayout
    private lateinit var notasView: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val observer: (NodeState) -> Unit = { render(it) }
    private val clock = object : Runnable {
        override fun run() { render(agro.node.state); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        page("02 / Panel", "Estado del huerto", "Una vista clara para anticiparte al frío.")
        tabBar(listOf("Panel", "Notas")) { i ->
            panelView.visibility = if (i == 0) View.VISIBLE else View.GONE
            notasView.visibility = if (i == 1) View.VISIBLE else View.GONE
        }
        val panel = panel().also { panelView = it }
        val notas = panel().also { notasView = it }

        source = label("", 12, amber, true, panel)
        connection = label("", 14, green, true, panel)
        age = label("Esperando la primera lectura…", 12, muted, parent = panel)
        banner = label("", 14, amber, true, panel)
        banner.visibility = View.GONE
        gap(parent = panel)
        val climate = card(panel)
        label("TEMPERATURA AMBIENTAL", 11, muted, true, climate)
        temperature = label("— °C", 48, ink, true, climate)
        humidity = label("Humedad relativa  — %", 18, muted, parent = climate)
        gap(12, climate)
        risk = label("Sin lectura", 18, muted, true, climate)
        val irrigation = card(panel)
        label("SISTEMA DE RIEGO", 11, muted, true, irrigation)
        gap(8, irrigation)
        relay = label("Esperando estado del nodo", 19, ink, true, irrigation)
        button("Alertas y riesgos", parent = panel) { startActivity(Intent(this, AlertsActivity::class.java)) }
        button("Controlar el riego", parent = panel) { startActivity(Intent(this, ControlActivity::class.java)) }
        button("Cambiar conexión", secondary = true, parent = panel) { disconnectAndReturn() }
        button("Cerrar sesión", secondary = true, parent = panel) { logout() }
        button(if (agro.darkMode) "Modo claro" else "Modo oscuro", secondary = true, parent = panel) { toggleDarkMode() }
        gap(parent = panel)
        label("ACTIVIDAD DE ESTA SESIÓN", 11, muted, true, panel)
        gap(8, panel)
        events = label("", 13, muted, parent = panel)
        gap(14, panel)
        label("BITÁCORA DE CAMPO · PERSISTENTE", 11, muted, true, panel)
        gap(8, panel)
        history = label("", 13, muted, parent = panel)

        val notasCard = card(notas)
        label("SOBRE ESTE PANEL", 11, muted, true, notasCard)
        gap(8, notasCard)
        label("El estado mostrado corresponde al relé; el prototipo no mide caudal de agua.", 14, muted, parent = notasCard)
        gap(12, notasCard)
        label("Umbrales del proyecto", 15, ink, true, notasCard)
        gap(8, notasCard)
        label("● Normal · sobre 10 °C", 14, green, parent = notasCard)
        label("● Prehelada · entre 5 y 10 °C", 14, amber, parent = notasCard)
        label("● Riesgo crítico · 5 °C o menos", 14, red, parent = notasCard)
        gap(12, notasCard)
        label(if (BuildConfig.CLOUD_ENABLED)
            "Los eventos quedan guardados en Firebase, no en el teléfono. Bitácora basada en la propuesta de Sebastián Torres."
        else "Los eventos quedan guardados en el teléfono aunque cierres la app. Bitácora basada en la propuesta de Sebastián Torres.",
            13, muted, parent = notasCard)
        panel.visibility = View.VISIBLE

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { disconnectAndReturn() }
        })
    }
    private fun disconnectAndReturn() { agro.node.disconnect(); finish() }
    private fun render(s: NodeState) {
        if (!::events.isInitialized) return
        source.text = when {
            s.simulated -> "SIMULACIÓN · NO SON MEDICIONES DEL HUERTO"
            s.bluetooth -> "NODO BLUETOOTH · TELEMETRÍA RECIBIDA"
            else -> "NODO WI-FI · TELEMETRÍA RECIBIDA"
        }
        val fresh = s.connected && s.receivedAt > 0 && SystemClock.elapsedRealtime() - s.receivedAt <= 7000
        connection.text = if (fresh) "● En línea · ${s.message}" else "● ${s.message}"
        connection.setTextColor(if (fresh) green else red)
        age.text = if (s.receivedAt > 0) {
            val seconds = (SystemClock.elapsedRealtime() - s.receivedAt) / 1000
            "${if (fresh) "Última lectura" else "Dato antiguo · última lectura"}: hace $seconds s"
        } else "Esperando la primera lectura válida…"
        val t = s.telemetry
        temperature.text = t?.temperature?.let { String.format(Locale.forLanguageTag("es-CL"), "%.1f °C", it) } ?: "— °C"
        humidity.text = t?.humidity?.let { "Humedad relativa  ${it.toInt()} %" } ?: "Humedad relativa  — %"
        risk.text = if (fresh) "● ${t?.risk?.label ?: "Sin lectura"}" else "Sin datos actuales"
        risk.setTextColor(when { !fresh -> muted; t?.risk == Risk.OK -> green; t?.risk == Risk.HELADA_CRITICA -> red; else -> amber })
        relay.text = if (t == null) "Esperando estado del nodo" else {
            "Relé ${if (t.relay) "encendido" else "apagado"} · ${if (t.automatic) "Automático" else "Manual"}${if (!fresh) "\nÚltimo estado conocido" else ""}"
        }
        events.text = s.events.joinToString("\n")
        history.text = Bitacora.recent().take(10).joinToString("\n")
        val levels = if (fresh && t != null) listOfNotNull(
            if (t.risk == Risk.SENSOR_ERROR) null else Protocol.temperatureLevel(t.risk),
            t.humidity?.let { Protocol.humidityLevel(it) }
        ) else emptyList()
        banner.text = when {
            Nivel.CRITICO in levels -> "⚠ RIESGO CRÍTICO EN EL HUERTO · revisa «Alertas y riesgos»"
            Nivel.ALERTA in levels -> "Alerta preventiva vigente · revisa «Alertas y riesgos»"
            else -> ""
        }
        banner.setTextColor(if (Nivel.CRITICO in levels) red else amber)
        banner.visibility = if (banner.text.isEmpty()) View.GONE else View.VISIBLE
    }
    override fun onStart() { super.onStart(); if (::events.isInitialized) { agro.node.observe(observer); handler.post(clock) } }
    override fun onStop() { agro.node.remove(observer); handler.removeCallbacks(clock); super.onStop() }
}
