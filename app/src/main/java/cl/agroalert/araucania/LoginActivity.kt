package cl.agroalert.araucania

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : BaseActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var feedback: TextView
    private lateinit var submit: MaterialButton
    private lateinit var password: TextInputEditText
    private lateinit var userName: TextInputEditText
    private lateinit var newUser: TextInputEditText
    private lateinit var newPass: TextInputEditText
    private lateinit var newEmail: TextInputEditText
    private lateinit var accountFeedback: TextView
    private lateinit var chgUser: TextInputEditText
    private lateinit var chgAuth: TextInputEditText
    private lateinit var chgNew: TextInputEditText
    private lateinit var resetFeedback: TextView
    private lateinit var recoverEmail: TextInputEditText
    private lateinit var recoverFeedback: TextView
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
        gap(4, intro)
        label("Temperatura, humedad y riego, desde tu bolsillo.", 15, muted, parent = intro)

        label("ACCESO ÚNICO", 11, muted, true)
        userName = field("Usuario o correo (admin: admin)")
        password = field("Contraseña", password = true)
        feedback = label("3 intentos fallidos bloquean el ingreso; la espera crece en cada ronda.", 13, muted)
        gap()
        submit = button("Entrar") {
            if (busy) return@button
            busy = true; submit.isEnabled = false
            val user = userName.text?.toString()?.trim().orEmpty().ifEmpty { "admin" }
            val p = password.text?.toString() ?: ""
            Thread {
                val ok = agro.credentials.login(user, p)
                runOnUiThread {
                    if (!isDestroyed) {
                        busy = false
                        password.text?.clear()
                        if (ok) {
                            if (agro.credentials.passwordExpired(user)) {
                                resetFeedback.setTextColor(red)
                                resetFeedback.text = "La clave debe cambiarse antes de continuar (vigencia o política de claves)."
                            } else {
                                agro.loggedIn = true
                                startActivity(Intent(this, ConnectionActivity::class.java))
                                finish()
                            }
                        } else {
                            feedback.setTextColor(red)
                            feedback.text = "Contraseña incorrecta para ${LocalAccounts.normalize(user)}."
                            submit.isEnabled = agro.credentials.remaining() == 0L
                        }
                    }
                }
            }.start()
        }
        gap(18)
        label("CLAVE INICIAL · admin: admin1", 13, muted)
        label(if (BuildConfig.CLOUD_ENABLED)
            "Las cuentas, claves y la bitácora se guardan en Firebase."
        else "Firebase aún no está configurado: por ahora las claves quedan en este teléfono como hash PBKDF2.",
            12, muted)
        gap(18)
        label("TU PROPIA CUENTA · ACCESO COMPLETO", 11, muted, true)
        newUser = field("Usuario nuevo")
        newPass = field("Contraseña nueva", password = true)
        newEmail = field("Correo real (para recuperar la clave)", email = true)
        accountFeedback = label("8 caracteres con mayúscula, minúscula y número, sin patrones comunes ni tu usuario. El correo debe ser real: allí llegará el enlace de recuperación.", 13, muted)
        button("Crear cuenta") {
            if (busy) return@button
            busy = true
            accountFeedback.setTextColor(muted)
            accountFeedback.text = "Creando cuenta…"
            val user = newUser.text?.toString() ?: ""
            val pass = newPass.text?.toString() ?: ""
            val email = newEmail.text?.toString() ?: ""
            Thread {
                val problem = agro.credentials.register(user, pass, email)
                runOnUiThread {
                    busy = false
                    if (!isDestroyed) {
                        if (problem == null) {
                            newUser.text?.clear(); newPass.text?.clear(); newEmail.text?.clear()
                            accountFeedback.setTextColor(green)
                            accountFeedback.text = "Cuenta creada para ${LocalAccounts.normalize(user)}. Entra usando ${email.trim()}."
                        } else {
                            accountFeedback.setTextColor(red)
                            accountFeedback.text = problem
                        }
                    }
                }
            }.start()
        }
        gap(18)
        label("CAMBIAR CONTRASEÑA", 11, muted, true)
        chgUser = field("Usuario a cambiar")
        chgAuth = field("Clave actual", password = true)
        chgNew = field("Clave nueva", password = true)
        resetFeedback = label("El dueño cambia su clave con su clave actual. Máximo 3 claves previas, vigencia de 90 días.", 13, muted)
        button("Guardar nueva clave", secondary = true) {
            if (busy) return@button
            busy = true
            resetFeedback.setTextColor(muted)
            resetFeedback.text = "Guardando…"
            val user = chgUser.text?.toString() ?: ""
            val auth = chgAuth.text?.toString() ?: ""
            val next = chgNew.text?.toString() ?: ""
            Thread {
                val problem = agro.credentials.changePassword(user, auth, next)
                runOnUiThread {
                    busy = false
                    if (!isDestroyed) {
                        if (problem == null) {
                            chgUser.text?.clear(); chgAuth.text?.clear(); chgNew.text?.clear()
                            resetFeedback.setTextColor(green)
                            resetFeedback.text = "Clave actualizada para ${LocalAccounts.normalize(user)}."
                        } else {
                            resetFeedback.setTextColor(red)
                            resetFeedback.text = problem
                        }
                    }
                }
            }.start()
        }
        gap(18)
        label("RECUPERAR CONTRASEÑA", 11, muted, true)
        recoverEmail = field("Correo registrado al crear la cuenta", email = true)
        recoverFeedback = label("Firebase envía un enlace para elegir una clave nueva. Las cuentas antiguas sin correo no pueden recuperarse por aquí.", 13, muted)
        button("Enviar enlace de recuperación", secondary = true) {
            if (busy) return@button
            busy = true
            recoverFeedback.setTextColor(muted)
            recoverFeedback.text = "Enviando…"
            val email = recoverEmail.text?.toString() ?: ""
            Thread {
                val problem = agro.credentials.requestReset(email)
                runOnUiThread {
                    busy = false
                    if (!isDestroyed) {
                        if (problem == null) {
                            recoverEmail.text?.clear()
                            recoverFeedback.setTextColor(green)
                            recoverFeedback.text = "Revisa tu correo y sigue el enlace de Firebase para elegir la clave nueva."
                        } else {
                            recoverFeedback.setTextColor(red)
                            recoverFeedback.text = problem
                        }
                    }
                }
            }.start()
        }
        gap(14)
        button(if (agro.darkMode) "Modo claro" else "Modo oscuro", secondary = true) { toggleDarkMode() }
        gap(10)
        label("PROYECTO TI3042 · INACAP\nDarío Candia & Sebastián Torres", 12, muted)
    }
}
