"""Nodo AgroAlert simulado: Python 3.10+, sin dependencias externas."""
import argparse
import asyncio
import hmac
import json
import math
from pathlib import Path
import secrets
import socket
import time


class AgroServer:
    def __init__(self, control_token, monitor_token, temperature=15.0, humidity=65.0, cycle=False):
        self.tokens = {"CONTROL": control_token, "MONITOR": monitor_token}
        self.temperature = temperature
        self.humidity = humidity
        self.cycle = cycle
        self.started = time.monotonic()
        self.automatic = True
        self.relay = False
        self.sensor_ok = True
        self.clients = {}
        self.server = None
        self.ticker = None
        self.lease_until = 0.0
        self.evaluate()

    def evaluate(self):
        if not self.sensor_ok:
            self.relay = False
        elif self.automatic:
            self.relay = self.temperature <= 5.0

    def telemetry(self):
        self.evaluate()
        if self.sensor_ok:
            risk = "HELADA_CRITICA" if self.temperature <= 5 else "PRE_HELADA" if self.temperature <= 10 else "OK"
            values = f"TEMP:{self.temperature:.1f}C|HUM:{self.humidity:.0f}%"
        else:
            risk = "SENSOR_ERROR"
            values = "TEMP:NA|HUM:NA"
        return (f"TELEMETRIA|{values}|RELE:{'ACTIVADO' if self.relay else 'APAGADO'}"
                f"|MODO:{'AUTO' if self.automatic else 'MANUAL'}|ESTADO:{risk}"
                f"|SENSOR:{'OK' if self.sensor_ok else 'ERROR'}")

    async def send(self, writer, text):
        writer.write((text + "\n").encode("ascii"))
        await asyncio.wait_for(writer.drain(), timeout=3)

    async def broadcast(self):
        line = self.telemetry()
        for writer in list(self.clients):
            try:
                await self.send(writer, line)
            except (OSError, asyncio.TimeoutError, ConnectionError):
                self.clients.pop(writer, None)
                writer.close()

    async def handle(self, reader, writer):
        try:
            raw = await asyncio.wait_for(reader.readline(), timeout=5)
            line = raw.decode("ascii").strip()
            fields = line.split("|")
            role = fields[1].removeprefix("ROL:") if len(fields) == 3 else ""
            token = fields[2].removeprefix("TOKEN:") if len(fields) == 3 else ""
            expected = self.tokens.get(role, "")
            valid_format = len(fields) == 3 and fields[0] == "AUTH" and fields[1] == f"ROL:{role}" and fields[2] == f"TOKEN:{token}"
            if not valid_format or not expected or not hmac.compare_digest(token, expected):
                await self.send(writer, "AUTH:ERROR")
                return
            await self.send(writer, "AUTH:OK|SIMULADOR")
            self.clients[writer] = role
            await self.send(writer, self.telemetry())
            while raw := await reader.readline():
                command = raw.decode("ascii").strip()
                if role != "CONTROL":
                    await self.send(writer, "ERROR:ROL_SIN_PERMISO")
                elif command not in ("1", "0", "A"):
                    await self.send(writer, "ERROR:COMANDO_INVALIDO")
                elif not self.sensor_ok:
                    await self.send(writer, "ERROR:SENSOR_ERROR")
                else:
                    self.automatic = command == "A"
                    if not self.automatic:
                        self.relay = command == "1"
                        # Modo manual con vigencia, para regresar a AUTO aunque se pierda el Wi-Fi.
                        self.lease_until = time.monotonic() + 30
                    self.evaluate()
                    await self.send(writer, f"ACK:{command}")
                    await self.broadcast()
        except (OSError, UnicodeError, ValueError, asyncio.TimeoutError):
            pass
        finally:
            self.clients.pop(writer, None)
            writer.close()
            try:
                await writer.wait_closed()
            except OSError:
                pass
            if not self.automatic and "CONTROL" not in self.clients.values():
                self.automatic = True
                self.evaluate()
                await self.broadcast()

    async def tick(self):
        while True:
            await asyncio.sleep(2)
            if self.cycle:
                phase = int((time.monotonic() - self.started) // 12) % 3
                self.temperature = (15.0, 8.0, 3.0)[phase]
            if not self.automatic and time.monotonic() >= self.lease_until:
                self.automatic = True
            await self.broadcast()

    async def start(self, host="0.0.0.0", port=5000):
        self.server = await asyncio.start_server(self.handle, host, port, limit=512)
        self.ticker = asyncio.create_task(self.tick())
        return self.server.sockets[0].getsockname()[1]

    async def close(self):
        if self.ticker:
            self.ticker.cancel()
            try:
                await self.ticker
            except asyncio.CancelledError:
                pass
        for writer in list(self.clients):
            writer.close()
        if self.server:
            self.server.close()
            await self.server.wait_closed()


def load_tokens(path):
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
        tokens = [data.get(role, "") for role in ("CONTROL", "MONITOR")]
        if any(not isinstance(t, str) or not 16 <= len(t) <= 128 or
               any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-" for c in t) for t in tokens):
            raise ValueError("Archivo de claves inválido")
        if tokens[0] == tokens[1]:
            raise ValueError("Cada rol debe tener una clave diferente")
        return data
    data = {role: secrets.token_urlsafe(18) for role in ("CONTROL", "MONITOR")}
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")
    return data


async def run(args):
    tokens = load_tokens(Path(__file__).with_name("credentials.json"))
    server = AgroServer(tokens["CONTROL"], tokens["MONITOR"], args.temperature, args.humidity, args.cycle)
    server.sensor_ok = not args.sensor_error
    port = await server.start(args.host, args.port)
    addresses = sorted({info[4][0] for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET)})
    print("\nAGROALERT ARAUCANIA - SIMULADOR (no controla un Arduino)", flush=True)
    print(f"Puerto TCP: {port} | IP candidatas del PC: {', '.join(addresses)}", flush=True)
    print("Usa la IPv4 del adaptador Wi-Fi del PC; en el emulador Android usa 10.0.2.2.", flush=True)
    print(f"Clave del nodo CONTROL: {tokens['CONTROL']}", flush=True)
    print(f"Clave del nodo MONITOR: {tokens['MONITOR']}", flush=True)
    print("Estas claves son distintas de las contraseñas locales que creas en la app.", flush=True)
    print("Muestreo: 2 s | Manual: máximo 30 s, luego AUTO | Ctrl+C para terminar.\n", flush=True)
    try:
        async with server.server:
            await server.server.serve_forever()
    finally:
        await server.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=5000)
    parser.add_argument("--temperature", type=float, default=15.0)
    parser.add_argument("--humidity", type=float, default=65.0)
    parser.add_argument("--cycle", action="store_true", help="Cambia entre 15, 8 y 3 grados cada 12 segundos")
    parser.add_argument("--sensor-error", action="store_true", help="Simula sensor desconectado")
    args = parser.parse_args()
    if not math.isfinite(args.temperature) or not -50 <= args.temperature <= 80:
        parser.error("Temperatura fuera de rango")
    if not math.isfinite(args.humidity) or not 0 <= args.humidity <= 100:
        parser.error("Humedad fuera de rango")
    try:
        asyncio.run(run(args))
    except KeyboardInterrupt:
        print("\nSimulador detenido.")


if __name__ == "__main__":
    main()
