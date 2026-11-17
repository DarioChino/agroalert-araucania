# Mapa de cableado del Arduino Uno R3 y guía de compilación

Nodo oficial del proyecto (el de la [web](https://proyecto-iot.momentumspace.cl/) y
las fotos de laboratorio). El sketch está en
[`firmware/AgroAlert_Uno_HC05.ino`](../firmware/AgroAlert_Uno_HC05.ino) y **ya fue
verificado compilando para Uno**: 10.128 bytes (31 % de la flash), 456 bytes de RAM.

## 1. Vista general

```
                    PROTOBOARD
   Arduino Uno R3   riel rojo (+5V) · riel azul (GND)
   ┌───────────┐
   │  5V  GND  │────► rieles de alimentación
   │           │
   │  D2  ─────┼────► DHT11 (datos)          sensor ambiental
   │  D3  ─────┼────► 220Ω ► LED verde       T° > 10 °C
   │  D4  ─────┼────► 220Ω ► LED amarillo    prehelada 5-10 °C
   │  D5  ─────┼────► 220Ω ► LED rojo        helada ≤ 5 °C
   │  D8  ─────┼────► IN del relé            bomba (Active LOW)
   │  D10 ─────┼────► TXD del HC-05          Arduino recibe órdenes
   │  D11 ─────┼────► divisor ► RXD HC-05    Arduino envía telemetría
   │  USB      │────► PC (alimentación + monitor serie)
   └───────────┘
```

## 2. Mapeo oficial pin a pin

### Paso previo: rieles de la protoboard

| Desde (Arduino) | Hasta | Color sugerido |
|---|---|---|
| Pin **5V** | Riel rojo (+) de la protoboard | Rojo |
| Pin **GND** | Riel azul (−) de la protoboard | Negro |

Todos los módulos se alimentan desde esos dos rieles, nunca desde pines sueltos.

### Sensor DHT11 (temperatura y humedad)

| Pin del módulo | Conexión | Detalle |
|---|---|---|
| **S** (datos) | **D2** del Arduino | Bus digital One-Wire |
| **VCC** (+) | Riel 5V | |
| **GND** (−) | Riel GND | |

Con el DHT11 de 4 pines «pelado» (sin placa), añadir resistencia **pull-up de
10 kΩ** entre el pin de datos y 5V; el módulo de 3 pines ya la trae integrada.
El cable de datos debe quedar corto (< 30 cm) para lecturas estables.

### Semáforo LED tricolor (señalización en poste)

| LED | Conexión | Estado que señala |
|---|---|---|
| **Verde** | D3 → resistencia 220 Ω → ánodo (+); cátodo (−) → riel GND | T° > 10 °C |
| **Amarillo** | D4 → resistencia 220 Ω → ánodo (+); cátodo (−) → riel GND | 5 °C < T° ≤ 10 °C |
| **Rojo** | D5 → resistencia 220 Ω → ánodo (+); cátodo (−) → riel GND | T° ≤ 5 °C |

La pata **larga** del LED es el ánodo (+); la corta (y el borde plano del
cuerpo) es el cátodo (−). Sin las resistencias los LEDs se queman.

### Módulo relé 5V — bomba de aspersión (Active LOW)

| Pin del módulo | Conexión | Detalle |
|---|---|---|
| **IN** (disparo) | **D8** del Arduino | **LOW activa la bomba, HIGH la apaga** |
| **VCC** (DC+) | Riel 5V | |
| **GND** (DC−) | Riel GND | |
| Terminales de carga (NO/COM/NC) | **VACÍOS** | Seguridad OT en laboratorio: nunca conectar 220 V en el banco |

El firmware arranca con el pin en **HIGH** (relé abierto): si la app se
desconecta o el Arduino se reinicia, la bomba queda apagada (fail-safe). Al
activarse se escucha el «CLIC» mecánico del relé y enciende su LED testigo.

### Bluetooth HC-05 (enlace con la app)

| Pin del módulo | Conexión | Detalle |
|---|---|---|
| **VCC** | Riel 5V | El módulo de placa ZS-040 acepta 5V (tiene regulador) |
| **GND** | Riel GND | |
| **TXD** | **D10** (SoftwareSerial RX) | 3.3 V llegando al Arduino: lectura directa, sin adaptar |
| **RXD** | **D11** (SoftwareSerial TX) **vía divisor de tensión** | El Arduino manda 5 V; el RXD del HC-05 es 3.3 V: hay que adaptar |
| STATE / EN | Desconectados | |

**Divisor de tensión obligatorio en RXD** (requisito OT de la guía oficial:
«adecuación de niveles lógicos»):

```
D11 ───[ R1 = 1 kΩ ]───┬───► RXD del HC-05
                       │
                  [ R2 = 2 kΩ ]
                       │
                      GND
```

5 V × 2 kΩ/(1 kΩ+2 kΩ) ≈ **3,3 V** en RXD. Ojo al cruce: TX del Arduino va al
RX del módulo y viceversa — cruzado, nunca directo.

Como el HC-05 va por **SoftwareSerial (D10/D11)** y no por los pines 0/1,
**se puede compilar y subir por USB con el módulo conectado** sin conflicto serie.

## 3. Verificación después de cablear

Al alimentar por USB (sin compilar nada aún), el sketch viejo o el nuevo deja
esta secuencia observable:

1. LED **verde** del semáforo encendido (estado inicial) y relé silencioso.
2. LED del HC-05 **parpadeando rápido** (~2 Hz): modo esclavo esperando conexión.
3. Si algo no coincide, revisar esa rama del mapa antes de continuar.

## 4. Guía de compilación y subida (Arduino IDE)

1. **Instalar Arduino IDE 2.x** desde [arduino.cc](https://www.arduino.cc/en/software/).
   La placa **Arduino Uno** viene incluida: no hay que instalar paquetes de placas.
2. **Conectar el Uno por USB** al PC. En *Tools › Board* elegir **Arduino Uno**;
   en *Tools › Port* elegir el **COM** que aparezca al enchufar.
   - Si no aparece ningún puerto: instalar el driver **CH340** (los Uno clónicos
     lo usan) y reconectar.
3. **Instalar la librería del sensor**: *Tools › Manage Libraries* (Ctrl+Shift+I),
   buscar **«DHT sensor library»** (de Adafruit) → *Install* → aceptar instalar
   también **«Adafruit Unified Sensor»** que pide como dependencia.
4. **Abrir el sketch** `firmware/AgroAlert_Uno_HC05.ino` (descargarlo desde el
   repositorio o clonarlo).
5. **Compilar**: botón ✓ (*Verify*). Resultado esperado, ya verificado:
   `10.128 bytes (31%) del espacio de programa · 456 bytes (22%) de memoria dinámica`.
6. **Subir**: botón → (*Upload*). El LED TX/RX de la placa parpadea y al terminar
   el semáforo vuelve al verde (fail-safe de arranque).
7. **Abrir el Monitor Serie** (lupa arriba a la derecha) a **9600 baudios**:
   debe aparecer el banner `AGROALERT ARAUCANIA - SISTEMA IOT ANTI-HELADAS` y
   una trama cada 2 segundos:
   `TELEMETRIA|TEMP:15.0C|HUM:65%|RELE:APAGADO|MODO:AUTO|ESTADO:OK`
8. **Probar órdenes desde el PC**: escribir `1` y Enter en el Monitor Serie →
   clic del relé + LED rojo + respuesta `RELE:ENCENDIDO_MANUAL`. Con `0` vuelve,
   con `A` retorna a automático. Igual que hará la app por Bluetooth.
9. **Probar el sensor frío**: soplar o acercar hielo al DHT11 hasta bajar de
   5 °C → semáforo rojo + relé activado solo (modo automático). Desconectar el
   cable de datos del DHT11 → mensaje `[ADVERTENCIA OT] Sensor DHT11 no responde`
   sin que el sistema se cuelgue (fail-safe).
10. **Pasar a Bluetooth**: vincular el HC-05 en Ajustes (PIN 1234/0000) y
    seguir [BLUETOOTH.md](BLUETOOTH.md) para la demo con la app.

### Alternativa: compilar y subir desde este PC

Con el Arduino conectado a este computador se puede compilar y subir sin abrir
el IDE: avisar y se hace con `list_boards` (detecta el COM) y `upload_sketch`
(compila y sube el mismo sketch verificado).

## 5. Errores típicos y su arreglo

| Síntoma | Causa probable | Arreglo |
|---|---|---|
| No compila: `DHT.h: No such file` | Falta la librería | Paso 3 de la guía |
| No aparece puerto COM | Driver del USB-serial | Instalar CH340 y reconectar |
| Sube pero no llega trama al PC | Monitor en otro baudaje | Fijar 9600 en el Monitor Serie |
| Tramas al PC pero no por Bluetooth | TXD/RXD derechos en vez de cruzados, o divisor faltante | Cruzar TX↔RX y poner el divisor del §HC-05 |
| Relé activado de inmediato al arrancar | Pin flotante: IN sin conectar a D8 | Verificar cable IN → D8 |
| Lecturas `nan` / advertencia OT | Cable de datos suelto o sin pull-up (sensor pelado) | Reasentar en D2 y revisar pull-up de 10 kΩ |
