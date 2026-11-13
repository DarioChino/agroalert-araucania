package cl.agroalert.araucania

enum class Role(val wire: String, val label: String) {
    CONTROL("CONTROL", "Control"), MONITOR("MONITOR", "Monitor")
}

/** Nombres de usuario locales aceptados en el inicio de sesión. */
object Users {
    fun resolve(username: String): Role? = when (username.trim().lowercase()) {
        "admin", "control" -> Role.CONTROL
        "monitor" -> Role.MONITOR
        else -> null
    }
}

enum class Risk(val label: String) {
    OK("Condición normal"), PRE_HELADA("Alerta de prehelada"),
    HELADA_CRITICA("Riesgo crítico de helada"), SENSOR_ERROR("Revisar sensor")
}

data class Telemetry(
    val temperature: Double?, val humidity: Double?, val relay: Boolean,
    val automatic: Boolean, val risk: Risk
)

object Protocol {
    const val MAX_LINE = 512
    fun riskFor(temperature: Double): Risk = when {
        temperature <= 5.0 -> Risk.HELADA_CRITICA
        temperature <= 10.0 -> Risk.PRE_HELADA
        else -> Risk.OK
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
