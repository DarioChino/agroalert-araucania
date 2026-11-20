# Arquitectura combinada — AgroAlert Araucanía

**Autores:** Darío Candia · Sebastián Torres — TI3042, INACAP Sede Temuco.
**Fecha:** 17-11-2026.

Este documento integra el trabajo de ambos integrantes del equipo en una sola
arquitectura con dos etapas, y deja por escrito el porqué del cambio de
transporte Wi-Fi → Bluetooth del prototipo de campo.

## 1. Dos repos, un proyecto

| Repositorio | Rol en el proyecto | Autor |
|---|---|---|
| [`agroalert-araucania`](https://github.com/DarioChino/agroalert-araucania) (este) | **Capa de conexión y seguridad real**: cliente TCP/SPP que habla con el nodo, protocolo `TELEMETRIA`, PBKDF2, bloqueo por intentos, política de claves, caducidad de datos, simuladores y auditoría de seguridad (`attack.py`). | Darío |
| [`IOT-APP-ANDROID-STUDIO-AGROALERT`](https://github.com/sebatmail/IOT-APP-ANDROID-STUDIO-AGROALERT) | **Capa de presentación y evolución**: interfaz Jetpack Compose + MVVM, bitácora de campo, `EncryptedSharedPreferences`, y firmware ESP32-WROOM-32 (Wi-Fi + Firebase RTDB) que define la segunda etapa. | Sebastián |

Ningún trabajo se descarta: las dos aplicaciones conviven en el mismo teléfono
(paquetes distintos, `cl.agroalert.araucania` y `com.agroalert.araucania`) y la
fusión de código está planificada en la hoja de ruta (§5).

## 2. Dos etapas de hardware

**Etapa 1 — Prototipo de campo (implantada).** El hardware efectivamente
disponible y documentado en la [web oficial del proyecto](https://proyecto-iot.momentumspace.cl/)
es un **Arduino Uno R3 + módulo HC-05**. El HC-05 es un enlace serie
inalámbrico (perfil Bluetooth SPP; el firmware v1.4 negocia 38400/9600 bps):
la app se conecta con el UUID
estándar del perfil (`00001101-…`) y habla el mismo protocolo del informe,
trama a trama:

```
TELEMETRIA|TEMP:8.0C|HUM:70%|RELE:APAGADO|MODO:AUTO|ESTADO:PRE_HELADA   → nodo → app
1 / 0 / A                                                              → app → nodo
RELE:ENCENDIDO_MANUAL / RELE:APAGADO_MANUAL / MODO:AUTOMATICO           → nodo → app
```

El transporte Wi-Fi/TCP **se conserva**: la misma aplicación sigue conectando
por red al nodo o al simulador, con el mismo protocolo. El cambio agregó un
transporte; no reemplazó ninguno.

**Etapa 2 — Escalabilidad (documentada, en camino).** El firmware ESP32 de
Sebastián (`AgroAlert_ESP32.ino`) implementa el nodo con Wi-Fi 802.11 b/g/n +
Firebase Realtime Database, DHT11 y relé con fail-safe local. Es la **Ruta A**
de [INTEGRACION_ARDUINO.md](INTEGRACION_ARDUINO.md): cuando el nodo del huerto
necesite telemetría remota y varios observadores, el mismo protocolo viaja por
Wi-Fi hacia la nube y la interfaz evoluciona a la app Compose de Sebastián.

## 3. Por qué el prototipo usa Bluetooth (y no Wi-Fi)

1. **El hardware disponible no tiene Wi-Fi.** El nodo del prototipo es un Uno
   R3, que solo tiene puerto serie. Conectarlo por Wi-Fi era técnicamente
   imposible sin cambiar de placa; el HC-05 convierte ese puerto serie en radio.
2. **El protocolo no cambió.** Siempre fue serie con tramas
   `TELEMETRIA|…`. Solo el medio físico pasó de cable a radio: cero cambios en
   el diseño del informe.
3. **Wi-Fi sigue vivo en el proyecto.** La app conserva el cliente TCP para el
   nodo en red y el simulador; Bluetooth es el transporte del prototipo de
   campo, no un reemplazo de la arquitectura.
4. **La seguridad se mantuvo por capas.** El PIN trivial del HC-05 (motivo por
   el cual se evaluó retirarlo) se mitiga en la aplicación: contraseñas con
   PBKDF2, autenticación de cuenta (toda cuenta opera el relé desde 1.5.0),
   bloqueo por intentos, caducidad de datos a 7 s y confirmación de cada orden. La auditoría
   [`simulator/attack.py`](../simulator/attack.py) resistió 8 de 9 ataques, y el
   alcance de 10 m punto a punto con un solo cliente da una superficie de
   ataque menor que una red Wi-Fi compartida. El salto a TLS llega con la
   Etapa 2 (ESP32 + Firebase).

## 4. Compatibilidad hoy

- Las dos apps se instalan lado a lado (paquetes distintos) para demostrar la
  interfaz de ambos integrantes en un solo teléfono.
- El protocolo es el mismo en ambos repos: las tramas del firmware ESP32 y las
  del Uno+HC-05 comparten formato, por lo que la fusión de la Etapa 3 es de
  interfaz, no de negocio.

## 5. Hoja de ruta

| Fase | Alcance | Estado |
|---|---|---|
| 1 | Arquitectura combinada y justificación de transporte (este documento) | ✅ 17-11-2026 |
| 2 | Portar la bitácora de campo de Sebastián a esta app, con crédito | ✅ 17-11-2026 (`Bitacora.kt`) |
| 3 | Interfaz Compose/MVVM de Sebastián sobre `NodeConnection` real (TCP+SPP) | Después de la entrega |

## 6. Demo (30 min)

El guion de demostración con el Arduino físico está en
[BLUETOOTH.md](BLUETOOTH.md); si no hay hardware a mano, el montaje de
dos celulares con Serial Bluetooth Terminal que lo reemplaza está en
[SIMULADOR_BLUETOOTH.md](SIMULADOR_BLUETOOTH.md).
