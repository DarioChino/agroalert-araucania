# Firebase: nube activa en AgroAlert

Desde la versión **1.6.0** la app compila contra el proyecto Firebase real
`agroalert-5a4ad` (displayName *AgroAlert*). El `google-services.json` está en
`app/` —ignorado por Git— y Gradle detecta su presencia para aplicar el plugin
de Google Services, añadir Firebase Auth + Firestore y compilar
`app/src/cloud/java/.../CloudStore.kt` en lugar del stub local.

Señal de que quedó activo: en la pantalla de acceso dice *«Las cuentas, claves y la
bitácora se guardan en Firebase»* y `BuildConfig.CLOUD_ENABLED == true`.

## Estado del proyecto

| Recurso | Estado |
|---|---|
| App Android | `1:634218768904:android:2b74224553d420250fbfbd`, paquete `cl.agroalert.araucania` |
| Firestore | Nativo, `southamerica-west1` |
| Reglas | [`firestore.rules`](../firestore.rules) publicadas en `projects/agroalert-5a4ad/releases/cloud.firestore` |
| Authentication | **Correo electrónico/contraseña habilitado** y verificado: registro, ingreso y borrado de un usuario de prueba dieron 200 |

Las reglas limitan cada usuario a su propio documento `users/{uid}` y a las
subcolecciones `events` y `commands`, usando `request.auth.uid`.

## Email/Password: habilitado y verificado

Se activó **Correo electrónico/contraseña** en
<https://console.firebase.google.com/project/agroalert-5a4ad/authentication/users>
→ *Sign-in method*, sin verificación por correo. La semilla y las cuentas
antiguas derivan el correo del usuario (`admin` → `admin@agroalert.local`,
dominio ficticio); desde la 1.7.0 cada cuenta nueva se crea con un correo real.

Verificación del 20-11-2026 con la API de Auth: `signUp` → 200,
`signInWithPassword` → 200 y borrado del usuario de prueba → 200. No quedó
ningún usuario de prueba en el proyecto.

Nota: la API pública de Identity Platform solo podía inicializar Auth mediante
`identityPlatform:initializeAuth`, que upgradea a Identity Platform y cambia la
facturación (Spark: 3.000 DAU; Blaze: 50.000 MAU gratis y luego pago). Por eso
se hizo desde la consola, sin costo.

La semilla `admin / admin1` y las cuentas creadas desde la app funcionan con Auth
gestionando la clave. La semilla se creó en Firebase Auth con uid `admin` y su
documento `users/admin` (`currentHash`, `history` vacío, `changedAt`). Es una
credencial pública de demo: cámbiala o bórrala antes de usar el proyecto con
datos reales.

## Cuentas y recuperación (1.7.0)

El registro pide usuario, clave y **correo real**; Firebase crea la cuenta con
ese correo y Firestore guarda `username`, `email`, `currentHash`, `history` y
`changedAt` en `users/{uid}`. El ingreso acepta el correo; `admin` y las cuentas
creadas antes de la 1.7.0 siguen entrando con su usuario derivado.

«RECUPERAR CONTRASEÑA» llama a `sendPasswordResetEmail`: Firebase envía el
enlace y la persona elige la clave nueva en la página de Firebase. Como ese
cambio ocurre fuera de la app, el siguiente ingreso vuelve a alinear el hash y
el historial de Firestore (`CloudStore.reconcile`), preservando los 3 históricos
y la vigencia de 90 días. Si la clave elegida en la página de Firebase no cumple
la política de la app, se marca `policyIssue` y el ingreso obliga a cambiarla.
El dominio interno `@agroalert.local` se rechaza en el formulario porque allí no
llega ningún correo.

**Estado verificado en consola/API (20-11-2026):** `Email enumeration protection`
está activa (`emailPrivacyConfig.enableImprovedEmailPrivacy = true`), así que
un bot no puede usar `signIn`/`sendPasswordResetEmail` para descubrir qué
correos existen; los correos de registro que ya están tomados siguen devolviendo
`EMAIL_EXISTS`, por eso la app muestra un mensaje genérico. El bloqueo local de
3 intentos con espera creciente (60 s → 5 min → 15 min) es una barrera de la
interfaz, **no** una defensa contra bots que llamen a la API directamente.

Pendiente de configurar en consola, sin cambios de código: bajar la cuota de
`identitytoolkit.googleapis.com` en *Google Cloud → APIs y servicios → Identity
Toolkit API → Cuotas* (por ejemplo peticiones por minuto por proyecto). MFA,
bloqueo por cuenta y App Check con proveedor real requieren Identity Platform o
un proveedor de atestación; no se activaron.

## Compilar

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease
```

La huella del release 1.8.0 es
`F5C1C8375D8B1388AC5FAAEDFE0EB091CEA279D9180DCF53E798D188E5D7FA34`.

## Qué se guarda en cada modo

| Dato | Sin JSON (modo local) | Con JSON (1.8.0, actual) |
|---|---|---|
| Acceso | Hash PBKDF2 en `local_credentials.xml` | Firebase Auth; hash e historial en `users/{uid}` de Firestore |
| Correo de recuperación | No existe | `users/{uid}.email` en Firestore; el enlace lo envía Firebase Auth |
| Bitácora (60 eventos) | `bitacora.xml` | `users/{uid}/events` |
| Cola de órdenes (5) | `buffer.xml` | `users/{uid}/commands` |
| Temperatura / humedad | Solo en memoria, caducan a 7 s | Igual: no se guardan en ningún lado |
| Tema claro/oscuro | `ui.xml` | Igual: es preferencia de la persona, no dato del huerto |

La caché offline de Firestore se desactiva explícitamente en `CloudStore` para que
la bitácora no quede en el teléfono.

## Política de claves (los 4 puntos del feedback)

- Mínimo 8 caracteres con mayúscula, minúscula y número.
- Rechazo de patrones comunes (`1234`, `qwerty`, `password`, …) y de la contraseña que contenga el usuario.
- Historial de las últimas 3 claves: no se pueden repetir.
- Vigencia de 90 días; la app obliga a cambiar la clave vencida antes de continuar.

Auth gestiona la clave de acceso y Firestore guarda `currentHash`, `history` (3) y
`changedAt`. El historial se compara en el cliente con PBKDF2; las reglas impiden
que otro usuario lo lea. Para validación **server-side** haría falta una Cloud
Function: queda como ampliación.

## Límites honestos

- Firebase Auth conserva un token de sesión en el dispositivo; es estándar y no es la contraseña.
- El ingreso depende de que Firebase Auth siga habilitado en el proyecto; si se desactiva Email/Password, la app vuelve a responder `CONFIGURATION_NOT_FOUND`.
- La clave del nodo Wi-Fi sigue viajando en texto plano al simulador: es un token del prototipo, distinto de la clave de la app. TLS requiere el módulo Wi-Fi de la etapa 2.
- La clave de cuenta de servicio en `ClaveFirebase/` es un secreto de administrador: no se compila, no se publica y está ignorada por Git. Usar solo para tareas de configuración del proyecto.
