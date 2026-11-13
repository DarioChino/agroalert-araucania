package cl.agroalert.araucania

import android.app.Application

class AgroApplication : Application() {
    val node = NodeConnection()
    lateinit var credentials: Credentials; private set
    var role: Role? = null
    override fun onCreate() { super.onCreate(); credentials = Credentials(this) }
    fun logout() { node.disconnect("Sesión cerrada"); role = null }
}
