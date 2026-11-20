package cl.agroalert.araucania

/**
 * Cuentas de la app: una sola clase, sin perfiles. El registro local guarda
 * únicamente "salt:hash" (PBKDF2); con Firebase activo ese valor vive en Firestore.
 */
object LocalAccounts {
    private val NAME = Regex("[a-z0-9._]{3,20}")
    private val MAYUS = Regex("[A-Z]")
    private val MINUS = Regex("[a-z]")
    private val DIGITO = Regex("[0-9]")
    private val RESERVED = setOf("admin")
    private val COMMON = listOf("1234", "123456", "qwerty", "password", "abc123", "111111")
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    /** El correo debe ser real: Firebase no entrega a nuestro dominio interno. */
    fun validateEmail(email: String): String? {
        val value = email.trim()
        return when {
            value.length > 120 -> "El correo es demasiado largo."
            value.endsWith("@agroalert.local") -> "Ese dominio no recibe correos; usa tu correo real."
            !EMAIL.matches(value) -> "Escribe un correo válido para recuperar la clave."
            else -> null
        }
    }

    fun normalize(username: String) = username.trim().lowercase()

    /** null si la clave sirve; se usa al crear y al cambiar contraseña. */
    fun validatePassword(password: String, username: String = ""): String? = when {
        password.length !in 8..128 -> "La contraseña debe tener entre 8 y 128 caracteres."
        !MAYUS.containsMatchIn(password) || !MINUS.containsMatchIn(password) || !DIGITO.containsMatchIn(password) ->
            "La contraseña necesita al menos una mayúscula, una minúscula y un número."
        COMMON.any { it in password.lowercase() } -> "La contraseña contiene un patrón común (1234, qwerty, ...)."
        username.isNotBlank() && normalize(username) in password.lowercase() -> "La contraseña no puede contener el usuario."
        else -> null
    }

    /** null si los datos sirven; si no, el motivo para mostrar en pantalla. */
    fun validate(username: String, password: String): String? = when {
        !NAME.matches(normalize(username)) -> "El usuario debe tener 3 a 20 letras, números, punto o guion bajo."
        normalize(username) in RESERVED -> "Ese nombre está reservado para la cuenta inicial."
        else -> validatePassword(password, normalize(username))
    }

    /** Devuelve la credencial "salt:hash" del registro, o null si está corrupto. */
    fun decode(record: String): String? = runCatching {
        val parts = record.split(':')
        if (parts.size == 2 && parts.all { it.isNotBlank() }) record else null
    }.getOrNull()
}

enum class Risk(val label: String) {
    OK("Condición normal"), PRE_HELADA("Alerta de prehelada"),
    HELADA_CRITICA("Riesgo crítico de helada"), SENSOR_ERROR("Revisar sensor")
}

/** Niveles de riesgo por factor, de menor a mayor severidad. */
enum class Nivel(val label: String) {
    NORMAL("Normal"), ALERTA("Alerta"), CRITICO("Crítico")
}

data class Telemetry(
    val temperature: Double?, val humidity: Double?, val relay: Boolean,
    val automatic: Boolean, val risk: Risk
)

object Protocol {
    const val MAX_LINE = 512
    /** El simulador sigue esperando este campo por cable; no es un perfil de cuenta. */
    const val NODE_ROLE = "CONTROL"

    /** El firmware Bluetooth original responde con texto propio; se traduce al ACK estándar. */
    fun ackForArduino(line: String): String? = when (line) {
        "RELE:ENCENDIDO_MANUAL" -> "ACK:1"
        "RELE:APAGADO_MANUAL" -> "ACK:0"
        "MODO:AUTOMATICO" -> "ACK:A"
        else -> null
    }

    fun riskFor(temperature: Double): Risk = when {
        temperature <= 5.0 -> Risk.HELADA_CRITICA
        temperature <= 10.0 -> Risk.PRE_HELADA
        else -> Risk.OK
    }

    /** Traduce el riesgo térmico a nivel de alerta; null significa sin datos. */
    fun temperatureLevel(risk: Risk): Nivel? = when (risk) {
        Risk.OK -> Nivel.NORMAL
        Risk.PRE_HELADA -> Nivel.ALERTA
        Risk.HELADA_CRITICA -> Nivel.CRITICO
        Risk.SENSOR_ERROR -> null
    }

    /** Humedad ambiente: 40-75 % normal; fuera de rango alerta; 85 % o más es crítico (moho). */
    fun humidityLevel(humidity: Double): Nivel = when {
        humidity >= 85.0 -> Nivel.CRITICO
        humidity > 75.0 || humidity < 35.0 -> Nivel.ALERTA
        else -> Nivel.NORMAL
    }

    /** El formato coincide con la telemetría publicada; SENSOR_ERROR es la extensión Wi-Fi. */
    fun parse(line: String): Telemetry? {
        if (line.length > MAX_LINE || !line.startsWith("TELEMETRIA|")) return null
        val parts = line.split('|').drop(1)
        if (parts.size != 6) return null
        val fields = linkedMapOf<String, String>()
        for (part in parts) {
            val pair = part.split(':', limit = 2)
            if (pair.size != 2 || fields.put(pair[0], pair[1]) != null) return null
        }
        if (fields.keys != setOf("TEMP", "HUM", "RELE", "MODO", "ESTADO", "SENSOR")) {
            return null
        }
        return parseFields(fields)
    }

    fun parseCompatible(line: String): Telemetry? {
        if (line.length > MAX_LINE) return null
        // El firmware original tiene cinco campos; el contrato Wi-Fi añade SENSOR.
        return if (line.startsWith("TELEMETRIA|") && line.split('|').size == 6) {
            parse("$line|SENSOR:OK")
        } else parse(line)
    }

    private fun parseFields(f: Map<String, String>): Telemetry? {
        val relay = when (f["RELE"]) { "ACTIVADO" -> true; "APAGADO" -> false; else -> return null }
        val automatic = when (f["MODO"]) { "AUTO" -> true; "MANUAL" -> false; else -> return null }
        if (f["SENSOR"] == "ERROR") {
            if (f["TEMP"] != "NA" || f["HUM"] != "NA" || f["ESTADO"] != "SENSOR_ERROR") return null
            return Telemetry(null, null, relay, automatic, Risk.SENSOR_ERROR)
        }
        if (f["SENSOR"] != "OK") return null
        val rawT = f["TEMP"] ?: return null
        val rawH = f["HUM"] ?: return null
        if (!rawT.endsWith('C') || !rawH.endsWith('%')) return null
        val t = rawT.dropLast(1).toDoubleOrNull() ?: return null
        val h = rawH.dropLast(1).toDoubleOrNull() ?: return null
        if (!t.isFinite() || !h.isFinite() || t !in -50.0..80.0 || h !in 0.0..100.0) return null
        val risk = riskFor(t)
        if (f["ESTADO"] != risk.name) return null
        return Telemetry(t, h, relay, automatic, risk)
    }
}
