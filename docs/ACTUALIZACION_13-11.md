# Actualización del 13-11-2026

Paquete de mejoras del 13-11-2026: refresco de bibliotecas, ícono adaptativo de
araucaria, cuentas locales precreadas y modo oscuro opcional. Se recompiló,
re-testearon y regeneraron los APK de entrega.

## Qué cambió

| Cambio | Detalle |
|---|---|
| Bibliotecas de interfaz | `androidx.appcompat` 1.7.0 → **1.8.0** y `com.google.android.material` 1.12.0 → **1.14.0** |
| Avisos de nulabilidad | appcompat 1.8.0 marca `getText()` como `@Nullable`; se ajustaron 9 lecturas con llamadas seguras. Sin cambio de comportamiento. |
| Ícono adaptativo | Araucaria araucana estilizada (silueta `#103D33` sobre el gradiente de la marca `#9ADFB2→#2F7D54`), en `res/mipmap/` con capas background, foreground y **monochrome** (ícono temático en Android 13+). Reemplaza a `ic_agroalert.xml`. |
| Cuentas locales | Login con usuario y contraseña normal. Se eliminó el formulario de primer inicio. Cuentas precreadas: `admin`/`admin1` (Control) y `monitor`/`monitor1` (Monitor), sembradas como hash PBKDF2 al primer arranque. `control` sigue aceptándose como alias de `admin`. |
| Modo oscuro | Botón «Modo oscuro / Modo claro» en las cuatro pantallas. Tema `DayNight` con `values-night`, persistente entre sesiones. |
| Carpeta de íconos | `mipmap-anydpi-v26/` → `mipmap/` (redundante con `minSdk 26`; aviso de Lint resuelto). |

El bloqueo tras tres intentos fallidos (60 s) y todo el mecanismo PBKDF2 se
mantienen sin cambios. Detalle de la decisión de cuentas y su desviación frente
al informe en [REQUISITOS.md](REQUISITOS.md).

## Verificación

| Comprobación | Resultado |
|---|---|
| Compilación limpia (debug + tests + lint + release) | Correcta |
| Avisos del compilador Kotlin | **0** |
| Pruebas unitarias JVM | **7/7** (nuevo test: resolución de usuarios admin/monitor) |
| Android Lint | **0 errores**, 13 avisos (los aceptados de estilo) |
| Firma del release | Correcta (keystore propio) |

## Artefactos

| Archivo | Uso | Tamaño |
|---|---|---|
| `entregables/AgroAlert-1.0.apk` | Publicación (release firmado, R8) | 1,6 MB |
| `entregables/AgroAlert-1.0-debug.apk` | Pruebas de desarrollo | 6,4 MB |

SHA-256 del APK de publicación:

```text
E35144ABA147A941DBE7BD281F064C13DBA3849AA54D23FC5F9C60A73905ECCA
```

El mismo APK está en `assets/AgroAlert-1.0.apk` del sitio
([AgroAlertWeb](https://github.com/DarioChino/agroalert-web)), que publica la
huella en la sección «Descargar». No cambió el número de versión (`1.0.0`,
`versionCode 1`): el APK nuevo se instala encima del anterior.

## Publicación

- Código de la app: https://github.com/DarioChino/agroalert-araucania
- Sitio web: https://dariochino.github.io/agroalert-web/ (GitHub Pages, rama `main`)
- El sitio refleja tamaño, fecha y huella SHA-256 vigentes.
- Nueva guía: [INTEGRACION_ARDUINO.md](INTEGRACION_ARDUINO.md) con las rutas de
  hardware para salir del prototipo (ESP32 solo, ESP-01 puente o ESP32 puente).

## Cómo reproducir

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```
