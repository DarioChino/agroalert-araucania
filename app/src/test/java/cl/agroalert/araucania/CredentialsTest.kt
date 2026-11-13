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
    @Test fun `third failure locks for sixty seconds and then allows retry`() {
        val third = AttemptState().failed(100).failed(200).failed(300)
        assertEquals(60_000L, third.remaining(300))
        assertEquals(1L, third.remaining(60_299))
        assertEquals(0L, third.remaining(60_300))
        assertEquals(AttemptState(1, 0), third.failed(60_300))
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
