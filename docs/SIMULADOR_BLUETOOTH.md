# Probar el Bluetooth sin Arduino — dos celulares + Serial Bluetooth Terminal

El HC-05 es un enlace serie inalámbrico (perfil SPP): para la app da igual si al
otro lado está el módulo o **otro celular que hable SPP**. Con Serial Bluetooth
Terminal (SBT) en modo servidor puedes probar hoy todo el flujo Bluetooth de la
app — vinculación, permiso, conexión, telemetría y órdenes — sin hardware.

## Qué valida y qué no

| Se prueba con este método | No se prueba |
|---|---|
| Vinculación y permiso de Bluetooth (Android 12+) | El relé físico |
| Conexión RFCOMM/SPP con el mismo código que usará el HC-05 | El DHT11 real |
| Recepción y parseo de la telemetría | El clic del módulo de relé |
| Envío de `1`, `0`, `A` y confirmación `ACK:1/0/A` | Autonomía del nodo sin app |
| Caducidad de datos a los 7 s y bloqueo de órdenes | |

## Paso a paso

1. **Vincula los dos celulares entre sí**: Ajustes › Bluetooth en ambos,
   vincular nuevo dispositivo, aceptar el PIN que pida el sistema (una vez).
2. **Celular A (hace de Arduino)**: abre SBT y actívalo en modo servidor o de
   escucha (en la pantalla de dispositivos, conexión entrante). Déjalo abierto.
3. **Crea los macros**: en SBT, mantén presionados los botones M1…M7 y pega las
   tramas de la tabla siguiente (configura el fin de línea en LF, por defecto).
4. **Celular B (la app)**: AgroAlert → `admin` / `admin1` → **Buscar módulos
   emparejados** → toca el celular A.
5. En A, toca un macro de telemetría: en B aparece la lectura al instante.
   Toca otro macro cada pocos segundos y verás el semáforo cambiar en vivo.
6. En B toca **Encender**: en A llega el texto `1`. Responde tocando el macro
   `RELE:ENCENDIDO_MANUAL`: en B aparece «Orden confirmada · ACK:1».

## Tramas para los macros (copiar tal cual)

| Macro | Trama | Efecto en la app |
|---|---|---|
| M1 — Normal 15° | `TELEMETRIA\|TEMP:15.0C\|HUM:65%\|RELE:APAGADO\|MODO:AUTO\|ESTADO:OK` | Semáforo verde |
| M2 — Prehelada 8° | `TELEMETRIA\|TEMP:8.0C\|HUM:70%\|RELE:APAGADO\|MODO:AUTO\|ESTADO:PRE_HELADA` | Semáforo amarillo |
| M3 — Helada 3.5° | `TELEMETRIA\|TEMP:3.5C\|HUM:80%\|RELE:ACTIVADO\|MODO:AUTO\|ESTADO:HELADA_CRITICA` | Semáforo rojo |
| M4 — Respuesta encender | `RELE:ENCENDIDO_MANUAL` | «Orden confirmada · ACK:1» |
| M5 — Respuesta apagar | `RELE:APAGADO_MANUAL` | «Orden confirmada · ACK:0» |
| M6 — Respuesta automático | `MODO:AUTOMATICO` | «Orden confirmada · ACK:A» |
| M7 — Sensor en error | `TELEMETRIA\|TEMP:NA\|HUM:NA\|RELE:APAGADO\|MODO:AUTO\|ESTADO:SENSOR_ERROR\|SENSOR:ERROR` | Controles bloqueados |

En Ajustes de SBT activa «Enviar al conectar» con la línea
`=== AGROALERT ARAUCANIA CONECTADO ===` para imitar el saludo del firmware real.

Ojo: la app marca los datos como antiguos a los 7 segundos — mientras pruebas,
anda tocando un macro de telemetría seguido. Es exactamente el comportamiento
que tendrá con el Arduino detenido.

## Plan B para la demo

Si mañana el Arduino falla, este mismo montón de macros convierte el celular A
en «nodo de emergencia»: el profesor ve la app completa en vivo (panel, controles,
órdenes confirmadas) mientras revisas el hardware. Guarda los macros hoy.
