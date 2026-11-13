# Actualización de dependencias — 13-11-2026

Actualización menor: se refrescaron las bibliotecas de interfaz señaladas por
Android Lint como con versiones más nuevas disponibles, se corrigieron los
avisos de nulabilidad que introdujo la versión nueva, se recompiló todo y se
regeneró el APK de publicación.

## Qué cambió

| Cambio | Detalle |
|---|---|
| `androidx.appcompat` | 1.7.0 → **1.8.0** |
| `com.google.android.material` | 1.12.0 → **1.14.0** |
| Avisos de nulabilidad | appcompat 1.8.0 marca `getText()` como `@Nullable`; se ajustaron 9 lecturas de campos de texto con llamadas seguras (`text?.toString() ?: ""`) en `LoginActivity.kt` y `ConnectionActivity.kt`. Sin cambio de comportamiento. |

No cambió el número de versión (`1.0.0`, `versionCode 1`), los permisos ni la
lógica: el APK nuevo se instala encima del anterior en el mismo celular.

## Verificación

| Comprobación | Resultado |
|---|---|
| Compilación limpia (debug + tests + lint + release) | Correcta, 2 m 27 s |
| Avisos del compilador Kotlin | **0** (antes 9 por el cambio de anotaciones) |
| Pruebas unitarias JVM | **6/6** |
| Android Lint | **0 errores**, 15 avisos (los 2 de dependencia obsoleta desaparecieron) |
| Firma del release | Correcta (keystore propio) |

## Artefactos

| Archivo | Uso | Tamaño |
|---|---|---|
| `entregables/AgroAlert-1.0.apk` | Publicación (release firmado, R8) | 1,6 MB |
| `entregables/AgroAlert-1.0-debug.apk` | Pruebas de desarrollo | 6,4 MB |

SHA-256 del APK de publicación:

```text
19E4E98C69AB8CB09F843481325A9CB7F929C9D601AF8C561653D05643638C11
```

El mismo APK está en `assets/AgroAlert-1.0.apk` del sitio
([AgroAlertWeb](https://github.com/DarioChino/agroalert-web)), que publica la
huella en la sección «Descargar».

## Publicación

- Código de la app: https://github.com/DarioChino/agroalert-araucania
- Sitio web: https://dariochino.github.io/agroalert-web/ (GitHub Pages, rama `main`)
- El sitio se actualizó con el nuevo tamaño, fecha y huella SHA-256.

## Cómo reproducir

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```
