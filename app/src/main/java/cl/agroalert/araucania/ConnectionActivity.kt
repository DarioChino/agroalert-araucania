package cl.agroalert.araucania

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ConnectionActivity : BaseActivity() {
    private lateinit var status: TextView
    private lateinit var connect: MaterialButton
    private lateinit var key: TextInputEditText
    private var navigating = false
    private val observer: (NodeState) -> Unit = { state ->
        status.text = state.message
        connect.isEnabled = !state.connecting
        if (state.connected && !navigating) {
            navigating = true
            key.text?.clear()
            startActivity(Intent(this, DashboardActivity::class.java))
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSession()) return
        val prefs = getSharedPreferences("connection", MODE_PRIVATE)
        page("01 / Conexión · ${agro.role!!.label}", "Enlaza tu nodo.", "Conecta el celular a la misma red Wi-Fi que el nodo AgroAlert o el simulador.")
        val info = card()
        label("Wi-Fi · red local", 18, ink, true, info)
        gap(8, info)
        label("Cada celular abre su propia conexión. Ambos reciben el mismo estado del sistema.", 14, muted, parent = info)
        val host = field("Dirección IP del nodo", initial = prefs.getString("host", "") ?: "")
        val port = field("Puerto TCP", initial = prefs.getInt("port", 5000).toString(), numeric = true)
        key = field("Clave del nodo para ${agro.role!!.label}", password = true)
        label("Esta clave la entrega el nodo o el simulador. Es diferente de tu contraseña de inicio de sesión.", 13, muted)
        gap()
        status = label("Listo para conectar", 14, green)
        gap()
        connect = button("Conectar por Wi-Fi") {
            val address = host.text.toString().trim()
            val p = port.text.toString().toIntOrNull()
            val token = key.text.toString().trim()
            val validIp = address.split('.').let { parts -> parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) && (it.toIntOrNull() ?: -1) in 0..255 } }
            if (!validIp || p == null || p !in 1..65535) {
                status.text = "Escribe una IPv4 válida (ej. 192.168.1.25) y un puerto entre 1 y 65535."
            } else if (!token.matches(Regex("[A-Za-z0-9_-]{16,128}"))) {
                status.text = "La clave del nodo debe tener entre 16 y 128 letras, números, guiones o guiones bajos."
            } else {
                prefs.edit().putString("host", address).putInt("port", p).apply()
                agro.node.connect(address, p, token, agro.role!!)
            }
        }
        button("Cerrar sesión", secondary = true) { logout() }
        gap(16)
        label("¿Estás probando sin Arduino?\nInicia simulator/server.py en el computador y usa la IP y la clave que muestra la terminal.", 13, muted)
    }
    override fun onStart() {
        super.onStart()
        if (::status.isInitialized) { navigating = false; agro.node.observe(observer) }
    }
    override fun onStop() { agro.node.remove(observer); super.onStop() }
}
