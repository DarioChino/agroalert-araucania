package cl.agroalert.araucania

/** Stub compilado mientras no existe google-services.json; la app usa la ruta local. */
object CloudStore {
    fun signIn(username: String, password: String): Boolean = error("Firebase no configurado")
    fun createAccount(username: String, password: String): String? = error("Firebase no configurado")
    fun changePassword(username: String, current: String, next: String): String? = error("Firebase no configurado")
    fun passwordExpired(username: String): Boolean = error("Firebase no configurado")
    fun signOut(): Unit = error("Firebase no configurado")
    fun logEvent(text: String): Unit = error("Firebase no configurado")
    fun recentEvents(): List<String> = error("Firebase no configurado")
    fun queue(command: Char): Boolean = error("Firebase no configurado")
    fun pending(): Int = error("Firebase no configurado")
    fun drain(): List<Char> = error("Firebase no configurado")
}
