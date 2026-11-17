"""Ataque controlado contra el nodo AgroAlert (SOLO contra tu propio simulador).

Demuestra qué resiste el nodo y dónde quedan expuestos los datos, con evidencia
en vivo. Ideal para mostrar el análisis de riesgos al docente.

Uso:
  1. Inicia el nodo:   python simulator/server.py
  2. En otra terminal: python simulator/attack.py

Python 3.10+, sin dependencias. Ataca 127.0.0.1:5000 por defecto.
"""
import asyncio
import json
from pathlib import Path

HOST = "127.0.0.1"
PORT = 5000

results: list[tuple[str, bool, str]] = []


def record(name: str, defended: bool, detail: str) -> None:
    mark = "RESISTIDO " if defended else "EXPUESTO  "
    print(f"[{mark}] {name}: {detail}", flush=True)
    results.append((name, defended, detail))


async def open_conn(timeout: float = 5.0):
    return await asyncio.wait_for(asyncio.open_connection(HOST, PORT), timeout)


async def send(writer, text: str) -> None:
    writer.write((text + "\n").encode("ascii"))
    await writer.drain()


async def recv(reader, timeout: float = 3.0) -> str:
    try:
        return (await asyncio.wait_for(reader.readline(), timeout)).decode("ascii", "ignore").strip()
    except asyncio.TimeoutError:
        return "<sin respuesta>"


def load_tokens() -> tuple[str, str]:
    path = Path(__file__).with_name("credentials.json")
    data = json.loads(path.read_text(encoding="utf-8"))
    return data["CONTROL"], data["MONITOR"]


async def node_alive() -> bool:
    """Comprueba que el nodo sigue sirviendo clientes válidos tras cada ataque."""
    control, _ = load_tokens()
    reader, writer = await open_conn()
    try:
        await send(writer, f"AUTH|ROL:CONTROL|TOKEN:{control}")
        return (await recv(reader)) == "AUTH:OK|SIMULADOR"
    finally:
        writer.close()


async def t1_empty_token() -> None:
    reader, writer = await open_conn()
    try:
        await send(writer, "AUTH|ROL:CONTROL|TOKEN:")
        record("Acceso sin token", await recv(reader) == "AUTH:ERROR", "el nodo rechaza TOKEN vacío")
    finally:
        writer.close()


async def t2_wrong_token() -> None:
    reader, writer = await open_conn()
    try:
        await send(writer, "AUTH|ROL:CONTROL|TOKEN:" + "x" * 32)
        record("Fuerza bruta de un token", await recv(reader) == "AUTH:ERROR", "rechazo inmediato de clave errónea")
    finally:
        writer.close()


async def t3_cross_role_token() -> None:
    _, monitor = load_tokens()
    reader, writer = await open_conn()
    try:
        await send(writer, f"AUTH|ROL:CONTROL|TOKEN:{monitor}")
        record("Token MONITOR usado como CONTROL", await recv(reader) == "AUTH:ERROR",
               "cada rol solo entra con su propia clave")
    finally:
        writer.close()


async def t4_oversized_line() -> None:
    reader, writer = await open_conn()
    try:
        writer.write(b"A" * 600 + b"\n")
        await writer.drain()
        reply = await recv(reader)
        record("Línea de 600 bytes (desborde)", reply != "AUTH:OK" and await node_alive(),
               f"respuesta: {reply or '<conexión cerrada>'}; el nodo sigue vivo")
    finally:
        writer.close()


async def t5_monitor_privilege_escalation() -> None:
    # Conexión limpia autenticada como MONITOR que intenta una orden de CONTROL.
    _, monitor = load_tokens()
    reader, writer = await open_conn()
    try:
        await send(writer, f"AUTH|ROL:MONITOR|TOKEN:{monitor}")
        await recv(reader)
        await recv(reader)  # primera telemetría
        await send(writer, "1")
        record("Monitor intenta encender el relé", (await recv(reader)) == "ERROR:ROL_SIN_PERMISO",
               "escalada de privilegios bloqueada en el nodo")
    finally:
        writer.close()


async def t6_fake_command() -> None:
    control, _ = load_tokens()
    reader, writer = await open_conn()
    try:
        await send(writer, f"AUTH|ROL:CONTROL|TOKEN:{control}")
        await recv(reader)
        await recv(reader)
        await send(writer, "TELEMETRIA|TEMP:99C|HUM:100%|RELE:ACTIVADO|MODO:AUTO|ESTADO:OK|SENSOR:OK")
        record("Trama falsa inyectada como orden", (await recv(reader)) == "ERROR:COMANDO_INVALIDO",
               "el nodo no acepta telemetría inventada como comando")
    finally:
        writer.close()


async def t7_silent_connection() -> None:
    reader, writer = await open_conn()
    try:
        reply = await recv(reader, timeout=7)
        record("Conexión muda (slowloris)", reply == "", "el nodo corta sola la conexión en ~5 s")
    finally:
        writer.close()


async def t8_flood() -> None:
    conns = [await open_conn() for _ in range(40)]
    try:
        record("Ráfaga de 40 conexiones sin auth", await node_alive(),
               "el nodo sobrevive y sigue aceptando clientes válidos (sin límite de conexiones: mejora pendiente)")
    finally:
        for _, w in conns:
            w.close()


async def t9_token_replay() -> None:
    # Simula olfatear la red: el token viaja en TEXTO PLANO y se reutiliza tal cual.
    control, _ = load_tokens()
    reader, writer = await open_conn()
    try:
        await send(writer, f"AUTH|ROL:CONTROL|TOKEN:{control}")
        if (await recv(reader)) != "AUTH:OK|SIMULADOR":
            record("Repetición de token olfateado", True, "no se pudo reproducir (revisa credentials.json)")
            return
        await recv(reader)  # telemetría inicial
        await send(writer, "1")
        ack = await recv(reader)
        tele = await recv(reader)
        record("Token capturado y reutilizado (MITM)", False,
               f"{ack} · {tele}: el token viaja SIN cifrado y es reutilizable; quien lo olfatee CONTROLA EL RIEGO")
        await send(writer, "A")  # dejar el nodo en AUTO
    finally:
        writer.close()


async def main() -> None:
    print(f"Ataque controlado a {HOST}:{PORT} — SOLO úsalo contra tu propio nodo.\n", flush=True)
    if not await node_alive():
        print("El nodo no responde. Inicia primero:  python simulator/server.py", flush=True)
        return
    await t1_empty_token()
    await t2_wrong_token()
    await t3_cross_role_token()
    await t4_oversized_line()
    await t5_monitor_privilege_escalation()
    await t6_fake_command()
    await t7_silent_connection()
    await t8_flood()
    await t9_token_replay()

    held = sum(1 for _, ok, _ in results if ok)
    print(f"\nResultado: el nodo resistió {held} de {len(results)} ataques.", flush=True)
    print("Debilidad demostrada: AUTH y telemetría viajan en TEXTO PLANO (sin TLS).", flush=True)
    print("Mitigación para la demo: usa WPA2, regenera claves (borra credentials.json)", flush=True)
    print("y no pruebes en redes públicas o de invitados.", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
