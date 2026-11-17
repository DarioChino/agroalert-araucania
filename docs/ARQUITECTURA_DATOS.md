# Dónde y cómo guarda los datos la app (para responder en la demo)

**Fecha:** 17-11-2026 · Revisión completa del día de la demo: compilación, 10
pruebas unitarias y Lint **sin errores**.

## 1. Qué guarda hoy la app (y dónde)

**No hay base de datos SQL.** Toda la persistencia es **local y privada**, en
archivos clave-valor de Android (SharedPreferences) dentro del almacenamiento
exclusivo de la app (`/data/data/cl.agroalert.araucania/` — ningún otro app ni
el usuario normal puede leerlos). Elegido a propósito: el prototipo guarda muy
pocos datos y sin relaciones entre ellos.

| Archivo | Contenido | Formato |
|---|---|---|
| `local_credentials` | Hash de la clave de cada rol (`salt:hash` en Base64) + contador de intentos fallidos y bloqueo | PBKDF2-HMAC-SHA256, 120.000 iteraciones, sal aleatoria de 16 bytes |
| `bitacora` | Últimos 60 eventos del nodo (conexiones, órdenes, cambios de riesgo) | Líneas de texto `fecha · evento` |
| `ui` | Modo claro/oscuro elegido | Booleano |
| Telemetría | **No se persiste**: vive solo en memoria mientras la app está abierta | — |

La verificación de clave compara con `MessageDigest.isEqual` (tiempo constante,
resistente a ataques de temporización) y **nunca guarda texto plano**: lo que
queda en el teléfono es un hash con sal, distinto en cada instalación.
`CredentialsTest` lo demuestra: dos claves iguales producen hashes distintos, y
el hash no contiene la clave.

## 2. Respuesta lista para el docente (30 segundos)

> «La app no usa SQL: el modelo de datos del prototipo es mínimo (un hash de
> credencial por rol, contadores de bloqueo y una bitácora de 60 eventos), así
> que usamos SharedPreferences, el almacenamiento clave-valor **privado** de
> Android. Las claves nunca se guardan en texto plano: van con **PBKDF2 con
> sal** (120.000 iteraciones), comparación en tiempo constante y bloqueo por
> intentos. La telemetría ni siquiera se persiste: es de solo lectura y caduca
> a los 7 segundos. Si el proyecto crece a muchas cuentas o historial
> consultable, el camino natural es **SQLite con Room** primero y cuentas en la
> nube después — está documentado como etapa de escalabilidad.»

Puntos fuertes a mencionar si pregunta más: `allowBackup=false` en el manifest
(los datos no salen en backups), datos personales cero (solo rol), sin nube
(los datos no abandonan la red local).

## 3. La idea de «que cualquiera se haga cuenta + servidor en la nube»

Es la **evolución correcta** del proyecto, pero es una etapa, no un cambio de
esta noche. Lo que trae consigo:

1. **Un servidor con cuentas es un blanco**: exige HTTPS/TLS obligatorio,
   hash de claves en el servidor, límites de intentos, recuperación de cuenta y
   protección de datos personales (Ley 21.719 en Chile). Construirlo a mano en
   una noche es la receta clásica de vulnerabilidades.
2. **El servicio administrado resuelve eso**: Firebase Authentication (registro
   propio de cuentas, hashing gestionado por Google) + Firestore para el
   historial — que es **exactamente la etapa 2 ya documentada** en
   [ARQUITECTURA_COMBINADA.md](ARQUITECTURA_COMBINADA.md) con el firmware y la
   app del compañero.
3. **Paso intermedio barato**: si solo se quiere historial consultable sin
   nube, migrar la bitácora de SharedPreferences a **Room (SQLite local)**.

## 4. Hoja de ruta de datos propuesta (tras la demo, previa acordada con el docente)

| Paso | Alcance | Esfuerzo |
|---|---|---|
| D1 | Bitácora en **Room/SQLite local**: eventos consultables y exportables, sin cambiar nada de red | Medio |
| D2 | **Firebase Auth + Firestore** (etapa 2 del proyecto): registro propio de cuentas, bitácora por usuario, panel de administración web | Alto |
| D3 | Cifrado TLS extremo a extremo con el módulo Wi-Fi/ESP32 (Ruta A de `INTEGRACION_ARDUINO.md`) | Alto, con hardware |

Hoy el prototipo responde la rúbrica completa con su almacenamiento actual;
D1-D3 se presentan como **trabajo futuro ya planificado**, no como deuda.
