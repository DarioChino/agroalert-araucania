# Revisión profunda — 11-11-2026

Revisión previa a la compilación del APK de publicación. Se revisó el código, se
compiló desde cero, se ejecutaron las pruebas y se probó el flujo completo en un
emulador Android 16 (API 36).

## Resultado general

| Comprobación | Resultado |
|---|---|
| Compilación limpia (`clean assembleDebug`) | Correcta |
| Pruebas unitarias JVM | **6/6** |
| Pruebas de integración del simulador | **5/5** |
| Android Lint | **0 errores** (19 avisos, en su mayoría de estilo) |
| Flujo completo en emulador API 36 | Correcto |
| APK release firmado | Correcto (1,4 MB) |

## Qué se probó en el emulador

1. Instalación limpia y creación de contraseñas distintas para `control` y `monitor`.
2. Inicio de sesión como `control` y conexión TCP al simulador en `10.0.2.2:5000`.
3. Telemetría en vivo cada 2 segundos, con los tres estados del ciclo (15, 8 y 3 °C).
4. Órdenes `Apagar` y `Encender` en modo manual, con confirmación `ACK` del nodo.
5. Rol `monitor`: panel sin botón de control y etiqueta de solo lectura.
6. Caída del nodo: aviso de enlace perdido, dato marcado como antiguo y controles bloqueados.
7. Tres intentos fallidos: bloqueo de 60 s, mensaje con cuenta regresiva y desbloqueo automático.
8. APK release instalado y probado con el mismo flujo (login, conexión y telemetría).

Evidencias: [primer inicio del release](evidencias/04_primer_inicio_release.png),
[panel en revisión](evidencias/05_panel_revision.png) y
[control en revisión](evidencias/06_control_revision.png).

## Hallazgos corregidos en esta revisión

| # | Hallazgo | Severidad | Corrección |
|---|---|---|---|
| 1 | El APK de publicación no estaba firmado ni existía configuración `release`. | Alta | Firma release con keystore propio, R8 y reducción de recursos. |
| 2 | Los errores de red mostraban la excepción Java (`Connection reset`). | Media | Mensajes en español para timeout, conexión rechazada, host inválido y enlace perdido. |
| 3 | `allowBackup="false"` está obsoleto en Android 12+; una transferencia entre equipos podía copiar los hashes de las claves. | Media | `data_extraction_rules.xml` y `backup_rules.xml` excluyen preferencias, bases y archivos. |
| 4 | `targetSdk 35` con `compileSdk 36`. | Baja | `targetSdk 36`, probado en emulador API 36. |
| 5 | Permiso `ACCESS_NETWORK_STATE` declarado sin uso. | Baja | Eliminado; solo queda `INTERNET`. |
| 6 | Los campos de texto se veían grises, como deshabilitados. | Baja | Fondo blanco explícito en los `TextInputLayout`. |
| 7 | Sin límite superior de contraseña: una entrada enorme podía retrasar el PBKDF2. | Baja | Máximo 128 caracteres en creación y verificación. |

## Avisos aceptados o fuera de alcance

- **Textos en Kotlin (Lint `SetTextI18n`)**: la app es monolingüe (es-CL) por alcance del proyecto; no se planea traducción.
- **`commit()` en preferencias**: es intencional. Se ejecuta en hilo de fondo y garantiza que el bloqueo quede guardado antes de responder.
- **Sin TLS en el TCP del prototipo**: documentado en el README. Las claves por rol y la red local son la barrera actual; no se debe exponer el nodo a Internet.
- **Sin reconexión automática**: al perder el enlace se vuelve a conectar manualmente. El nodo regresa a modo automático solo.
- **Sin historial permanente ni notificaciones**: fuera del alcance definido.
- **Icono no adaptativo**: pendiente cosmético; se puede reutilizar el logo en la página web.

## Cómo reproducir

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleRelease

cd simulator
python -m unittest -v test_server.py
```

## Artefactos

| Archivo | Uso | Tamaño |
|---|---|---|
| `entregables/AgroAlert-1.0.apk` | Publicación (release firmado, R8) | 1,4 MB |
| `entregables/AgroAlert-1.0-debug.apk` | Pruebas de desarrollo | 6,7 MB |

SHA-256 del APK de publicación:

```text
7DA174456A6B8192EDDCE4B896134DBECB4CB60183B8AC4A58386A6398D7ED40
```

> Nota del 13-11-2026: este APK corresponde a la revisión de esa fecha. Tras
> actualizar dependencias se regeneró el APK con otra huella; la vigente está en
> [docs/ACTUALIZACION_13-11.md](ACTUALIZACION_13-11.md).
