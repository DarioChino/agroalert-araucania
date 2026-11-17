# Rúbrica de la demo (U3) — evidencia por criterio y guion

Fuente: `TI3042_U3_EF_GUÍA.docx` (lista de cotejo, 100 puntos). La guía pide un
prototipo Arduino con sensores y actuadores **controlado y monitoreado desde
Android por Bluetooth o Wi-Fi**, con envío seguro e integridad de datos. Nuestro
prototipo cumple con **ambos transportes** (Bluetooth SPP al Arduino y Wi-Fi/TCP
al nodo o simulador) y ambos se muestran con el mismo protocolo.

## 1. Mapa rúbrica → evidencia

### Grupo 3.1.1 — Seguridad del envío (25 pts)

| # | Criterio | Pts | Evidencia en el proyecto | Cómo mostrarlo en vivo |
|---|---|---|---|---|
| 1 | Instrucciones con estándares de envío seguro | 10 | Parser estricto en `Protocol.kt`: solo acepta la trama de 6 campos exactos, descarta duplicados, campos extra, tamaños > 512 bytes y valores fuera de rango (T° −50..80 °C, HR 0..100 %). Sanitización en el firmware: `procesarComando()` solo acepta `1`, `0`, `A` y **descarta todo lo demás** (ruido/inyección). Contrato formal: `docs/PROTOCOLO_WIFI.md`. Pruebas: 8 unitarias en `ProtocolTest`. | Con el segundo celular (Serial Bluetooth Terminal) **inyectar una trama malformada** (`TELEMETRIA\|TEMP:999C\|…` o un campo duplicado): la app la **rechaza** y el panel marca "sin datos actuales" en vez de mostrar basura. |
| 2 | Integridad de lo enviado y recibido | 10 | Triple control: (a) el `ESTADO` de la trama debe coincidir con el que la app calcula del valor `TEMP` — una trama mentirosa se rechaza; (b) cada orden exige **ACK** del nodo (`ACK:1/0/A`), sin cambio optimista local; (c) los datos **caducan a los 7 s** y los controles se bloquean con datos viejos. | Inyectar por SBT una trama con `ESTADO:OK` pero `TEMP:3.5C` (inconsistente): la app la rechaza. Y detener el envío de tramas: a los 7 s aparece «dato antiguo» y se bloquea el control. |
| 3 | Cifrado o medidas de seguridad en la serial | 5 | El HC-05 (SPP) no cifra el enlace — es una limitación del hardware disponible. Medidas compensatorias: **PIN de emparejamiento personalizado vía AT**, enlace punto a punto con **un solo cliente** y alcance ~10 m, **token de sesión** en el canal Wi-Fi (`AUTH\|ROL:…\|TOKEN:…`), seguridad por capas en la app (PBKDF2, RBAC, bloqueo) y sanitización de entrada. Ruta a TLS documentada con ESP32 (`docs/INTEGRACION_ARDUINO.md`, etapa 2). | Decir la frase honesta: «el enlace SPP no cifra; la rúbrica pide cifrado **o** medidas adecuadas — cumplimos con el paquete: PIN privado por AT, un solo cliente punto a punto, token de sesión, hashing PBKDF2, roles y bloqueo. El cifrado TLS llega con el módulo Wi-Fi (etapa 2 documentada)». |

### Grupo 3.1.2 — App → microcontrolador (30 pts)

| # | Criterio | Pts | Evidencia | Cómo mostrarlo |
|---|---|---|---|---|
| 4 | Enviar instrucciones serial desde la app | 10 | `NodeConnection.kt` envía `1` (encender), `0` (apagar), `A` (automático) + `\n` por SPP (y por TCP en modo Wi-Fi). Botones solo para rol Control. | Tocar **Encender** en la app: se escucha el **CLIC** del relé y llega la confirmación. |
| 5 | Recibir instrucciones serial en la app | 10 | Bucle de lectura unificado TCP+SPP; el parser convierte las tramas en telemetría visible (temperatura, humedad, relé, modo, estado). | El panel se actualiza **cada 2 segundos** con la trama que llega del Arduino. |
| 6 | Sensores y actuadores responden | 10 | DHT11 → semáforo térmico; relé → bomba; LED tricolor D3/D4/D5. | **Soplar o enfriar el DHT11** hasta ≤ 5 °C: LED rojo + relé activado solos (modo automático). Tocar Encender/Apagar: clic + LED. |

### Grupo 3.1.3 — Microcontrolador → app y estabilidad (25 pts)

| # | Criterio | Pts | Evidencia | Cómo mostrarlo |
|---|---|---|---|---|
| 7 | Enviar instrucciones desde el µc a la app | 10 | El firmware transmite cada 2 s `TELEMETRIA\|TEMP:…\|HUM:…\|RELE:…\|MODO:…\|ESTADO:…`, el saludo `=== AGROALERT ARAUCANIA CONECTADO ===` y las respuestas `RELE:ENCENDIDO_MANUAL` / `RELE:APAGADO_MANUAL` / `MODO:AUTOMATICO`. | Se ve en la app traducido («Orden confirmada · ACK:1») y en crudo en SBT. |
| 8 | Recibir instrucciones en el µc desde la app | 10 | `bluetooth.available()` → `procesarComando()` en el firmware con sanitización (default descarta). | Al tocar un botón, el Serial Monitor del PC imprime `[REMOTO] Rele forzado a ENCENDIDO`. |
| 9 | Estabilidad de la bidireccional | 5 | Indicador en vivo «● En línea», ventana de frescura de 7 s con marca «dato antiguo», y diseño **sin reenvío de órdenes tras una desconexión** (evita órdenes duplicadas). Reconexión manual simple desde la pantalla de conexión. | Detener el envío (apagar el Arduino) y mostrar cómo la app **declara los datos viejos** y bloquea el control en vez de operar a ciegas. |

### Grupo 3.1.4 — Seguridad de la conexión y los datos (20 pts)

| # | Criterio | Pts | Evidencia | Cómo mostrarlo |
|---|---|---|---|---|
| 10 | Estándares ISO de seguridad en la conexión | 10 | Mapeo a principios **ISO/IEC 27001**: mínimo privilegio (RBAC Control/Monitor), gestión de credenciales (PBKDF2 + bloqueo), integridad (parser + ACK), registro de eventos (bitácora). Verificación móvil según **OWASP MASVS**. Evidencia empírica: `simulator/attack.py` — **8 de 9 ataques resistidos** (el token plano reutilizado encendió el relé: hallazgo corregido con ACK + roles). | Abrir `attack.py` y leer el resultado de la auditoría; mencionar el mapeo ISO 27001 criterio a criterio. |
| 11 | Protección de datos personales | 5 | **Minimización**: la app solo guarda usuario y rol — nada de datos personales ni ubicación. **Sin nube**: los datos no salen de la red local, no hay terceros. `allowBackup=false` en el AndroidManifest (los datos no van a backups). Bitácora guardada solo en el teléfono. | Abrir el AndroidManifest y mostrar `allowBackup=false`; explicar que las tramas no contienen datos personales. |
| 12 | Gestión segura de credenciales | 5 | Contraseñas con **PBKDF2-HMAC-SHA256, 120.000 iteraciones y sal aleatoria** (nunca en texto plano), **bloqueo de 60 s tras 3 intentos fallidos** persistente al cerrar la app, roles separados (Monitor es solo lectura). | Intentar 3 claves mal → bloqueo en pantalla; mostrar `Credentials.kt` y `CredentialsTest`. |

## 2. Guion de demo (10 minutos, orden de la rúbrica)

1. **(2 min) Hardware:** nodo armado según `docs/CABLEADO_ARDUINO.md` (o la guía 3D). Relé sin carga AC, semáforo visible. *(contexto 3.1.2.6)*
2. **(1 min) Login seguro:** entrar como `admin1` → rol Control. Mencionar PBKDF2 y bloqueo. *(3.1.4.12)*
3. **(2 min) Conexión Bluetooth + panel en vivo:** vincular HC-05, conectar en la app, ver tramas cada 2 s. *(3.1.2.5, 3.1.3.7, 3.1.3.9)*
4. **(2 min) Órdenes con confirmación:** Encender → CLIC + LED + «Orden confirmada · ACK:1». Apagar. Automático. *(3.1.2.4, 3.1.3.8, 3.1.3.9)*
5. **(1 min) Sensor → actuador:** soplar el DHT11 hasta ≤ 5 °C → rojo + relé solos. *(3.1.2.6)*
6. **(1 min) Prueba de integridad:** con SBT inyectar trama malformada o mentirosa → la app la rechaza y caduca a los 7 s. *(3.1.1.1, 3.1.1.2, 3.1.3.9)*
7. **(1 min) Paquete de seguridad:** frase del criterio 3 + `attack.py` 8/9 + `allowBackup=false`. *(3.1.1.3, 3.1.4.10, 3.1.4.11)*

## 3. Checklist previo (la noche anterior)

- [ ] APK 1.1.0 instalado en el celular de la demo (o depuración desde el repo).
- [ ] Firmware `firmware/AgroAlert_Uno_HC05.ino` subido al Uno (compilación ya verificada: 31 % flash).
- [ ] HC-05 emparejado con el celular y probado con SBT (`1` → clic del relé).
- [ ] Opcional (criterio 3): cambiar el PIN por defecto del HC-05 con comandos AT
      (`AT+PSWD="481516"` en modo AT, manteniendo el botón al alimentar el módulo;
      si el módulo no entra en modo AT, queda como medida declarada en el guion).
- [ ] Plan B cargado: `docs/SIMULADOR_BLUETOOTH.md` (dos celulares) y `simulator/server.py` (Wi-Fi).
- [ ] Segundo celular con SBT y los macros de telemetría listos (para las inyecciones de los criterios 1 y 2).
