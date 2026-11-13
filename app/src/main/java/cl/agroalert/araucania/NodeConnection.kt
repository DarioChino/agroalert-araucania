package cl.agroalert.araucania

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors

data class NodeState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val message: String = "Sin conexión",
    val telemetry: Telemetry? = null,
    val receivedAt: Long = 0,
    val pending: Char? = null,
    val confirmation: String? = null,
    val simulated: Boolean = false,
    val events: List<String> = emptyList()
)

/** TCP de texto delimitado por salto de línea. Nunca reenvía órdenes tras una desconexión. */
class NodeConnection {
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    private val listeners = linkedSetOf<(NodeState) -> Unit>()
    @Volatile private var generation = 0
    @Volatile private var socket: Socket? = null
    @Volatile private var role: Role? = null
    private var commandSequence = 0L
    @Volatile var state = NodeState(); private set

    fun observe(listener: (NodeState) -> Unit) { listeners.add(listener); listener(state) }
    fun remove(listener: (NodeState) -> Unit) { listeners.remove(listener) }
    private fun update(value: NodeState) { state = value; listeners.toList().forEach { it(value) } }
    private fun event(text: String): List<String> {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        return (listOf("$time · $text") + state.events).take(12)
    }

    fun connect(host: String, port: Int, accessKey: String, selectedRole: Role) {
        disconnect("Conectando…")
        role = selectedRole
        val id = generation
        update(NodeState(connecting = true, message = "Conectando con $host:$port…"))
        val client = Socket()
        socket = client
        Thread({
            try {
                if (id != generation) return@Thread
                client.connect(InetSocketAddress(host, port), 5000)
                client.soTimeout = 1000
                client.tcpNoDelay = true
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                client.getOutputStream().write("AUTH|ROL:${selectedRole.wire}|TOKEN:$accessKey\n".toByteArray(Charsets.UTF_8))
                var authenticated = false
                var lastValid = SystemClock.elapsedRealtime()
                val buffer = StringBuilder()
                while (id == generation) {
                    if (SystemClock.elapsedRealtime() - lastValid > 7000) {
                        throw java.io.IOException(if (authenticated) "Sin datos válidos durante 7 segundos" else "El nodo no confirmó el acceso")
                    }
                    val ch = try { reader.read() } catch (_: SocketTimeoutException) { continue }
                    if (ch == -1) throw java.io.IOException("El nodo cerró la conexión")
                    if (ch == 13) continue
                    if (ch != 10) {
                        if (buffer.length >= Protocol.MAX_LINE) throw java.io.IOException("Mensaje del nodo demasiado largo")
                        buffer.append(ch.toChar())
                        continue
                    }
                    val line = buffer.toString()
                    buffer.clear()
                    if (!authenticated) {
                        if (line != "AUTH:OK" && line != "AUTH:OK|SIMULADOR") throw java.io.IOException("Acceso rechazado por el nodo")
                        authenticated = true
                        lastValid = SystemClock.elapsedRealtime()
                        main.post { if (id == generation) update(state.copy(connected = true, connecting = false,
                            message = "Enlace establecido · esperando lecturas", simulated = line.endsWith("SIMULADOR"),
                            events = event("Nodo conectado · ${selectedRole.label}"))) }
                    } else {
                        val reading = Protocol.parseCompatible(line)
                        if (reading != null) {
                            lastValid = SystemClock.elapsedRealtime()
                            main.post {
                                if (id == generation) {
                                    val events = if (state.telemetry?.risk != reading.risk) event(reading.risk.label) else state.events
                                    update(state.copy(telemetry = reading, receivedAt = SystemClock.elapsedRealtime(),
                                        message = "Recibiendo datos en tiempo real", events = events))
                                }
                            }
                        } else if (line.startsWith("ACK:") || line.startsWith("ERROR:")) {
                            main.post {
                                if (id == generation) {
                                    if (line == "ACK:${state.pending}" && state.pending != null) {
                                        val command = state.pending
                                        val confirmation = "Orden confirmada por el nodo · ACK:$command"
                                        update(state.copy(pending = null, confirmation = confirmation, message = confirmation,
                                            events = event("Orden $command confirmada")))
                                        main.postDelayed({
                                            if (id == generation && state.confirmation == confirmation) update(state.copy(confirmation = null))
                                        }, 4000)
                                    } else if (line.startsWith("ERROR:")) {
                                        val confirmation = "El nodo rechazó la orden: ${line.removePrefix("ERROR:")}"
                                        update(state.copy(pending = null, confirmation = confirmation, message = confirmation,
                                            events = event("El nodo rechazó la orden")))
                                        main.postDelayed({
                                            if (id == generation && state.confirmation == confirmation) update(state.copy(confirmation = null))
                                        }, 4000)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val reason = when (e) {
                    is ConnectException -> "No se pudo conectar al nodo: revisa la IP y el puerto"
                    is UnknownHostException -> "La dirección del nodo no es válida"
                    is SocketTimeoutException -> "El nodo no respondió a tiempo"
                    is SocketException -> "Se perdió la conexión con el nodo"
                    else -> e.message?.takeIf { it.isNotBlank() } ?: "Error de red"
                }
                main.post { if (id == generation) disconnect(reason) }
            } finally { runCatching { client.close() } }
        }, "agroalert-reader").start()
    }

    fun send(command: Char) {
        if (role != Role.CONTROL || !state.connected || state.pending != null || command !in "10A") return
        val reading = state.telemetry ?: return
        if (SystemClock.elapsedRealtime() - state.receivedAt > 7000 || reading.risk == Risk.SENSOR_ERROR) return
        val id = generation
        val sequence = ++commandSequence
        val client = socket ?: return
        update(state.copy(pending = command, confirmation = null, message = "Esperando confirmación…",
            events = event("Orden $command enviada")))
        writer.execute {
            try {
                if (id == generation) {
                    client.getOutputStream().write("$command\n".toByteArray(Charsets.UTF_8))
                    client.getOutputStream().flush()
                }
            } catch (_: Exception) { main.post { if (id == generation) disconnect("No se pudo enviar la orden") } }
        }
        main.postDelayed({
            if (id == generation && sequence == commandSequence && state.pending == command) disconnect("Orden sin confirmar; comprueba el estado al reconectar")
        }, 5000)
    }

    fun disconnect(reason: String = "Desconectado") {
        generation++
        runCatching { socket?.close() }
        socket = null
        role = null
        update(state.copy(connected = false, connecting = false, pending = null, confirmation = null,
            message = reason, events = event(reason)))
    }
}
