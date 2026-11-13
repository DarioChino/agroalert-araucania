package cl.agroalert.araucania

import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    private fun frame(t: String = "15.0", h: String = "65", state: String = "OK") =
        "TELEMETRIA|TEMP:${t}C|HUM:${h}%|RELE:APAGADO|MODO:AUTO|ESTADO:$state"

    @Test fun `accepts original firmware and wifi extension`() {
        val original = Protocol.parseCompatible(frame())!!
        assertEquals(15.0, original.temperature!!, 0.001)
        assertEquals(original, Protocol.parseCompatible(frame() + "|SENSOR:OK"))
    }
    @Test fun `boundary temperatures have exact project semantics`() {
        for ((t, risk) in listOf(10.1 to Risk.OK, 10.0 to Risk.PRE_HELADA, 5.1 to Risk.PRE_HELADA, 5.0 to Risk.HELADA_CRITICA, -2.0 to Risk.HELADA_CRITICA)) {
            assertEquals(risk, Protocol.parseCompatible(frame(t.toString(), state = risk.name))!!.risk)
        }
    }
    @Test fun `rejects malformed and misleading values`() {
        for (line in listOf(frame("NaN"), frame("Infinity"), frame(h = "101"), frame("5.0"),
            frame().replace("RELE:APAGADO", "RELE:INVALIDO"), frame() + "|TEMP:1C",
            frame().replace("HUM:65%", "TEMP:65%"), "X".repeat(513))) {
            assertNull(line, Protocol.parseCompatible(line))
        }
    }
    @Test fun `sensor errors never invent measurements`() {
        val t = Protocol.parseCompatible("TELEMETRIA|TEMP:NA|HUM:NA|RELE:APAGADO|MODO:AUTO|ESTADO:SENSOR_ERROR|SENSOR:ERROR")!!
        assertNull(t.temperature)
        assertNull(t.humidity)
        assertEquals(Risk.SENSOR_ERROR, t.risk)
    }
}
