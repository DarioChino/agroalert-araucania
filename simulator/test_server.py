import asyncio
import unittest
from server import AgroServer


class IntegrationTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.node = AgroServer("control-test-token", "monitor-test-token")
        self.port = await self.node.start("127.0.0.1", 0)
        self.connections = []

    async def asyncTearDown(self):
        for _, writer in self.connections:
            writer.close()
            await writer.wait_closed()
        await self.node.close()

    async def connect(self, role, token):
        reader, writer = await asyncio.open_connection("127.0.0.1", self.port)
        self.connections.append((reader, writer))
        writer.write(f"AUTH|ROL:{role}|TOKEN:{token}\n".encode())
        await writer.drain()
        return reader, writer

    async def line(self, reader):
        return (await asyncio.wait_for(reader.readline(), 2)).decode().strip()

    async def test_two_phones_and_monitor_cannot_control(self):
        cr, cw = await self.connect("CONTROL", "control-test-token")
        mr, mw = await self.connect("MONITOR", "monitor-test-token")
        self.assertEqual(await self.line(cr), "AUTH:OK|SIMULADOR")
        self.assertEqual(await self.line(mr), "AUTH:OK|SIMULADOR")
        self.assertEqual(await self.line(cr), await self.line(mr))
        mw.write(b"1\n")
        await mw.drain()
        self.assertEqual(await self.line(mr), "ERROR:ROL_SIN_PERMISO")
        self.assertFalse(self.node.relay)
        for command, expected in ((b"1\n", "RELE:ACTIVADO"), (b"0\n", "RELE:APAGADO"), (b"A\n", "MODO:AUTO")):
            cw.write(command)
            await cw.drain()
            self.assertEqual(await self.line(cr), f"ACK:{command.decode().strip()}")
            control_data, monitor_data = await self.line(cr), await self.line(mr)
            self.assertEqual(control_data, monitor_data)
            self.assertIn(expected, control_data)

    async def test_wrong_role_key_rejected(self):
        reader, _ = await self.connect("CONTROL", "monitor-test-token")
        self.assertEqual(await self.line(reader), "AUTH:ERROR")
        self.assertEqual(await self.line(reader), "")

    async def test_fragmented_auth_and_invalid_command(self):
        r, w = await asyncio.open_connection("127.0.0.1", self.port)
        self.connections.append((r, w))
        for part in (b"AUTH|ROL:CON", b"TROL|TOKEN:control-test-token\n"):
            w.write(part)
            await w.drain()
        self.assertEqual(await self.line(r), "AUTH:OK|SIMULADOR")
        await self.line(r)
        w.write(b"ON\n")
        await w.drain()
        self.assertEqual(await self.line(r), "ERROR:COMANDO_INVALIDO")

    async def test_sensor_error_blocks_commands(self):
        self.node.sensor_ok = False
        r, w = await self.connect("CONTROL", "control-test-token")
        await self.line(r)
        self.assertIn("TEMP:NA|HUM:NA", await self.line(r))
        w.write(b"1\n")
        await w.drain()
        self.assertEqual(await self.line(r), "ERROR:SENSOR_ERROR")
        self.assertFalse(self.node.relay)

    async def test_disconnect_control_returns_auto_for_monitor(self):
        cr, cw = await self.connect("CONTROL", "control-test-token")
        mr, _ = await self.connect("MONITOR", "monitor-test-token")
        for reader in (cr, mr):
            await self.line(reader)
            await self.line(reader)
        cw.write(b"1\n")
        await cw.drain()
        await self.line(cr)
        await self.line(cr)
        await self.line(mr)
        cw.close()
        await cw.wait_closed()
        self.assertIn("MODO:AUTO", await self.line(mr))


if __name__ == "__main__":
    unittest.main()
