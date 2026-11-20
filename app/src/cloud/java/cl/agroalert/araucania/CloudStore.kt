package cl.agroalert.araucania

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Capa Firebase. Se compila solo cuando app/google-services.json existe.
 * Las contraseñas las gestiona Firebase Auth; el hash propio y el historial de
 * 3 claves viven en Firestore. La caché offline está desactivada: nada queda en
 * el teléfono salvo el token de sesión que Firebase Auth administra.
 */
object CloudStore {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private const val SUFFIX = "@agroalert.local"

    init {
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(false)
            .build()
    }

    private fun emailFor(username: String) = "${LocalAccounts.normalize(username)}$SUFFIX"
    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun currentUid() = auth.currentUser?.uid
    private fun reason(e: Exception) = e.message?.takeIf { it.isNotBlank() } ?: "revisa tu conexión"

    fun signIn(username: String, password: String): Boolean = try {
        Tasks.await(auth.signInWithEmailAndPassword(emailFor(username), password))
        true
    } catch (_: Exception) {
        false
    }

    fun createAccount(username: String, password: String): String? {
        LocalAccounts.validate(username, password)?.let { return it }
        val name = LocalAccounts.normalize(username)
        return try {
            val result = Tasks.await(auth.createUserWithEmailAndPassword(emailFor(name), password))
            val uid = result.user?.uid ?: return "Firebase no devolvió la cuenta creada."
            Tasks.await(userDoc(uid).set(mapOf<String, Any>(
                "username" to name,
                "currentHash" to PasswordHash.create(password),
                "changedAt" to System.currentTimeMillis(),
                "history" to emptyList<String>()
            )))
            null
        } catch (e: Exception) {
            "No se pudo crear la cuenta en la nube: ${reason(e)}"
        }
    }

    fun changePassword(username: String, current: String, next: String): String? {
        val user = auth.currentUser ?: return "Inicia sesión para cambiar la clave."
        if (user.email != emailFor(username)) return "La clave actual no corresponde a esta cuenta."
        LocalAccounts.validatePassword(next, username)?.let { return it }
        return try {
            val snap = Tasks.await(userDoc(user.uid).get())
            val currentHash = snap.getString("currentHash")
                ?: return "No hay registro de clave en la nube."
            val history = (snap.get("history") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (!PasswordHash.verify(current, currentHash)) return "La clave actual no coincide."
            if (PasswordHash.matchesAny(next, listOf(currentHash) + history))
                return "La clave no puede repetir ninguna de las últimas 3."

            Tasks.await(user.reauthenticate(EmailAuthProvider.getCredential(emailFor(username), current)))
            Tasks.await(user.updatePassword(next))
            Tasks.await(userDoc(user.uid).update(mapOf<String, Any>(
                "currentHash" to PasswordHash.create(next),
                "history" to (history + currentHash).takeLast(3),
                "changedAt" to System.currentTimeMillis()
            )))
            null
        } catch (e: Exception) {
            "No se pudo cambiar la clave: ${reason(e)}"
        }
    }

    fun passwordExpired(username: String): Boolean {
        val uid = currentUid() ?: return false
        return try {
            val snap = Tasks.await(userDoc(uid).get())
            val changed = snap.getLong("changedAt") ?: 0L
            if (changed == 0L) {
                userDoc(uid).update("changedAt", System.currentTimeMillis())
                false
            } else {
                System.currentTimeMillis() - changed >= Credentials.PASSWORD_PERIOD_MS
            }
        } catch (_: Exception) {
            false
        }
    }

    fun signOut() { auth.signOut() }

    fun logEvent(text: String) {
        val uid = currentUid() ?: return
        val time = SimpleDateFormat("dd-MM HH:mm", Locale.getDefault()).format(Date())
        val clean = text.replace('\n', ' ').take(300)
        try {
            Tasks.await(userDoc(uid).collection("events").add(mapOf<String, Any>(
                "text" to "$time · $clean",
                "createdAt" to System.currentTimeMillis()
            )))
        } catch (_: Exception) { /* la bitácora no debe detener el enlace */ }
    }

    fun recentEvents(): List<String> {
        val uid = currentUid() ?: return emptyList()
        return try {
            val snap = Tasks.await(userDoc(uid).collection("events")
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(60).get())
            snap.documents.mapNotNull { it.getString("text") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun queue(command: Char): Boolean {
        val uid = currentUid() ?: return false
        if (command !in "10A") return false
        return try {
            if (pending() >= 5) return false
            Tasks.await(userDoc(uid).collection("commands").add(mapOf<String, Any>(
                "command" to command.toString(),
                "createdAt" to System.currentTimeMillis()
            )))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun pending(): Int {
        val uid = currentUid() ?: return 0
        return try {
            Tasks.await(userDoc(uid).collection("commands").get()).size()
        } catch (_: Exception) {
            0
        }
    }

    fun drain(): List<Char> {
        val uid = currentUid() ?: return emptyList()
        return try {
            val snap = Tasks.await(userDoc(uid).collection("commands")
                .orderBy("createdAt", Query.Direction.ASCENDING).get())
            val result = snap.documents.mapNotNull { it.getString("command")?.firstOrNull()?.takeIf { c -> c in "10A" } }
            if (!snap.isEmpty) Tasks.await(Tasks.whenAll(snap.documents.map { it.reference.delete() }))
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
