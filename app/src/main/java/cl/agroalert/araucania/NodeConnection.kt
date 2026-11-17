package cl.agroalert.araucania

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
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
    val bluetooth: Boolean = false,
    val events: List<String> = emptyList()
)

/** Enlace de texto delimitado por salto de línea, sobre TCP o Bluetooth SPP. Nunca reenvía órdenes tras una desconexión. */
class NodeConnection {
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    private val listeners = linkedSetOf<(NodeState) -> Unit>()
    @Volatile private var generation = 0
    @Volatile private var tcpSocket: Socket? = null
    @Volatile private var btSocket: BluetoothSocket? = null
    @Volatile private var role: Role? = null
    private var commandSequence = 0L
    @Volatile var state = NodeState(); private set

    fun observe(listener: (NodeState) -> Unit) { listeners.add(listener); listener(state) }
    fun remove(listener: (NodeState) -> Unit) { listeners.remove(listener) }
    private fun update(value: NodeState) { state = value; listeners.toList().forEach { it(value) } }
    private fun event(text: String): List<String> {
        Bitacora.log(text) // el historial persistente guarda los mismos eventos de la sesión
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        return (listOf("$time · $text") + state.events).take(12)
    }

    fun connect(host: String, port: Int, accessKey: String, selectedRole: Role) {
        launch(selectedRole, "Conectando con $host:$port…") { id ->
            val client = Socket()
            tcpSocket = client
            try {
                client.connect(InetSocketAddress(host, port), 5000)
                client.soTimeout = 1000
                client.tcpNoDelay = true
                val input = client.getInputStream()
                client.getOutputStream().write("AUTH|ROL:${selectedRole.wire}|TOKEN:$accessKey\n".toByteArray(Charsets.UTF_8))
                readLoop(input, id, selectedRole, authenticated = false)
            } finally { runCatching { client.close() } }
        }
    }

    @SuppressLint("MissingPermission") // ConnectionActivity exige BLUETOOTH_CONNECT antes de llamar aquí
    fun connectBluetooth(device: BluetoothDevice, selectedRole: Role) {
        launch(selectedRole, "Conectando con ${device.name ?: "el módulo Bluetooth"}…") { id ->
            val client = device.createRfcommSocketToServiceRecord(SPP_UUID)
            btSocket = client
            try {
                client.connect()
                main.post {
                    if (id == generation) update(state.copy(connected = true, connecting = false, bluetooth = true,
                        message = "Enlace Bluetooth establecido · esperando lecturas",
                        events = event("Nodo conectado · ${selectedRole.label}")))
                }
                readLoop(client.inputStream, id, selectedRole, authenticated = true)
            } finally { runCatching { client.close() } }
        }
    }

    private fun launch(selectedRole: Role, message: String, block: (Int) -> Unit) {
        disconnect("Conectando…")
        role = selectedRole
        val id = generation
        update(NodeState(connecting = true, message = message))
        Thread({
            try {
                if (id == generation) block(id)
            } catch (e: Exception) {
                val reason = when (e) {
                    is SecurityException -> "Permiso de Bluetooth denegado; concédelo y vuelve a intentar"
                    is ConnectException -> "No se pudo conectar al nodo: revisa la IP y el puerto"
                    is UnknownHostException -> "La dirección del nodo no es válida"
                    is SocketTimeoutException -> "El nodo no respondió a tiempo"
                    is SocketException -> "Se perdió la conexión con el nodo"
                    is IOException -> if (e.message?.contains("read failed", ignoreCase = true) == true)
                        "No se pudo conectar con el módulo Bluetooth" else e.message?.takeIf { it.isNotBlank() } ?: "Error de enlace"
                    else -> e.message?.takeIf { it.isNotBlank() } ?: "Error de enlace"
                }
                main.post { if (id == generation) disconnect(reason) }
            }
        }, "agroalert-link").start()
    }

    /** Bucle común de ambos enlaces: bytes → líneas → autenticación, telemetría y confirmaciones. */
    private fun readLoop(input: InputStream, id: Int, selectedRole: Role, authenticated: Boolean) {
        var authed = authenticated
        var lastValid = SystemClock.elapsedRealtime()
        val buffer = StringBuilder()
        while (id == generation) {
            if (SystemClock.elapsedRealtime() - lastValid > 7000) {
                throw IOException(if (authed) "Sin datos válidos durante 7 segundos" else "El nodo no confirmó el acceso")
            }
            val ch = if (input.available() > 0) input.read() else { Thread.sleep(150); continue }
            if (ch == -1) throw IOException("El nodo cerró la conexión")
            if (ch == 13) continue
            if (ch != 10) {
                if (buffer.length >= Protocol.MAX_LINE) throw IOException("Mensaje del nodo demasiado largo")
                buffer.append(ch.toChar())
                continue
            }
            val line = buffer.toString()
            buffer.clear()
            val message = Protocol.ackForArduino(line) ?: line
            if (!authed) {
                if (message != "AUTH:OK" && message != "AUTH:OK|SIMULADOR") throw IOException("Acceso rechazado por el nodo")
                authed = true
                lastValid = SystemClock.elapsedRealtime()
                main.post { if (id == generation) update(state.copy(connected = true, connecting = false,
                    message = "Enlace establecido · esperando lecturas", simulated = message.endsWith("SIMULADOR"),
                    events = event("Nodo conectado · ${selectedRole.label}"))) }
            } else {
                val reading = Protocol.parseCompatible(message)
                if (reading != null) {
                    lastValid = SystemClock.elapsedRealtime()
                    main.post {
                        if (id == generation) {
                            val events = if (state.telemetry?.risk != reading.risk) event(reading.risk.label) else state.events
                            update(state.copy(telemetry = reading, receivedAt = SystemClock.elapsedRealtime(),
                                message = "Recibiendo datos en tiempo real", events = events))
                        }
                    }
                } else if (message.startsWith("ACK:") || message.startsWith("ERROR:")) {
                    main.post {
                        if (id == generation) {
                            if (message == "ACK:${state.pending}" && state.pending != null) {
                                val command = state.pending
                                val confirmation = "Orden confirmada por el nodo · ACK:$command"
                                update(state.copy(pending = null, confirmation = confirmation, message = confirmation,
                                    events = event("Orden $command confirmada")))
                                main.postDelayed({
                                    if (id == generation && state.confirmation == confirmation) update(state.copy(confirmation = null))
                                }, 4000)
                            } else if (message.startsWith("ERROR:")) {
                                val confirmation = "El nodo rechazó la orden: ${message.removePrefix("ERROR:")}"
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
    }

    fun send(command: Char) {
        if (role != Role.CONTROL || !state.connected || state.pending != null || command !in "10A") return
        val reading = state.telemetry ?: return
        if (SystemClock.elapsedRealtime() - state.receivedAt > 7000 || reading.risk == Risk.SENSOR_ERROR) return
        val id = generation
        val sequence = ++commandSequence
        val out: OutputStream = tcpSocket?.getOutputStream() ?: btSocket?.getOutputStream() ?: return
        update(state.copy(pending = command, confirmation = null, message = "Esperando confirmación…",
            events = event("Orden $command enviada")))
        writer.execute {
            try {
                if (id == generation) {
                    out.write("$command\n".toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            } catch (_: Exception) { main.post { if (id == generation) disconnect("No se pudo enviar la orden") } }
        }
        main.postDelayed({
            if (id == generation && sequence == commandSequence && state.pending == command) disconnect("Orden sin confirmar; comprueba el estado al reconectar")
        }, 5000)
    }

    fun disconnect(reason: String = "Desconectado") {
        generation++
        runCatching { tcpSocket?.close() }; tcpSocket = null
        runCatching { btSocket?.close() }; btSocket = null
        role = null
        update(state.copy(connected = false, connecting = false, pending = null, confirmation = null,
            message = reason, events = event(reason)))
    }

    companion object {
        /** Perfil serie estándar (SPP) del HC-05. */
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
