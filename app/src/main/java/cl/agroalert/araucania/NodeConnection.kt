package cl.agroalert.araucania

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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

/** Enlace de texto delimitado por salto de línea, sobre TCP o Bluetooth SPP. Ante una caída inesperada reconecta solo y reenvía las órdenes que quedaron en cola. */
class NodeConnection {
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    private val listeners = linkedSetOf<(NodeState) -> Unit>()
    @Volatile private var generation = 0
    @Volatile private var tcpSocket: Socket? = null
    @Volatile private var btSocket: BluetoothSocket? = null
    @Volatile private var role: Role? = null
    private var commandSequence = 0L
    // Datos del último enlace Wi-Fi para la reconexión automática (null = no reconectar).
    private var autoHost: String? = null
    private var autoPort = 0
    private var autoKey: String? = null
    private var autoRole: Role? = null
    // Datos del último enlace Bluetooth, para reconectar igual que se hace con Wi-Fi.
    private var autoDevice: BluetoothDevice? = null
    @Volatile private var transportIsBt = false
    private var reconnectAttempts = 0
    @Volatile private var autoReconnecting = false
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
        autoDevice = null; transportIsBt = false
        autoHost = host; autoPort = port; autoKey = accessKey; autoRole = selectedRole
        launch(selectedRole, "Conectando con $host:$port…") { id ->
            val client = Socket()
            tcpSocket = client
            try {
                client.connect(InetSocketAddress(host, port), 5000)
                client.soTimeout = 1000
                client.tcpNoDelay = true
                val input = client.getInputStream()
                client.getOutputStream().write("AUTH|ROL:${selectedRole.wire}|TOKEN:$accessKey\n".toByteArray(Charsets.UTF_8))
                readLoop(input, client.getOutputStream(), id, selectedRole, authenticated = false)
            } finally { runCatching { client.close() } }
        }
    }

    @SuppressLint("MissingPermission") // ConnectionActivity exige BLUETOOTH_CONNECT antes de llamar aquí
    fun connectBluetooth(device: BluetoothDevice, selectedRole: Role) {
        val label = try { device.name } catch (_: SecurityException) { null } ?: "el módulo Bluetooth"
        autoHost = null; autoDevice = device; transportIsBt = true
        launch(selectedRole, "Conectando con $label…") { id ->
            // Con un escaneo activo Android suele rechazar el SPP; hay que cancelarlo antes de connect().
            runCatching { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() }
            val client = device.createRfcommSocketToServiceRecord(SPP_UUID)
            btSocket = client
            try {
                client.connect()
                main.post {
                    if (id == generation) {
                        val recovered = autoReconnecting
                        autoReconnecting = false
                        reconnectAttempts = 0
                        update(state.copy(connected = true, connecting = false, bluetooth = true,
                            message = "Enlace Bluetooth establecido · esperando lecturas",
                            events = event(if (recovered) "Bluetooth reconectado" else "Nodo conectado · ${selectedRole.label}")))
                        if (recovered) drainPending(id)
                    }
                }
                readLoop(client.inputStream, client.outputStream, id, selectedRole, authenticated = true)
            } finally { runCatching { client.close() } }
        }
    }

    private fun launch(selectedRole: Role, message: String, block: (Int) -> Unit) {
        generation++
        closeSockets()
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
                    is IOException -> when {
                        e.message?.contains("read failed", ignoreCase = true) == true ->
                            "No se pudo conectar con el HC-05: revisa que esté encendido y emparejado"
                        e.message?.contains("Sin datos válidos", ignoreCase = true) == true && transportIsBt ->
                            "El HC-05 no envió lecturas: revisa el baud (38400/9600) y el divisor 5V→3.3V en RX"
                        else -> e.message?.takeIf { it.isNotBlank() } ?: "Error de enlace"
                    }
                    else -> e.message?.takeIf { it.isNotBlank() } ?: "Error de enlace"
                }
                // El rechazo de credenciales no se reintenta: reconectar no arregla una clave errónea.
                val rejected = e is IOException && e.message == "Acceso rechazado por el nodo"
                main.post { if (id == generation) { if (rejected) disconnect(reason) else scheduleReconnect(reason) } }
            }
        }, "agroalert-link").start()
    }

    /** Bucle común de ambos enlaces: bytes → líneas → autenticación, telemetría y confirmaciones. */
    private fun readLoop(input: InputStream, output: OutputStream, id: Int, selectedRole: Role, authenticated: Boolean) {
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
            if (line == "?") {
                // El firmware prueba 38400 y 9600: este '!' confirma el baud que sí llegó.
                lastValid = SystemClock.elapsedRealtime()
                output.write("!\n".toByteArray(Charsets.UTF_8))
                output.flush()
                continue
            }
            val message = Protocol.ackForArduino(line) ?: line
            if (!authed) {
                if (message != "AUTH:OK" && message != "AUTH:OK|SIMULADOR") throw IOException("Acceso rechazado por el nodo")
                authed = true
                lastValid = SystemClock.elapsedRealtime()
                val recovered = autoReconnecting
                if (recovered) autoReconnecting = false
                reconnectAttempts = 0
                main.post {
                    if (id == generation) {
                        update(state.copy(connected = true, connecting = false,
                            message = "Enlace establecido · esperando lecturas", simulated = message.endsWith("SIMULADOR"),
                            events = event(if (recovered) "Reconectado automáticamente" else "Nodo conectado · ${selectedRole.label}")))
                        if (recovered) drainPending(id)
                    }
                }
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
            } catch (_: Exception) {
                main.post { if (id == generation) { Buffer.queue(command); scheduleReconnect("No se pudo enviar la orden") } }
            }
        }
        main.postDelayed({
            if (id == generation && sequence == commandSequence && state.pending == command) {
                Buffer.queue(command)
                scheduleReconnect("Orden sin confirmar; quedó en cola para sincronizar")
            }
        }, 5000)
    }

    /** Corte iniciado por la persona: cancela la reconexión automática. */
    fun disconnect(reason: String = "Desconectado") {
        generation++
        closeSockets()
        autoHost = null; autoKey = null; autoRole = null; autoDevice = null
        transportIsBt = false
        reconnectAttempts = 0
        autoReconnecting = false
        role = null
        update(state.copy(connected = false, connecting = false, pending = null, confirmation = null,
            message = reason, events = event(reason)))
    }

    private fun closeSockets() {
        runCatching { tcpSocket?.close() }; tcpSocket = null
        runCatching { btSocket?.close() }; btSocket = null
    }

    /** Caída inesperada: reintenta el último enlace cada 5 s, hasta 6 veces, y sincroniza la cola al volver. */
    private fun scheduleReconnect(reason: String) {
        generation++
        closeSockets()
        val host = autoHost; val key = autoKey; val target = autoRole; val device = autoDevice
        if (target == null || (host == null && device == null) || (host != null && key == null) || reconnectAttempts >= 5) {
            autoReconnecting = false
            val final = if (host == null && device == null) reason else "No se pudo reconectar tras 6 reintentos"
            role = null
            update(state.copy(connected = false, connecting = false, pending = null, confirmation = null,
                message = final, events = event(final)))
            return
        }
        reconnectAttempts++
        autoReconnecting = true
        role = null
        val notice = "Reconectando ($reconnectAttempts/6)…"
        val ticket = reconnectAttempts
        update(state.copy(connected = false, connecting = true, pending = null, confirmation = null,
            message = notice, events = event("$reason · $notice")))
        main.postDelayed({
            if (autoReconnecting && reconnectAttempts == ticket) {
                if (device != null) connectBluetooth(device, target) else connect(host!!, autoPort, key!!, target)
            }
        }, 5000)
    }

    /** Reenvía las órdenes que quedaron pendientes; cada una espera su ACK como la primera vez. */
    private fun drainPending(id: Int) {
        val queued = Buffer.drain()
        if (queued.isEmpty()) return
        val palabra = if (queued.size == 1) "orden" else "órdenes"
        main.post { if (id == generation) update(state.copy(events = event("Sincronizando ${queued.size} $palabra guardadas"))) }
        // shortcut: si el enlace cae durante el drenaje, las órdenes restantes no se reenvían; upgrade: devolverlas a la cola
        queued.forEachIndexed { index, cmd -> main.postDelayed({ if (id == generation) send(cmd) }, 2500 + index * 1500L) }
    }

    companion object {
        /** Perfil serie estándar (SPP) del HC-05. */
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
