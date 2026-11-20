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
| Authentication | Requiere activar **Correo electrónico/contraseña** en la consola (ver abajo) |

Las reglas limitan cada usuario a su propio documento `users/{uid}` y a las
subcolecciones `events` y `commands`, usando `request.auth.uid`.

## Paso pendiente: habilitar Email/Password

La API pública de Identity Platform solo permite inicializar Auth mediante
`identityPlatform:initializeAuth`, que **upgradea el proyecto a Identity Platform**
y cambia la facturación (Spark: 3.000 DAU; Blaze: 50.000 MAU gratis y luego pago).
Por eso no se ejecutó automáticamente. Sin este paso, el acceso responde
`CONFIGURATION_NOT_FOUND`.

Hacerlo manualmente, sin costo:

1. <https://console.firebase.google.com/project/agroalert-5a4ad/authentication/users>
2. **Authentication → Sign-in method → Correo electrónico/contraseña → Habilitar → Guardar**.
3. No hace falta verificación por correo: la app deriva el correo del usuario
   (`profe.juan_1` → `profe.juan_1@agroalert.local`) en el dominio ficticio
   `@agroalert.local`.

Tras habilitarlo, la semilla `admin / admin1` y las cuentas creadas desde la app
funcionan igual que en modo local, pero con Auth gestionando la clave.

## Compilar

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease
```

La huella del release 1.6.0 es
`B6AEE3CCF7725ACC5B77B245B26EAF3DE70F70B38C91740A7AE2152E91258721`.

## Qué se guarda en cada modo

| Dato | Sin JSON (modo local) | Con JSON (1.6.0, actual) |
|---|---|---|
| Acceso | Hash PBKDF2 en `local_credentials.xml` | Firebase Auth; hash e historial en `users/{uid}` de Firestore |
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
- Hasta activar Email/Password en consola, esta APK no permite iniciar sesión.
- La clave del nodo Wi-Fi sigue viajando en texto plano al simulador: es un token del prototipo, distinto de la clave de la app. TLS requiere el módulo Wi-Fi de la etapa 2.
- La clave de cuenta de servicio en `ClaveFirebase/` es un secreto de administrador: no se compila, no se publica y está ignorada por Git. Usar solo para tareas de configuración del proyecto.
