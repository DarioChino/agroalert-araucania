package cl.agroalert.araucania

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHash {
    fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun create(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val enc = Base64.getEncoder()
        return "${enc.encodeToString(salt)}:${enc.encodeToString(derive(password, salt))}"
    }
    fun verify(password: String, stored: String): Boolean = runCatching {
        val (salt, hash) = stored.split(':')
        MessageDigest.isEqual(derive(password, Base64.getDecoder().decode(salt)), Base64.getDecoder().decode(hash))
    }.getOrDefault(false)
    fun matchesAny(password: String, hashes: List<String>): Boolean = hashes.any { verify(password, it) }
}

data class AttemptState(val failures: Int = 0, val blockedUntil: Long = 0) {
    fun remaining(now: Long): Long = (blockedUntil - now).coerceAtLeast(0)
    fun failed(now: Long): AttemptState {
        val count = if (blockedUntil > 0 && now >= blockedUntil) 1 else failures + 1
        return if (count >= 3) AttemptState(0, now + 60_000) else AttemptState(count, 0)
    }
}

class Credentials(context: Context) {
    companion object { const val PASSWORD_PERIOD_MS = 90L * 24 * 60 * 60 * 1000 }

    private val prefs = context.getSharedPreferences("local_credentials", Context.MODE_PRIVATE)
    val configured get() = prefs.contains("control") && prefs.contains("monitor")

    private fun historyKey(key: String) = "hist:$key"
    private fun changedKey(key: String) = "changed:$key"

    init {
        // Cuentas iniciales del equipo, una por rol: admin/admin1 (Control) y
        // monitor/monitor1 (Monitor). Se siembran solo si esta instalación no
        // tiene accesos y quedan guardadas como hash PBKDF2, nunca en texto plano.
        if (!configured) {
            val now = System.currentTimeMillis()
            prefs.edit()
                .putString("control", PasswordHash.create("admin1"))
                .putString("monitor", PasswordHash.create("monitor1"))
                .putLong(changedKey("control"), now)
                .putLong(changedKey("monitor"), now)
                .commit()
        }
    }

    private fun state() = AttemptState(prefs.getInt("failures", 0), prefs.getLong("blocked", 0))
    fun remaining() = state().remaining(System.currentTimeMillis())
    @Synchronized fun login(username: String, password: String): Role? {
        val now = System.currentTimeMillis()
        if (state().remaining(now) > 0) return null
        val name = LocalAccounts.normalize(username)
        val role = Users.resolve(name)
        val stored = prefs.getString(role?.wire?.lowercase() ?: "acct:$name", null)
        val custom = if (role == null) stored?.let { LocalAccounts.decode(it) } else null
        val valid = stored != null && password.length <= 128 && when {
            custom != null -> PasswordHash.verify(password, custom.second)
            else -> PasswordHash.verify(password, stored)
        }
        val next = if (valid) AttemptState() else state().failed(now)
        prefs.edit().putInt("failures", next.failures).putLong("blocked", next.blockedUntil).commit()
        return if (valid) role ?: custom?.first else null
    }

    /** Crea una cuenta propia en este teléfono. Devuelve null si quedó creada, o el motivo del rechazo. */
    fun register(username: String, password: String, role: Role): String? {
        LocalAccounts.validate(username, password)?.let { return it }
        val key = "acct:${LocalAccounts.normalize(username)}"
        if (prefs.contains(key)) return "Ese usuario ya existe en este teléfono."
        prefs.edit()
            .putString(key, "${role.wire}:${PasswordHash.create(password)}")
            .putLong(changedKey(key), System.currentTimeMillis())
            .commit()
        return null
    }

    /** Instalaciones anteriores a esta política arrancan el conteo ahora, no obligan a cambiar. */
    fun passwordExpired(username: String): Boolean {
        val key = Users.resolve(username)?.wire?.lowercase() ?: "acct:${LocalAccounts.normalize(username)}"
        val changed = prefs.getLong(changedKey(key), 0L)
        if (changed == 0L) {
            prefs.edit().putLong(changedKey(key), System.currentTimeMillis()).apply()
            return false
        }
        return System.currentTimeMillis() - changed >= PASSWORD_PERIOD_MS
    }

    /**
     * Cambia o restablece la clave de cualquier cuenta. La autorización es la clave
     * vigente de la propia cuenta o la de un usuario Control (vía de recuperación).
     * Devuelve null si el cambio quedó aplicado, o el motivo del rechazo.
     */
    fun changePassword(target: String, auth: String, newPassword: String): String? {
        val name = LocalAccounts.normalize(target)
        if (name.isEmpty()) return "Indica el usuario a cambiar."
        LocalAccounts.validatePassword(newPassword, name)?.let { return it }
        if (auth.length > 128) return "La clave de autorización es demasiado larga."
        val role = Users.resolve(name)
        val key = role?.wire?.lowercase() ?: "acct:$name"
        val record = prefs.getString(key, null) ?: return "Ese usuario no existe en este teléfono."
        val currentHash = (if (role == null) LocalAccounts.decode(record)?.second else record)
            ?: return "El registro de ese usuario está corrupto."
        if (!PasswordHash.verify(auth, currentHash) &&
            !PasswordHash.verify(auth, prefs.getString("control", "") ?: ""))
            return "La clave de autorización no coincide: usa la clave actual de esa cuenta o la del usuario admin."
        val history = prefs.getString(historyKey(key), "")!!.lines().filter { it.contains(':') }
        if (PasswordHash.matchesAny(newPassword, listOf(currentHash) + history))
            return "La clave no puede repetir ninguna de las últimas 3."
        val newRecord = if (role != null) PasswordHash.create(newPassword)
            else "${LocalAccounts.decode(record)!!.first.wire}:${PasswordHash.create(newPassword)}"
        prefs.edit()
            .putString(key, newRecord)
            .putString(historyKey(key), (history + currentHash).takeLast(3).joinToString("\n"))
            .putLong(changedKey(key), System.currentTimeMillis())
            .commit()
        return null
    }
}
