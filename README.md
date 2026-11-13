# AgroAlert Araucanía

Aplicación Android nativa en **Kotlin**, creada para el proyecto de **Darío Candia y Sebastián Torres**, asignatura TI3042, docente Franco Fossati Cordero.

## Qué está implementado

- APK con cuatro pantallas: acceso, conexión Wi-Fi, monitoreo y control.
- Configuración inicial de las contraseñas locales de `control` y `monitor`, sin claves predeterminadas.
- Contraseñas con sal aleatoria y PBKDF2-HMAC-SHA256 (120.000 iteraciones).
- Bloqueo de ingreso por 60 segundos tras tres intentos fallidos, persistente al cerrar la app.
- Red TCP local, lectura cada 2 segundos desde el nodo, sin bloquear la interfaz.
- Panel de temperatura, humedad, semáforo, relé, modo y actividad reciente.
- Semáforo: normal >10 °C, prehelada >5 y ≤10 °C, riesgo crítico ≤5 °C.
- Usuario Monitor sin controles; el servidor también rechaza sus órdenes.
- Comandos `1`, `0`, `A`, confirmación explícita y bloqueo con datos ausentes, antiguos o sensor en error.
- Simulador Python con estado compartido para dos o más celulares y claves independientes por rol.
- Pruebas del protocolo, contraseñas, bloqueo y comunicación de dos clientes.

**Estado de la integración física:** el módulo Wi-Fi aún no está identificado. La app se puede probar con el simulador. El Arduino/HC-05 publicado en la web **no implementa aún este enlace Wi-Fi**; falta adaptar el firmware al hardware elegido. No se ha probado con un Arduino físico.

## Verificación realizada

- Compilación `assembleDebug`, pruebas unitarias y Lint: **exitosas**.
- Flujo completo en emulador **Android 16 (API 36)**: creación de accesos, inicio de sesión, conexión TCP, telemetría en vivo y panel.
- Órdenes `1`, `0` y `A` verificadas contra el simulador, con confirmación visible del nodo.
- Segundo cliente en rol **Monitor** conectado en paralelo: recibió los mismos cambios de relé y modo, sin permiso para controlar.
- Pruebas de integración del simulador: **5/5 exitosas**; pruebas unitarias Android: **6/6 exitosas**.
- **No probado aún** en celulares físicos ni con Arduino real. Las capturas incluidas son del simulador y están etiquetadas así.
- Evidencias: [panel](docs/evidencias/02_panel_simulacion.png) y [control](docs/evidencias/03_control_simulacion.png).
- Revisión del 11-11-2026: compilación limpia, **6/6** pruebas unitarias, **5/5** del simulador, Lint sin errores y flujo completo repetido en emulador **API 36**. Detalle en [docs/REVISION.md](docs/REVISION.md).
- APK de publicación firmado (release + R8): `entregables/AgroAlert-1.0.apk`, 1,6 MB.

## Abrir en Android Studio

1. Elige **Open** y abre esta carpeta completa, `AgroAlertAraucania`.
2. Espera la sincronización de Gradle. Usa el JDK incluido en Android Studio (17 o superior).
3. SDK de compilación: **Android 36**. Compatibilidad: **Android 8.0 o posterior**.
4. Selecciona un celular con depuración USB o un emulador y presiona **Run**.

El archivo `local.properties` apunta al SDK de este computador y está excluido de Git. En otro PC Android Studio puede regenerarlo.

### Compilar desde PowerShell

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK de pruebas: `app/build/outputs/apk/debug/app-debug.apk`. Es una compilación de desarrollo, firmada con la clave debug.

### Compilar el APK de publicación (firmado)

El APK que se publica en la web debe ir firmado con la clave **release**, no con la debug. La clave y su contraseña están en `keystore/` (excluida de Git). **Respalda esa carpeta**: sin ella no se pueden publicar actualizaciones que instalen sobre la misma app.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleRelease
```

Salida: `app/build/outputs/apk/release/app-release.apk`, con R8 activado (más liviano y sin marcas de depuración). Para entregar, cópialo como `entregables/AgroAlert-1.0.apk`.

Verificar la firma antes de subirlo:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

## Probar con dos celulares

### 1. Iniciar el nodo simulado

En PowerShell, dentro de esta carpeta:

```powershell
python .\simulator\server.py --cycle
```

No necesita instalar paquetes. Requiere Python 3.10 o superior. El ciclo cambia entre 15, 8 y 3 °C cada 12 segundos para demostrar los tres estados. Sin `--cycle`, mantiene 15 °C. Otras pruebas:

```powershell
python .\simulator\server.py --temperature 4 --humidity 78
python .\simulator\server.py --sensor-error
```

La terminal muestra el puerto **5000** y dos **claves del nodo**, una por rol. Se generan la primera vez y se conservan en `simulator/credentials.json`, excluido de Git. Para regenerarlas, detén el servidor, elimina ese archivo y vuelve a iniciarlo. No publiques ese archivo.

### 2. Conectar los equipos

- PC y los dos celulares deben tener acceso entre sí en la misma red local.
- En Windows, `ipconfig` muestra la IPv4 del adaptador Wi-Fi. Esa es la IP que se ingresa en los celulares. No uses las IP de VMware/VirtualBox.
- En el emulador de Android Studio, la dirección del PC es **10.0.2.2**.
- Si Windows solicita permitir Python en el firewall, autoriza la **red privada** que usas para la prueba. No es necesario desactivar el firewall.
- Algunas redes de invitados y puntos de acceso aíslan los clientes; si no conecta, comprueba que la red permita comunicación entre equipos.

### 3. Usar la app

1. Instala el APK en ambos celulares.
2. En el primer inicio, crea contraseñas locales distintas para `control` y `monitor` (mínimo 8 caracteres y confirmación). Estas contraseñas pertenecen a **cada instalación**.
3. Celular A: inicia sesión como `control`. Celular B: como `monitor`.
4. En ambos, escribe la misma IP y puerto. Introduce en cada uno la **clave del nodo correspondiente a su rol** que muestra el simulador.
5. Verifica que los dos paneles muestran las mismas lecturas.
6. En el celular Control, abre **Controlar el riego** y prueba encender, apagar y automático. Monitor debe reflejar el cambio y no mostrar controles.
7. Detén el servidor: ambos equipos deben avisar la pérdida del enlace en aproximadamente 7–8 segundos, marcar los datos antiguos y bloquear órdenes.

La app etiqueta visiblemente los datos del simulador. **No representan una medición real y no accionan hardware.** El modo manual del simulador dura como máximo 30 segundos; vuelve a automático al vencer ese plazo o desconectarse el último cliente Control.

## Arquitectura

```text
Android A · Control ── TCP/Wi-Fi ─┐
                                ├── Nodo compartido (actualmente simulador)
Android B · Monitor ── TCP/Wi-Fi ─┘        └── Futuro: puente Wi-Fi ↔ Arduino
```

Los celulares sincronizan sus vistas mediante el mismo nodo. No hay comunicación directa teléfono-a-teléfono ni nube. Conviene validar con el docente si esa interconexión mediada cumple su interpretación del criterio 2.1.4.

| Archivo | Responsabilidad |
|---|---|
| `LoginActivity.kt` | Configuración y acceso local |
| `ConnectionActivity.kt` | Dirección, puerto y credencial del nodo |
| `DashboardActivity.kt` | Telemetría, semáforo y eventos |
| `ControlActivity.kt` | Órdenes y confirmaciones |
| `NodeConnection.kt` | Sockets, autenticación, caducidad y estado del enlace |
| `Protocol.kt` | Validación estricta de telemetría |
| `Credentials.kt` | Hashes y bloqueo de intentos |
| `simulator/server.py` | Nodo compartido reproducible sin hardware |

## Documentación y entrega

- [Contrato para el futuro módulo Wi-Fi](docs/PROTOCOLO_WIFI.md).
- [Requisitos y decisiones tomadas del informe](docs/REQUISITOS.md).
- [Pruebas y pasos de evaluación con hardware](docs/PRUEBAS.md).
- [Revisión profunda del 11-11-2026](docs/REVISION.md).
- [Actualización de dependencias del 13-11-2026](docs/ACTUALIZACION_13-11.md).

### Repositorio publicado

El código está publicado en **https://github.com/DarioChino/agroalert-araucania**.
El sitio web del proyecto está en **https://github.com/DarioChino/agroalert-web**
y se visualiza en **https://dariochino.github.io/agroalert-web/**, junto con la
descarga del APK.

El `.gitignore` excluye `build/`, `local.properties`, `keystore/`,
`simulator/credentials.json` y `entregables/`: la clave de firma y las claves del
nodo **nunca** se publican. Para subir cambios futuros:

```powershell
git add .
git commit -m "Descripción del cambio"
git push
```

Luego pega el enlace del repositorio en la tarea del AAI, como pide la guía.

## Alcance del prototipo

La implementación aplica prácticas relacionadas con ISO/IEC 27400; **no implica certificación ni cumplimiento integral de la norma**. Las contraseñas locales se almacenan transformadas, pero el TCP del prototipo no utiliza TLS: las claves de acceso al nodo y la telemetría no tienen cifrado de extremo a extremo. WPA2/WPA3 protege el enlace inalámbrico y no sustituye TLS. El inicio de sesión local es una barrera de la instalación; el nodo aplica la autorización efectiva mediante claves independientes por rol.

El monitoreo está orientado al uso con la app abierta. No incluye servicio de alarmas en segundo plano, notificaciones push, almacenamiento histórico permanente ni acceso desde Internet. Wi-Fi amplía la cobertura respecto del montaje Bluetooth, pero depende del alcance y conectividad de la red instalada.

El estado del relé no confirma circulación de agua. Los umbrales se implementan tal como exige el informe para la demostración; el DHT11 y la estrategia de riego requieren evaluación específica antes de uso agrícola real.
