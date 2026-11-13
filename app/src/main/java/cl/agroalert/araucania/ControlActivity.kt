package cl.agroalert.araucania

import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView
import com.google.android.material.button.MaterialButton

class ControlActivity : BaseActivity() {
    private lateinit var status: TextView
    private lateinit var relay: TextView
    private lateinit var source: TextView
    private val commands = mutableListOf<MaterialButton>()
    private val observer: (NodeState) -> Unit = { s ->
        val fresh = s.connected && s.receivedAt > 0 && SystemClock.elapsedRealtime() - s.receivedAt <= 7000
        commands.forEach { it.isEnabled = fresh && s.pending == null && s.telemetry?.risk != Risk.SENSOR_ERROR }
        status.text = when {
            !fresh -> "Controles bloqueados · ${s.message}"
            s.telemetry?.risk == Risk.SENSOR_ERROR -> "Controles bloqueados · revisa el sensor del nodo."
            s.confirmation != null -> s.confirmation
            else -> s.message
        }
        source.text = if (s.simulated) "SIMULADOR · LAS ÓRDENES NO ACTIVAN HARDWARE" else "NODO WI-FI · CONTROL DEL RELÉ"
        relay.text = s.telemetry?.let {
            "Relé ${if (it.relay) "encendido" else "apagado"}\nModo ${if (it.automatic) "automático" else "manual"}${if (!fresh) " · dato antiguo" else ""}"
        } ?: "Sin lectura del nodo"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        if (agro.role != Role.CONTROL) { finish(); return }
        page("03 / Control", "Gestiona el riego.", "Las órdenes se confirman desde el nodo. El panel muestra el estado que realmente reporta el relé.")
        source = label("", 12, amber, true)
        gap()
        val summary = card()
        relay = label("", 24, ink, true, summary)
        gap(8, summary)
        status = label("", 14, muted, parent = summary)
        commands += button("Encender · modo manual") { agro.node.send('1') }
        commands += button("Apagar · modo manual", secondary = true) { agro.node.send('0') }
        commands += button("Volver al modo automático", secondary = true) { agro.node.send('A') }
        gap()
        val explanation = card()
        label("¿Qué hace cada modo?", 17, ink, true, explanation)
        gap(8, explanation)
        label("Manual: el nodo recibe tu orden de encender o apagar. El protocolo Wi-Fi propone volver a automático después de 30 segundos o al perder el controlador.\n\nAutomático: el nodo decide según la temperatura, incluso sin la app conectada.", 14, muted, parent = explanation)
        button("Volver al panel", secondary = true) { finish() }
    }
    override fun onStart() { super.onStart(); if (::status.isInitialized) agro.node.observe(observer) }
    override fun onStop() { agro.node.remove(observer); super.onStop() }
}
