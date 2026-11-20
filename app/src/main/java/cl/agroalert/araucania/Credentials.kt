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
    val configured get() = BuildConfig.CLOUD_ENABLED || prefs.contains("admin")

    private fun historyKey(key: String) = "hist:$key"
    private fun changedKey(key: String) = "changed:$key"
    private fun keyFor(username: String): String =
        if (LocalAccounts.normalize(username) == "admin") "admin"
        else "acct:${LocalAccounts.normalize(username)}"

    init {
        if (!BuildConfig.CLOUD_ENABLED && !configured) {
            // Migración: la instalación 1.4 guardaba admin bajo "control" y además "monitor".
            val legacy = prefs.getString("control", null)
            val now = prefs.getLong(changedKey("control"), System.currentTimeMillis())
            prefs.edit()
                .putString("admin", legacy ?: PasswordHash.create("admin1"))
                .putString(historyKey("admin"), prefs.getString(historyKey("control"), "") ?: "")
                .putLong(changedKey("admin"), now)
                .remove("control").remove("monitor")
                .remove(changedKey("control")).remove(changedKey("monitor"))
                .remove(historyKey("control")).remove(historyKey("monitor"))
                .commit()
        }
    }

    private fun state() = AttemptState(prefs.getInt("failures", 0), prefs.getLong("blocked", 0))
    fun remaining() = state().remaining(System.currentTimeMillis())

    @Synchronized fun login(username: String, password: String): Boolean {
        val now = System.currentTimeMillis()
        if (state().remaining(now) > 0) return false
        val name = LocalAccounts.normalize(username)
        val valid = if (BuildConfig.CLOUD_ENABLED) {
            CloudStore.signIn(name, password)
        } else {
            val record = prefs.getString(keyFor(name), null) ?: ""
            val stored = if (keyFor(name) == "admin") record else LocalAccounts.decode(record)
            stored != null && password.length <= 128 && PasswordHash.verify(password, stored)
        }
        val next = if (valid) AttemptState() else state().failed(now)
        prefs.edit().putInt("failures", next.failures).putLong("blocked", next.blockedUntil).commit()
        return valid
    }

    /** Crea una cuenta. Devuelve null si quedó creada, o el motivo del rechazo. */
    fun register(username: String, password: String): String? {
        LocalAccounts.validate(username, password)?.let { return it }
        val name = LocalAccounts.normalize(username)
        if (BuildConfig.CLOUD_ENABLED) return CloudStore.createAccount(name, password)
        val key = "acct:$name"
        if (prefs.contains(key)) return "Ese usuario ya existe en este teléfono."
        prefs.edit().putString(key, PasswordHash.create(password))
            .putLong(changedKey(key), System.currentTimeMillis()).commit()
        return null
    }

    /** Instalaciones anteriores a esta política arrancan el conteo ahora, no obligan a cambiar. */
    fun passwordExpired(username: String): Boolean {
        val name = LocalAccounts.normalize(username)
        if (BuildConfig.CLOUD_ENABLED) return CloudStore.passwordExpired(name)
        val key = keyFor(name)
        val changed = prefs.getLong(changedKey(key), 0L)
        if (changed == 0L) {
            prefs.edit().putLong(changedKey(key), System.currentTimeMillis()).apply()
            return false
        }
        return System.currentTimeMillis() - changed >= PASSWORD_PERIOD_MS
    }

    /**
     * Cambia la clave de la propia cuenta. La autorización es la clave vigente.
     * Devuelve null si el cambio quedó aplicado, o el motivo del rechazo.
     */
    fun changePassword(target: String, auth: String, newPassword: String): String? {
        val name = LocalAccounts.normalize(target)
        if (name.isEmpty()) return "Indica el usuario a cambiar."
        LocalAccounts.validatePassword(newPassword, name)?.let { return it }
        if (auth.length > 128) return "La clave de autorización es demasiado larga."
        if (BuildConfig.CLOUD_ENABLED) return CloudStore.changePassword(name, auth, newPassword)

        val key = keyFor(name)
        val record = prefs.getString(key, null) ?: return "Ese usuario no existe en este teléfono."
        val currentHash = if (key == "admin") record else LocalAccounts.decode(record)
        if (currentHash == null) return "El registro de ese usuario está corrupto."
        if (!PasswordHash.verify(auth, currentHash)) return "La clave de autorización no coincide."
        val history = prefs.getString(historyKey(key), "")!!.lines().filter { it.contains(':') }
        if (PasswordHash.matchesAny(newPassword, listOf(currentHash) + history))
            return "La clave no puede repetir ninguna de las últimas 3."
        prefs.edit()
            .putString(key, PasswordHash.create(newPassword))
            .putString(historyKey(key), (history + currentHash).takeLast(3).joinToString("\n"))
            .putLong(changedKey(key), System.currentTimeMillis())
            .commit()
        return null
    }
}
