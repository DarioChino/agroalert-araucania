package cl.agroalert.araucania

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private lateinit var events: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val observer: (NodeState) -> Unit = { render(it) }
    private val clock = object : Runnable {
        override fun run() { render(agro.node.state); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        page("02 / Panel · ${agro.role!!.label}", "Estado del huerto", "Una vista clara para anticiparte al frío.")
        source = label("", 12, amber, true)
        connection = label("", 14, green, true)
        age = label("Esperando la primera lectura…", 12, muted)
        gap()
        val climate = card()
        label("TEMPERATURA AMBIENTAL", 11, muted, true, climate)
        temperature = label("— °C", 48, ink, true, climate)
        humidity = label("Humedad relativa  — %", 18, muted, parent = climate)
        gap(12, climate)
        risk = label("Sin lectura", 18, muted, true, climate)
        val irrigation = card()
        label("SISTEMA DE RIEGO", 11, muted, true, irrigation)
        gap(8, irrigation)
        relay = label("Esperando estado del nodo", 19, ink, true, irrigation)
        label("El estado mostrado corresponde al relé; el prototipo no mide caudal de agua.", 12, muted, parent = irrigation)
        val limits = card()
        label("Umbrales del proyecto", 15, ink, true, limits)
        gap(8, limits)
        label("● Normal             Más de 10 °C", 14, green, parent = limits)
        label("● Prehelada         Más de 5 y hasta 10 °C", 14, amber, parent = limits)
        label("● Riesgo crítico   5 °C o menos", 14, red, parent = limits)
        if (agro.role == Role.CONTROL) {
            button("Controlar el riego") { startActivity(Intent(this, ControlActivity::class.java)) }
        } else {
            label("MODO MONITOR · Acceso de solo lectura", 13, green, true)
            gap()
        }
        button("Cambiar conexión", secondary = true) { disconnectAndReturn() }
        button("Cerrar sesión", secondary = true) { logout() }
        gap()
        label("ACTIVIDAD DE ESTA SESIÓN", 11, muted, true)
        gap(8)
        events = label("", 13, muted)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { disconnectAndReturn() }
        })
    }
    private fun disconnectAndReturn() { agro.node.disconnect(); finish() }
    private fun render(s: NodeState) {
        if (!::events.isInitialized) return
        source.text = if (s.simulated) "SIMULACIÓN · NO SON MEDICIONES DEL HUERTO" else "NODO WI-FI · TELEMETRÍA RECIBIDA"
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
    }
    override fun onStart() { super.onStart(); if (::events.isInitialized) { agro.node.observe(observer); handler.post(clock) } }
    override fun onStop() { agro.node.remove(observer); handler.removeCallbacks(clock); super.onStop() }
}
