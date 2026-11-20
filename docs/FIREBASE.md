# Firebase: cómo activar el guardado en la nube

Hoy **no hay proyecto Firebase**, así que la APK 1.5.0 sigue funcionando en modo local:
las contraseñas se guardan como hash PBKDF2 en el teléfono y la bitácora/cola en
SharedPreferences. El código para nube ya está escrito, pero **no se compila ni se
activa** hasta que exista `app/google-services.json`.

## 1. Crear el proyecto (gratis)

1. Entra a <https://console.firebase.google.com/> con tu Google y pulsa **Agregar proyecto**.
2. Nombre sugerido: `agroalert-araucania`. No actives Analytics.
3. En **Compilación > Authentication**, pestaña **Sign-in method**, habilita **Correo electrónico/contraseña**.
4. En **Compilación > Firestore Database**, crea la base de datos. Empieza en **modo de producción**; luego pega las reglas de abajo.
5. En **Configuración del proyecto > Tus apps**, agrega una app Android con paquete `cl.agroalert.araucania`. El SHA-1 es opcional para este flujo.
6. Descarga `google-services.json` y cópialo a `app/google-services.json` (el ejemplo está en `app/google-services.json.example`). Ese archivo está ignorado por Git: no lo subas.

## 2. Reglas de seguridad

En **Firestore Database > Reglas**, pega el contenido de [`firestore.rules`](../firestore.rules) y publica.
Cada usuario solo puede leer/escribir su propio documento `users/{uid}` y sus subcolecciones `events` y `commands`.

Si prefieres CLI:

```powershell
firebase deploy --only firestore:rules
```

## 3. Activar y compilar

Al existir `app/google-services.json`, Gradle aplica el plugin de Google Services,
añade Firebase Auth + Firestore y compila `app/src/cloud/java/.../CloudStore.kt` en lugar del stub local.
No hay que cambiar código a mano.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:assembleRelease
```

Señal de que quedó activo: en la pantalla de acceso dice *«Las cuentas, claves y la bitácora se guardan en Firebase»*.

## Qué se guarda en cada modo

| Dato | Sin JSON (actual) | Con JSON |
|---|---|---|
| Acceso | Usuario y hash PBKDF2 en `local_credentials.xml` | Firebase Auth (correo derivado del usuario); el hash y su historial en Firestore |
| Bitácora (60 eventos) | `bitacora.xml` | `users/{uid}/events` |
| Cola de órdenes (5) | `buffer.xml` | `users/{uid}/commands` |
| Temperatura / humedad | Solo en memoria, caducan a 7 s | Igual: no se guardan en ningún lado |
| Tema claro/oscuro | `ui.xml` | Igual: es preferencia de la persona, no dato del huerto |

El correo que usa Firebase se deriva del usuario: `profe.juan_1` → `profe.juan_1@agroalert.local`.
No hace falta que el dominio exista ni enviar correos de verificación.

## Política de claves (los 4 puntos del feedback)

- Mínimo 8 caracteres con mayúscula, minúscula y número.
- Rechazo de patrones comunes (`1234`, `qwerty`, `password`, …) y de la contraseña que contenga el usuario.
- Historial de las últimas 3 claves: no se pueden repetir.
- Vigencia de 90 días; la app obliga a cambiar la clave vencida antes de continuar.

Con Firebase, Auth gestiona la clave de acceso y Firestore guarda `currentHash`, `history` (3) y `changedAt`.
El historial se compara en el cliente con PBKDF2; las reglas de Firestore impiden que otro usuario lo lea.
Para una validación **servidor-side** estricta haría falta una Cloud Function: queda como ampliación.

## Límites honestos

- Firebase Auth conserva un token de sesión en el dispositivo; es estándar y no es la contraseña.
- Firestore offline cache se desactiva explícitamente en `CloudStore` (`setPersistenceEnabled(false)`).
- La clave del nodo Wi-Fi sigue viajando en texto plano al simulador: es un token del prototipo, distinto de la clave de la app. TLS requiere el módulo Wi-Fi de la etapa 2.
- Sin `google-services.json` esta compilación **no cumple** «nada de datos en el teléfono»: avisa en pantalla y en este documento.
