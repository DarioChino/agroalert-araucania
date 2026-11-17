package cl.agroalert.araucania

import org.junit.Assert.*
import org.junit.Test

class CredentialsTest {
    @Test fun `admin and monitor usernames resolve to their roles`() {
        assertEquals(Role.CONTROL, Users.resolve("admin"))
        assertEquals(Role.CONTROL, Users.resolve("  ADMIN "))
        assertEquals(Role.CONTROL, Users.resolve("control"))
        assertEquals(Role.MONITOR, Users.resolve("monitor"))
        assertEquals(Role.MONITOR, Users.resolve("Monitor"))
        assertNull(Users.resolve("root"))
        assertNull(Users.resolve(""))
    }
    @Test fun `custom account validation accepts valid names and rejects reserved or malformed`() {
        assertNull(LocalAccounts.validate("profe.juan_1", "clave123"))
        assertEquals("profe.juan_1", LocalAccounts.normalize("  Profe.Juan_1 "))
        assertNotNull(LocalAccounts.validate("ab", "clave123"))
        assertNotNull(LocalAccounts.validate("a ".repeat(11) + "x", "clave123"))
        assertNotNull(LocalAccounts.validate("mi cuenta", "clave123"))
        assertNotNull(LocalAccounts.validate("admin", "clave123"))
        assertNotNull(LocalAccounts.validate("monitor", "clave123"))
        assertNotNull(LocalAccounts.validate("profe", "abc"))
        assertNotNull(LocalAccounts.validate("profe", "x".repeat(129)))
    }
    @Test fun `custom account record roundtrip keeps role and credential`() {
        val parsed = LocalAccounts.decode("MONITOR:c2FsdA==:aGFzaA==")
        assertEquals(Role.MONITOR, parsed?.first)
        assertEquals("c2FsdA==:aGFzaA==", parsed?.second)
        assertNull(LocalAccounts.decode("garbage"))
        assertNull(LocalAccounts.decode("OTRO:c2FsdA==:aGFzaA=="))
    }
    @Test fun `third failure locks for sixty seconds and then allows retry`() {
        val third = AttemptState().failed(100).failed(200).failed(300)
        assertEquals(60_000L, third.remaining(300))
        assertEquals(1L, third.remaining(60_299))
        assertEquals(0L, third.remaining(60_300))
        assertEquals(AttemptState(1, 0), third.failed(60_300))
    }
    @Test fun `arduino bluetooth replies map to standard acks`() {
        assertEquals("ACK:1", Protocol.ackForArduino("RELE:ENCENDIDO_MANUAL"))
        assertEquals("ACK:0", Protocol.ackForArduino("RELE:APAGADO_MANUAL"))
        assertEquals("ACK:A", Protocol.ackForArduino("MODO:AUTOMATICO"))
        assertNull(Protocol.ackForArduino("TELEMETRIA|TEMP:15.0C|HUM:65%|RELE:APAGADO|MODO:AUTO|ESTADO:OK"))
        assertNull(Protocol.ackForArduino("=== AGROALERT ARAUCANIA CONECTADO ==="))
    }

    @Test fun `salted passwords verify without storing plaintext`() {
        val one = PasswordHash.create("clave-ejemplo")
        val two = PasswordHash.create("clave-ejemplo")
        assertNotEquals(one, two)
        assertFalse(one.contains("clave-ejemplo"))
        assertTrue(PasswordHash.verify("clave-ejemplo", one))
        assertFalse(PasswordHash.verify("incorrecta", one))
        assertFalse(PasswordHash.verify("clave-ejemplo", "corrupto"))
    }
}
