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
        if (agro.credentials.configured) loginPage() else setupPage()
    }
    override fun onStart() { super.onStart(); handler.post(timer) }
    override fun onStop() { handler.removeCallbacks(timer); super.onStop() }

    private fun setupPage() {
        page("Primer inicio", "Cuidemos tu cultivo.", "Configura el acceso local de este celular. El mismo APK sirve para Control y Monitor.")
        val info = card()
        label("Dos roles, un mismo huerto", 18, ink, true, info)
        gap(8, info)
        label("Control · consulta y envía órdenes al riego.\nMonitor · consulta las lecturas del nodo.", 14, muted, parent = info)
        val control = field("Nueva contraseña de control", password = true)
        val confirmControl = field("Repetir contraseña de control", password = true)
        val monitor = field("Nueva contraseña de monitor", password = true)
        val confirmMonitor = field("Repetir contraseña de monitor", password = true)
        label("Usa contraseñas distintas de al menos 8 caracteres. Los usuarios serán control y monitor.", 13, muted)
        gap()
        feedback = label("", 14, red)
        submit = button("Crear accesos") {
            val c = control.text.toString()
            val m = monitor.text.toString()
            if (c.length < 8 || m.length < 8 || c == m) {
                feedback.text = "Usa al menos 8 caracteres y contraseñas distintas para cada rol."
            } else if (c != confirmControl.text.toString() || m != confirmMonitor.text.toString()) {
                feedback.text = "Las contraseñas y sus confirmaciones deben coincidir."
            } else {
                busy = true; submit.isEnabled = false
                Thread {
                    val result = runCatching { agro.credentials.setup(c, m) }
                    runOnUiThread {
                        if (!isDestroyed) {
                            busy = false
                            if (result.isSuccess) loginPage()
                            else { feedback.text = "No se pudieron guardar los accesos."; submit.isEnabled = true }
                        }
                    }
                }.start()
            }
        }
    }

    private fun loginPage() {
        page("Bienvenido", "Tu huerto, a la vista.", "Monitoreo agroclimático y control de riego para La Araucanía.")
        val intro = card()
        label("PREVENCIÓN DE HELADAS", 11, green, true, intro)
        gap(8, intro)
        label("Conecta. Observa. Actúa.", 22, ink, true, intro)
        gap(8, intro)
        label("Temperatura, humedad y estado del riego, desde tu red Wi-Fi.", 15, muted, parent = intro)
        val username = field("Usuario: control o monitor")
        val password = field("Contraseña", password = true)
        feedback = label("3 intentos fallidos bloquean el ingreso durante 60 segundos.", 13, muted)
        gap()
        submit = button("Iniciar sesión") {
            if (busy) return@button
            busy = true; submit.isEnabled = false
            val u = username.text.toString()
            val p = password.text.toString()
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
        label("PROYECTO TI3042 · INACAP\nDarío Candia & Sebastián Torres", 12, muted)
    }
}
