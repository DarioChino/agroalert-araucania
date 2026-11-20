package cl.agroalert.araucania

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import java.util.Locale

/** Riesgos del huerto evaluados factor por factor, con el qué hacer en cada nivel. Solo lectura. */
class AlertsActivity : BaseActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var summary: TextView
    private lateinit var factors: LinearLayout
    private val observer: (NodeState) -> Unit = { render(it) }
    private val clock = object : Runnable {
        override fun run() { render(agro.node.state); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        page("03 / Alertas", "Alertas y riesgos", "Cada factor se evalúa por separado y solo con datos frescos del nodo.")
        summary = label("Sin lectura", 15, muted, true)
        gap(14)
        factors = card()
        button("Volver al panel", secondary = true) { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    private fun colorFor(nivel: Nivel?) = when (nivel) {
        null -> muted; Nivel.NORMAL -> green; Nivel.ALERTA -> amber; Nivel.CRITICO -> red
    }

    private fun row(name: String, nivel: Nivel?, valor: String, consejo: String) {
        label(name, 11, muted, true, factors)
        val headline = if (nivel == null) "● Sin datos actuales · $valor" else "● ${nivel.label} · $valor"
        label(headline, 16, colorFor(nivel), true, factors)
        label(consejo, 13, muted, parent = factors)
        gap(14, factors)
    }

    private fun render(s: NodeState) {
        if (!::factors.isInitialized) return
        val fresh = s.connected && s.receivedAt > 0 && SystemClock.elapsedRealtime() - s.receivedAt <= 7000
        val t = s.telemetry
        factors.removeAllViews()
        if (!fresh || t == null) {
            summary.text = "● Sin datos actuales. La app no afirma si el huerto está bien o mal."
            summary.setTextColor(amber)
            row("TEMPERATURA", null, "—", "Se reanuda al llegar una lectura fresca (ventana de 7 segundos).")
            row("HUMEDAD AMBIENTE", null, "—", "Se reanuda al llegar una lectura fresca.")
            row("HUMEDAD DE SUELO", null, "—", "Sensor de suelo no disponible en este nodo.")
            return
        }
        val tempLevel = if (t.risk == Risk.SENSOR_ERROR) null else Protocol.temperatureLevel(t.risk)
        val humLevel = t.humidity?.let { Protocol.humidityLevel(it) }
        val niveles = listOfNotNull(tempLevel, humLevel)
        summary.text = when {
            Nivel.CRITICO in niveles -> "⚠ Riesgo crítico vigente: revisa los factores en rojo y actúa."
            Nivel.ALERTA in niveles -> "Alerta preventiva vigente: mantén vigilancia."
            else -> "Sin riesgos activos con los datos actuales."
        }
        summary.setTextColor(when {
            Nivel.CRITICO in niveles -> red; Nivel.ALERTA in niveles -> amber; else -> green
        })
        val temperature = t.temperature
        row("TEMPERATURA", tempLevel,
            temperature?.let { String.format(Locale.forLanguageTag("es-CL"), "%.1f °C", it) } ?: "—",
            when (tempLevel) {
                Nivel.NORMAL -> "Sobre 10 °C. Sin acción requerida."
                Nivel.ALERTA -> "Prehelada (5 a 10 °C): vigilar durante la noche y preparar cobertura."
                Nivel.CRITICO -> "Helada (5 °C o menos): activar riego de protección y cubrir el cultivo."
                null -> "El nodo reporta el sensor en error: revisar el DHT11 y el cableado."
            })
        val humidity = t.humidity
        row("HUMEDAD AMBIENTE", humLevel,
            humidity?.let { "${it.toInt()} %" } ?: "—",
            when (humLevel) {
                Nivel.NORMAL -> "Entre 40 y 75 %. Rango saludable para el cultivo."
                Nivel.ALERTA -> if (humidity != null && humidity < 35.0)
                    "Aire muy seco (menos de 35 %): conviene regar con más frecuencia."
                else "Humedad alta (más de 75 %): vigilar aparición de moho."
                Nivel.CRITICO -> "85 % o más: riesgo de moho y enfermedades. Ventilar y revisar el cultivo."
                null -> "Sin lectura de humedad."
            })
        row("HUMEDAD DE SUELO", null, "—",
            "Sensor de suelo no disponible en este nodo: la app no inventa valores. Queda como ampliación en docs/HUECOS_FUNCIONALES.md.")
    }

    override fun onStart() { super.onStart(); agro.node.observe(observer); handler.post(clock) }
    override fun onStop() { agro.node.remove(observer); handler.removeCallbacks(clock); super.onStop() }
}
