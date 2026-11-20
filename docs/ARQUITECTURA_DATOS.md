# Dónde y cómo guarda los datos la app (para responder en la demo)

**Fecha:** 20-11-2026 · Versión 1.6.0: Firebase activo, 15 pruebas unitarias y Lint **sin errores**.

## 1. Qué guarda hoy la app (y dónde)

**No hay base de datos SQL.** Desde la 1.6.0 la persistencia de cuentas, claves
y bitácora está en **Firebase** (proyecto `agroalert-5a4ad`), no en el teléfono.

| Dato | Dónde está (1.6.0, con `google-services.json`) | Respaldo sin JSON |
|---|---|---|
| Cuenta y clave | Firebase Auth (correo derivado del usuario) + `users/{uid}` en Firestore (`currentHash`, `history`, `changedAt`) | `local_credentials` en SharedPreferences |
| Bitácora (60 eventos) | `users/{uid}/events` en Firestore, sin caché offline | `bitacora` en SharedPreferences |
| Cola de órdenes (5) | `users/{uid}/commands` en Firestore | `buffer` en SharedPreferences |
| Tema claro/oscuro | `ui` en SharedPreferences (preferencia de la persona, no dato del huerto) | Igual |
| Telemetría | **No se persiste**: vive solo en memoria y caduca a los 7 s | Igual |

La caché offline de Firestore se desactiva en `CloudStore` para que la bitácora
no quede en el dispositivo. Las reglas de `firestore.rules` (publicadas) limitan
cada usuario a su propio `uid`. El `.gitignore` excluye `google-services.json` y
toda la carpeta `ClaveFirebase/`.

### Cuenta única (desde 1.5.0)

Cualquiera puede crear una cuenta desde la pantalla de ingreso; no hay perfiles:
toda cuenta ve el panel y puede accionar el relé. Con Firebase activo, Auth
guarda la credencial y Firestore el hash de política. Cada dueño cambia su clave
con su clave actual; no hay «admin» con privilegios de recuperación. La semilla
`admin/admin1` se conserva para la demo.

**Pendiente:** habilitar *Correo electrónico/contraseña* en la consola de Auth;
sin eso el acceso responde `CONFIGURATION_NOT_FOUND`. Ver
[docs/FIREBASE.md](FIREBASE.md).

## 2. Respuesta lista para el docente (30 segundos)

> «El modelo de datos del prototipo es mínimo (hash de credenciales, historial
> de 3 claves, contadores de bloqueo y bitácora de eventos), así que no usamos
> SQL. Desde la 1.6.0 guardamos cuentas, claves y bitácora en Firebase Auth y
> Firestore, por usuario y sin caché local; el modo local con SharedPreferences
> queda como respaldo. Hay una sola clase de cuenta: cada persona crea la suya
> y todas ven y controlan el nodo. Las claves nunca van en texto plano: van con
> **PBKDF2 con sal** (120.000 iteraciones) y comparación en tiempo constante. La
> telemetría ni siquiera se persiste: es de solo lectura y caduca a los 7
> segundos. Falta activar Email/Password en la consola para que el acceso
> funcione; los pasos están en `docs/FIREBASE.md».`

### Política de claves vigente

- 8 a 128 caracteres, con mayúscula, minúscula y número.
- Se rechazan patrones comunes (`1234`, `qwerty`, `password`, `abc123`, `111111`) y que la clave contenga el usuario.
- Se guardan como hash las últimas **3** claves; ninguna puede reutilizarse.
- Vigencia de **90 días**: al vencer, el login exige cambiar la clave antes de entrar.
- Firebase Auth + Firestore están **en uso** desde la 1.6.0 (etapa D2).

Puntos fuertes a mencionar si pregunta más: `allowBackup=false` en el manifest,
datos personales cero (solo usuario), reglas por `uid` y caché offline desactivada.

## 3. La idea de «que cualquiera se haga cuenta + servidor en la nube»

Es la evolución correcta del proyecto y ya está implementada con un servicio
administrado:

1. **Un servidor con cuentas es un blanco**: exige HTTPS/TLS, hash de claves en
   el servidor, límites de intentos y protección de datos personales (Ley 21.719
   en Chile). Construirlo a mano es la receta clásica de vulnerabilidades.
2. **El servicio administrado resuelve eso**: Firebase Authentication (hashing
   gestionado por Google) + Firestore para el historial, tal como se documentó
   en [ARQUITECTURA_COMBINADA.md](ARQUITECTURA_COMBINADA.md) con el firmware y
   la app del compañero.
3. **Paso intermedio barato** (si se quisiera volver atrás): migrar la bitácora
   a **Room (SQLite local)**, sin depender de red.

## 4. Hoja de ruta de datos

| Paso | Alcance | Estado |
|---|---|---|
| D1 | Bitácora en Room/SQLite local | No necesario: Firestore cubre la persistencia |
| D2 | **Firebase Auth + Firestore**: registro propio, bitácora por usuario, reglas por uid | **Hecho en 1.6.0** (falta activar Email/Password en consola) |
| D3 | Cifrado TLS extremo a extremo con el módulo Wi-Fi/ESP32 (Ruta A de `INTEGRACION_ARDUINO.md`) | Alto, con hardware |

La telemetría y el control del nodo siguen siendo locales: Firebase solo guarda
cuentas, claves y bitácora. D3 se presenta como **trabajo futuro ya planificado**.
