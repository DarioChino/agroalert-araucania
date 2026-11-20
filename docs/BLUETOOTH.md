# Demo Bluetooth en 30 minutos — Arduino Uno + HC-05

La app ahora conecta **directamente por Bluetooth** con el Arduino documentado en
[proyecto-iot.momentumspace.cl](https://proyecto-iot.momentumspace.cl/). Sin PC,
sin `server.py`, sin IPs, sin firewall. La ruta Wi-Fi/simulador sigue disponible
para pruebas sin hardware.

## Minuto 0-15: armar el nodo

Sigue la guía fotográfica oficial de la web (sección «Guía de Montaje Físico»):

| Componente | Pin del módulo | Pin Arduino Uno |
|---|---|---|
| Alimentación protoboard | 5V / GND | 5V / GND |
| Sensor DHT11 | S (datos) | D2 |
| LED verde (+220 Ω) | ánodo | D3 |
| LED amarillo (+220 Ω) | ánodo | D4 |
| LED rojo (+220 Ω) | ánodo | D5 |
| Módulo relé 5V (Active LOW) | IN | D8 |
| Bluetooth HC-05 | TXD | D10 |
| Bluetooth HC-05 | RXD | D11 (con divisor 5V→3,3V) |

**Importante:** el RX del HC-05 es de 3,3 V y el TX de Arduino da 5 V. Pon un
divisor de 1 kΩ (D11 → RX) y 2 kΩ (RX → GND), o un adaptador HC-05 con
nivelador. Sin eso el módulo puede recibir basura o quemarse. RXD se refiere al
RX **del HC-05**, que va al pin D10 de Arduino (TX del `SoftwareSerial`).

Sube `firmware/AgroAlert_Uno_HC05.ino` **v1.4** (Arduino IDE: seleccionar
«Arduino Uno» y el puerto COM; cargar). Los terminales AC del relé quedan
vacíos, como exige el protocolo de laboratorio.

### Baud del HC-05 (causa del fallo anterior)

Los HC-05 salen de fábrica a **38400** o **9600** según el lote. El sketch v1.3
estaba fijo en 9600: con un módulo a 38400 el celular conectaba pero nunca
recibía telemetría. El sketch v1.4 prueba ambos bauds automáticamente: envía
`?`, la app 1.4 responde `!`, y el Arduino se queda en el baud correcto antes de
emitir telemetría. No hay que configurar nada a mano.

## Minuto 15-20: emparejar el HC-05 (UNA sola vez por celular)

1. Con el Arduino alimentado por USB, el LED del HC-05 parpadea rápido.
2. En la app: **Buscar módulos emparejados**. Si el Bluetooth está apagado,
   Android pregunta «¿Activar Bluetooth?» — acepta y la lista sigue sola.
3. Si no hay nada vinculado, el botón **Abrir Ajustes de Bluetooth para
   vincular** te lleva directo: selecciona «HC-05», acepta el PIN **1234**
   (o **0000** según el módulo) y vuelve a la app.

Ese PIN lo pide **Android en Ajustes**, una única vez al vincular. La app
nunca vuelve a pedirlo: después de eso, conectar es tocar el HC-05 en la lista.

## Minuto 20-25: conectar la app

1. Abre AgroAlert e inicia sesión con `admin` / `admin1`, o con cualquier otra
   cuenta creada desde la propia pantalla de acceso.
2. En «01 / Conexión», toca **Buscar módulos emparejados** y concede el permiso
   de Bluetooth si lo pide.
3. Toca **HC-05 · XX:XX:...**: el panel abre solo cuando llegan lecturas.

## Minuto 25-30: verificar

- El panel muestra temperatura, humedad y el semáforo en vivo (cada 2 s).
- En cualquiera de los dos celulares: **Encender** → clic del relé + LED rojo;
  el nodo responde `RELE:ENCENDIDO_MANUAL` y la app la traduce a `ACK:1`.
- **Volver al modo automático** → `MODO:AUTOMATICO` → `ACK:A`.
- El otro celular refleja el mismo estado; ambas cuentas tienen los botones.
- Calienta el DHT11 con los dedos (baja ~0,1 °C por segundo) o sopla frío para
  cruzar los umbrales 10 °C / 5 °C y ver el semáforo completo.

## Si algo falla

| Síntoma | Causa probable | Arreglo |
|---|---|---|
| No aparece el HC-05 en la lista | No está emparejado | Emparejar primero en Ajustes › Bluetooth |
| «No se pudo conectar con el HC-05» | El Arduino está apagado u otro equipo lo tiene ocupado | Apagar y encender el Arduino; desvincular de otros celulares |
| «El HC-05 no envió lecturas: revisa el baud…» | Sketch v1.3 o anterior, o RX sin divisor | Subir el sketch v1.4 y poner el divisor 1 kΩ/2 kΩ en RX |
| Conecta pero «Sin datos válidos durante 7 segundos» | El sketch no está corriendo | Revisa el monitor serie: debe imprimir la telemetría por USB |
| «Controles bloqueados · revisa el sensor» | DHT11 suelto | Reconecta el cable de datos en D2 |
| Permiso de Bluetooth denegado | Android 12+ | Ajustes › Apps › AgroAlert › Permisos › Bluetooth |

## Nota de seguridad

El enlace Bluetooth SPP no usa el intercambio de tokens del protocolo Wi-Fi:
la autenticación es el emparejamiento del HC-05 (PIN), y el nodo responde a
cualquier celular ya emparejado. El firmware envía sus respuestas propias
(`RELE:ENCENDIDO_MANUAL`, `MODO:AUTOMATICO`), que la app traduce a `ACK:1`,
`ACK:0` y `ACK:A`; la autorización depende de la cuenta que inició sesión
(PBKDF2 local hoy, Firebase Auth al activarlo) y del emparejamiento del HC-05.
Ya no hay perfiles Control/Monitor: toda cuenta puede controlar.
