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
}

data class AttemptState(val failures: Int = 0, val blockedUntil: Long = 0) {
    fun remaining(now: Long): Long = (blockedUntil - now).coerceAtLeast(0)
    fun failed(now: Long): AttemptState {
        val count = if (blockedUntil > 0 && now >= blockedUntil) 1 else failures + 1
        return if (count >= 3) AttemptState(0, now + 60_000) else AttemptState(count, 0)
    }
}

class Credentials(context: Context) {
    private val prefs = context.getSharedPreferences("local_credentials", Context.MODE_PRIVATE)
    val configured get() = prefs.contains("control") && prefs.contains("monitor")
    fun setup(control: String, monitor: String) {
        check(!configured)
        require(control.length in 8..128 && monitor.length in 8..128 && control != monitor)
        prefs.edit().putString("control", PasswordHash.create(control))
            .putString("monitor", PasswordHash.create(monitor)).commit()
    }
    private fun state() = AttemptState(prefs.getInt("failures", 0), prefs.getLong("blocked", 0))
    fun remaining() = state().remaining(System.currentTimeMillis())
    @Synchronized fun login(username: String, password: String): Role? {
        val now = System.currentTimeMillis()
        if (state().remaining(now) > 0) return null
        val role = when (username.trim().lowercase()) {
            "control" -> Role.CONTROL
            "monitor" -> Role.MONITOR
            else -> null
        }
        val stored = prefs.getString(role?.wire?.lowercase() ?: "", null)
        val valid = stored != null && password.length <= 128 && PasswordHash.verify(password, stored)
        val next = if (valid) AttemptState() else state().failed(now)
        prefs.edit().putInt("failures", next.failures).putLong("blocked", next.blockedUntil).commit()
        return if (valid) role else null
    }
}
