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
        assertNull(LocalAccounts.validate("profe.juan_1", "Clave123"))
        assertEquals("profe.juan_1", LocalAccounts.normalize("  Profe.Juan_1 "))
        assertNotNull(LocalAccounts.validate("ab", "Clave123"))
        assertNotNull(LocalAccounts.validate("a ".repeat(11) + "x", "Clave123"))
        assertNotNull(LocalAccounts.validate("mi cuenta", "Clave123"))
        assertNotNull(LocalAccounts.validate("admin", "Clave123"))
        assertNotNull(LocalAccounts.validate("monitor", "Clave123"))
        assertNotNull(LocalAccounts.validate("profe", "abc"))
        assertNotNull(LocalAccounts.validate("profe", "x".repeat(129)))
    }
    @Test fun `password policy requires eight characters upper lower and digit`() {
        assertNull(LocalAccounts.validate("profe", "Huerto2026"))
        assertNotNull(LocalAccounts.validate("profe", "huerto2026"))
        assertNotNull(LocalAccounts.validate("profe", "HUERTO2026"))
        assertNotNull(LocalAccounts.validate("profe", "Huertoxzy"))
        assertNotNull(LocalAccounts.validate("profe", "Hu12345"))
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
    @Test fun `common patterns and username are rejected`() {
        assertNotNull(LocalAccounts.validate("profe", "Qwerty123"))
        assertNotNull(LocalAccounts.validate("profe", "Abc12345"))
        assertNotNull(LocalAccounts.validate("profe", "Profe123"))
        assertNull(LocalAccounts.validate("profe", "Huerto2026"))
    }
    @Test fun `history matches only previous hashes`() {
        val a = PasswordHash.create("Huerto2026")
        val b = PasswordHash.create("Campo2027")
        assertTrue(PasswordHash.matchesAny("Huerto2026", listOf(a, b)))
        assertFalse(PasswordHash.matchesAny("Lluvia2028", listOf(a, b)))
    }
}
