package cl.agroalert.araucania

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.google.android.material.button.MaterialButton

class LoginActivity : BaseActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var feedback: TextView
    private lateinit var submit: MaterialButton
    private var busy = false
    private val timer = object : Runnable {
        override fun run() {
            if (::submit.isInitialized && agro.credentials.configured) {
                val remaining = agro.credentials.remaining()
                submit.isEnabled = !busy && remaining == 0L
                if (remaining > 0) feedback.text = "Ingreso bloqueado. Intenta nuevamente en ${(remaining + 999) / 1000} s."
                else if (feedback.text.startsWith("Ingreso bloqueado")) feedback.text = "Ya puedes volver a iniciar sesión."
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loginPage()
    }
    override fun onStart() { super.onStart(); handler.post(timer) }
    override fun onStop() { handler.removeCallbacks(timer); super.onStop() }

    private fun loginPage() {
        page("Bienvenido", "Tu huerto, a la vista.", "Monitoreo agroclimático y control de riego para La Araucanía.")
        val intro = card()
        label("PREVENCIÓN DE HELADAS", 11, green, true, intro)
        gap(8, intro)
        label("Conecta. Observa. Actúa.", 22, ink, true, intro)
        gap(8, intro)
        label("Temperatura, humedad y estado del riego, desde tu red Wi-Fi.", 15, muted, parent = intro)
        val username = field("Usuario: admin o monitor")
        val password = field("Contraseña", password = true)
        feedback = label("3 intentos fallidos bloquean el ingreso durante 60 segundos.", 13, muted)
        gap()
        submit = button("Iniciar sesión") {
            if (busy) return@button
            busy = true; submit.isEnabled = false
            val u = username.text?.toString() ?: ""
            val p = password.text?.toString() ?: ""
            Thread {
                val role = agro.credentials.login(u, p)
                runOnUiThread {
                    if (!isDestroyed) {
                        busy = false
                        password.text?.clear()
                        if (role != null) {
                            agro.role = role
                            startActivity(Intent(this, ConnectionActivity::class.java))
                            finish()
                        } else {
                            feedback.setTextColor(red)
                            feedback.text = "Usuario o contraseña incorrectos."
                            submit.isEnabled = agro.credentials.remaining() == 0L
                        }
                    }
                }
            }.start()
        }
        gap(22)
        label("CUENTAS DE ESTE EQUIPO", 11, muted, true)
        gap(4)
        label("admin · Control del riego · clave inicial admin1\nmonitor · Solo lectura · clave inicial monitor1", 13, muted)
        gap(14)
        button(if (agro.darkMode) "Modo claro" else "Modo oscuro", secondary = true) { toggleDarkMode() }
        gap(10)
        label("PROYECTO TI3042 · INACAP\nDarío Candia & Sebastián Torres", 12, muted)
    }
}
