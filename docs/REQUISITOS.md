# Requisitos y decisiones

## Fuentes y prioridad

1. `C:\Users\Dario\Downloads\dario_candia_informe.docx`: fuente principal, guía formativa Unidad 2, informe de Darío Candia.
2. https://proyecto-iot.momentumspace.cl/: montaje, código y evidencias de Unidad 1.
3. Aclaración de Darío: Bluetooth fue una solución rápida para el laboratorio; la nueva aplicación debe utilizar Wi-Fi y empezar desde cero.

No se modificaron el informe ni el sitio web.

## Cambio de conectividad

La web documenta Arduino Uno R3 + DHT11 (D2), LEDs D3/D4/D5, relé Active LOW D8 y HC-05 D10/D11. El informe nuevo sustituye Bluetooth por Wi-Fi, pero **no precisa modelo de módulo ni protocolo de red**.

Decisión de implementación: Android nativo en Kotlin con sockets TCP de la plataforma y mensajes de texto terminados en salto de línea. Se conserva la trama existente y los comandos `1`, `0`, `A`; se añade autenticación por rol y confirmación de órdenes. Este es un **contrato propuesto**, no una interfaz que ya exista en su Arduino.

## Trazabilidad

| Requisito del informe | Implementación |
|---|---|
| Android Studio y Kotlin | Proyecto Gradle Android, Kotlin 2.0.21 |
| Material Design | Componentes Material, campos con etiqueta, botones y cuatro pantallas |
| Usuario y contraseña | Usuarios locales `control`/`monitor`, definidos en primer inicio |
| Máximo tres intentos | Bloqueo temporal de 60 s persistido en preferencias privadas |
| Contraseñas transformadas | PBKDF2, sal aleatoria por contraseña, comparación segura |
| Dos celulares | Sesiones independientes al nodo compartido |
| Rol Monitor | Panel de solo lectura; servidor rechaza sus comandos |
| Temperatura y humedad | Lecturas cada 2 s, formato validado |
| Semáforo | >10 normal, >5 y ≤10 prehelada, ≤5 crítico |
| Encender/apagar/auto | Comandos originales `1`/`0`/`A` |
| Mostrar respuesta del nodo | ACK y posterior estado reportado, sin inventar cambios locales |
| Pérdida de conexión | Datos caducados, mensaje visible y controles bloqueados |
| Arduino autónomo sin red | Requisito de firmware pendiente; demostrado únicamente por simulador |
| Falla de sensor | Extensión SENSOR:ERROR, sin valores ficticios, controles bloqueados |
| Solo permisos necesarios | INTERNET y ACCESS_NETWORK_STATE; sin ubicación/Bluetooth/almacenamiento |
| Código en GitHub | Publicado: https://github.com/DarioChino/agroalert-araucania |

## Puntos pendientes de confirmar

- Modelo exacto del módulo Wi-Fi o placa (ESP-01/ESP8266, ESP32, shield u otro).
- Si se conserva el Uno como controlador principal y el módulo actúa como puente.
- Firmware real cargado actualmente y alimentación/conexiones del nuevo módulo.
- Aceptación del docente del esquema dos Android → nodo común como interconexión de aplicaciones.
- Política de modo manual: se propone vigencia de 30 s y retorno a automático, ya implementada en el simulador.

## Diferencias relevantes del firmware web

El código publicado inicializa temperatura a 0 y, ante lectura inválida, conserva el valor anterior. Luego vuelve a ejecutar la evaluación térmica, que puede sobrescribir el LED amarillo y disparar el relé con un valor inicial o antiguo. Por tanto, su comentario de “Fail-Safe” no basta para garantizar el comportamiento descrito en el informe.

Antes de integrar Wi-Fi, el firmware debe conservar un estado explícito de validez de sensor, no presentar mediciones antiguas como nuevas y reportar SENSOR:ERROR. La decisión de salida ante ese error debe validarse en el banco; el simulador utiliza relé apagado. También debe inicializar el relé Active LOW en HIGH antes de habilitar la salida y mantener la evaluación automática independiente del enlace.
