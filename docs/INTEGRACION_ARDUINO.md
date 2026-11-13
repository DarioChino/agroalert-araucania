# Integración con Arduino — de prototipo a nodo real

La app ya habla el protocolo del contrato [PROTOCOLO_WIFI.md](PROTOCOLO_WIFI.md)
contra el simulador. Este documento describe **cómo llevar ese mismo protocolo a
hardware físico**, con tres rutas posibles y las decisiones que hay que tomar.
Nada de esto está implementado todavía: es la guía para cuando se compre el
módulo Wi-Fi.

## Lo que la app ya garantiza (no cambia)

- TCP a la IP y puerto que escribe la persona usuaria (por omisión **5000**).
- Autenticación de primera línea `AUTH|ROL:CONTROL|TOKEN:<clave>` o `MONITOR`
  con clave de 16–128 caracteres; espera `AUTH:OK` y cierra ante `AUTH:ERROR`.
- Lectura de telemetría cada 2 s; invalidez del enlace tras 7 s sin datos.
- Órdenes `1`, `0`, `A` con confirmación `ACK:1/0/A`; si no llega ACK en 5 s,
  desconecta y exige reconectar (no reenvía órdenes).
- Acepta la trama de 6 campos (`ESTADO` + `SENSOR:OK|ERROR`) y la compatible de
  5 campos sin `SENSOR`.

El firmware físico **debe reproducir exactamente ese contrato**. El simulador
`simulator/server.py` es la referencia ejecutable: cualquier firmware puede
validarse contra la app usando el simulador como juez de empates.

## Ruta A — ESP32 como nodo completo (recomendada)

Una placa **ESP32 DevKit** reemplaza por completo al Uno: tiene Wi-Fi, GPIO
suficientes y ADC. Es la ruta con menos piezas y menos puntos de falla.

```text
Celulares → red Wi-Fi de la casa → ESP32 (servidor TCP en :5000)
                                        ├── DHT11 en GPIO 4 (datos)
                                        ├── LEDs D3/D4/D5 equivalentes → GPIO 25/26/27
                                        └── Relé (módulo Active LOW) → GPIO 32
```

| Ventaja | Detalle |
|---|---|
| Sin puente serie | El protocolo corre directo en la placa; nada que traducir. |
| Una sola fuente de verdad | La lógica térmica y el servidor viven en el mismo programa. |
| Alimentación simple | El DevKit se alimenta por USB del cargador del nodo. |
| Costo | Una placa (~6–10 USD) reemplaza Uno + módulo Wi-Fi. |

**Atención con el relé Active LOW:** inicializar el GPIO en `HIGH` *antes* de
habilitar la salida, y evaluar la lógica térmica en el `loop()` sin depender de
la red (autonomía exigida por el informe).

**Atención con el DHT11:** el DHT11 solo entrega una lectura nueva cada ~1–2 s;
muestrear como máximo cada 2 s y ante lectura inválida reportar
`TELEMETRIA|TEMP:NA|HUM:NA|RELE:APAGADO|MODO:AUTO|ESTADO:SENSOR_ERROR|SENSOR:ERROR`
y **no** reevaluar el relé con el dato antiguo (el error de fail-safe del
firmware web está descrito en [REQUISITOS.md](REQUISITOS.md)).

## Ruta B — Uno + ESP-01/ESP8266 como puente serie

Si el montaje original con el Uno debe conservarse tal cual, el módulo Wi-Fi
solo actúa de puente: implementa el servidor TCP y traduce tramas al serie.

```text
Celulares → Wi-Fi → ESP-01 (TCP :5000) → serie 9600/115200 → Uno → sensor/LEDs/relé
```

- El ESP-01 corre firmware propio (no solo AT) que hable `AUTH`/`TELEMETRIA`/
  `ACK` con la app y reenvíe los comandos al Uno.
- El Uno responde por serie con la trama de telemetría y ejecuta `1/0/A`.
- El puente debe **validar rol y permisos** antes de reenviar una orden
  (`ERROR:ROL_SIN_PERMISO` lo responde el puente, no el Uno).
- Necesita adaptador de nivel lógico o placa con 3,3 V: el ESP-01 **no es 5 V
  tolerante** en sus pines.
- Desventajas: dos programas que mantener sincronizados, alimentación del
  ESP-01 (3,3 V con picos de corriente; regulador dedicado) y un punto de
  falla serie más. Se recomienda solo si el Uno es requisito del docente.

## Ruta C — Uno + shield/variante ESP32 como esclavo

Variante de la B usando un ESP32 como puente (más robusto que el ESP-01,
tolerante a más corriente). Mismas reglas del puente. Cae de precio el problema
de alimentación; sigue existiendo el doble firmware.

## Qué implementar en el firmware (cualquier ruta)

1. **Servidor TCP en el puerto 5000** con al menos 2 clientes simultáneos
   (Control y Monitor a la vez). En ESP32: `WiFiServer` + `WiFiClient`.
2. **AUTH** exactamente como el contrato; claves por rol distintas, guardadas
   en el firmware o en su NVS, nunca en el APK ni en el repositorio.
3. **Telemetría cada 2 s a todos los clientes**, formato del contrato,
   `RELE`, `MODO`, `ESTADO`, `SENSOR` coherentes entre sí.
4. **Órdenes**: aplicar, responder `ACK:x` y difundir el nuevo estado a todos.
5. **Autonomía**: la lógica térmica corre siempre; modo manual vence en 30 s;
   al perder el último cliente Control, volver a AUTO.
6. **Sensor en error**: `TEMP:NA`, `HUM:NA`, `SENSOR:ERROR`, relé apagado y
   **sin** sobrescribir el LED amarillo con datos antiguos.
7. Inicializar el relé Active LOW en `HIGH` antes de habilitar salidas.

## Plan de validación en banco (antes de conectar al huerto)

| # | Prueba | Criterio |
|---|---|---|
| 1 | Firmware vs simulador | Mismos mensajes que `server.py` línea a línea. |
| 2 | Dos celulares | Ambos ven el mismo estado; Monitor sin permiso de orden. |
| 3 | Caída de red | La app avisa enlace perdido en ~7–8 s y bloquea órdenes. |
| 4 | Modo manual | Vence en 30 s y vuelve a AUTO sin app conectada. |
| 5 | Sensor desconectado | `SENSOR:ERROR`, relé apagado, controles bloqueados. |
| 6 | Relé Active LOW | No chispea al encender la placa. |
| 7 | 24 h continuas | Sin fugas de memoria ni caída del socket en el módulo. |

Los pasos 2–5 ya están automatizados en el simulador; el firmware nuevo se
puede comparar contra él ejecutando ambos y alternando la IP en la app.

## Pendientes por decidir con el docente

- Modelo exacto del módulo/placa (Ruta A recomendada).
- Si el Uno se conserva como controlador (Ruta B/C) o se jubila (Ruta A).
- Alimentación del montaje real (cargador USB vs fuente 5 V del relé).
- Alcance del hack: la demostración académica puede correr con el simulador;
  la guía vale para la iteración siguiente.
