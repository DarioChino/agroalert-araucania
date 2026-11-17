package cl.agroalert.araucania

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : BaseActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var feedback: TextView
    private lateinit var submit: MaterialButton
    private lateinit var password: TextInputEditText
    private lateinit var cardControl: LinearLayout
    private lateinit var cardMonitor: LinearLayout
    private lateinit var checkControl: TextView
    private lateinit var checkMonitor: TextView
    private var busy = false
    private var selected: Role = Role.CONTROL
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

    private fun roleCard(title: String, subtitle: String, role: Role): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { selected = role; paintCards() }
        }
        box.addView(TextView(this).apply {
            text = title; textSize = 17f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2))
        box.addView(TextView(this).apply {
            text = subtitle; textSize = 13f; setTextColor(muted); setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val check = TextView(this).apply {
            textSize = 12f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.END
        }
        box.addView(check, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        if (role == Role.CONTROL) checkControl = check else checkMonitor = check
        return box
    }

    private fun paintCards() {
        listOf(cardControl, cardMonitor).zip(listOf(Role.CONTROL, Role.MONITOR)).forEach { (box, role) ->
            val picked = role == selected
            box.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (dark) Color.rgb(25, 35, 30) else Color.WHITE)
                setStroke(dp(if (picked) 2 else 1), if (picked) green else if (dark) Color.rgb(46, 60, 53) else Color.rgb(222, 231, 223))
            }
            val check = if (role == Role.CONTROL) checkControl else checkMonitor
            check.text = if (picked) "✓ Seleccionado" else "Toca para elegir"
            check.setTextColor(if (picked) green else muted)
        }
        submit.text = if (selected == Role.CONTROL) "Entrar como Control" else "Entrar como Monitor"
    }

    private fun loginPage() {
        page("Bienvenido", "Tu huerto, a la vista.", "Monitoreo agroclimático y control de riego para La Araucanía.")
        val intro = card()
        label("PREVENCIÓN DE HELADAS", 11, green, true, intro)
        gap(8, intro)
        label("Conecta. Observa. Actúa.", 22, ink, true, intro)
        gap(4, intro)
        label("Temperatura, humedad y riego, desde tu bolsillo.", 15, muted, parent = intro)

        label("ELIGE TU PERFIL", 11, muted, true)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(14) })
        cardControl = roleCard("Control", "Enciende, apaga y vigila el riego", Role.CONTROL)
        cardMonitor = roleCard("Monitor", "Observa los datos, sin controles", Role.MONITOR)
        row.addView(cardControl, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
        row.addView(cardMonitor, LinearLayout.LayoutParams(0, -2, 1f))

        password = field("Contraseña", password = true)
        feedback = label("3 intentos fallidos bloquean el ingreso durante 60 segundos.", 13, muted)
        gap()
        submit = button("Entrar como Control") {
            if (busy) return@button
            busy = true; submit.isEnabled = false
            val user = if (selected == Role.CONTROL) "admin" else "monitor"
            val p = password.text?.toString() ?: ""
            Thread {
                val role = agro.credentials.login(user, p)
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
                            feedback.text = "Contraseña incorrecta para $user."
                            submit.isEnabled = agro.credentials.remaining() == 0L
                        }
                    }
                }
            }.start()
        }
        paintCards()
        gap(18)
        label("CLAVES INICIALES · admin: admin1 · monitor: monitor1", 13, muted)
        gap(14)
        button(if (agro.darkMode) "Modo claro" else "Modo oscuro", secondary = true) { toggleDarkMode() }
        gap(10)
        label("PROYECTO TI3042 · INACAP\nDarío Candia & Sebastián Torres", 12, muted)
    }
}
