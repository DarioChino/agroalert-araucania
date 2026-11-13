# Verificación y guion de demostración

## Pruebas automatizadas

En la raíz del proyecto:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python -m unittest discover -s simulator -v
```

Cobertura de casos:

- Lectura compatible con la trama original y extensión Wi-Fi.
- Límites térmicos exactos: 5 y 10 °C, además de temperaturas negativas.
- Rechazo de NaN, infinito, humedad inválida, estado contradictorio, duplicados y trama excesiva.
- Sensor averiado sin números ficticios.
- Hashes con sal, aceptación de contraseña correcta y rechazo de contraseña equivocada.
- Bloqueo en tercer intento y expiración a los 60 s.
- Dos clientes TCP simultáneos reciben el mismo estado.
- Monitor rechazado al intentar controlar, incluso enviando un comando directamente.
- Clave de Monitor no autentica el rol Control.
- Autenticación fragmentada en varios envíos TCP.
- Comandos `1`, `0` y `A` confirmados y publicados a los clientes.
- Sensor en error bloquea comandos en el servidor.
- Retorno a automático al desconectarse el controlador.

Estas pruebas no sustituyen la demostración con dos celulares y hardware real.

## Resultado de la verificación actual

| Comprobación | Resultado |
|---|---|
| `:app:assembleDebug` | APK generado correctamente |
| `:app:testDebugUnitTest` | 6 pruebas aprobadas |
| `:app:lintDebug` | 0 errores (quedan advertencias de textos y estilos) |
| Simulador Python | 5 pruebas de integración aprobadas |
| Emulador Android 16 (API 36) | Flujo de configuración, login, conexión y panel verificado |
| Telemetría | 15 °C / 65 % recibidos y visualizados; ciclo 15/8/3 °C observado |
| Control | Encender, apagar y automático verificados; relé y modo reflejados |
| Confirmación | `Orden confirmada por el nodo · ACK:0` visible |
| Segundo cliente Monitor | Recibió `RELE:ACTIVADO|MODO:MANUAL` junto al Control |
| Pérdida de enlace | Cubierta por diseño y pruebas de protocolo; pendiente repetir en video con dos teléfonos |
| Hardware físico | Pendiente: no hay módulo Wi-Fi definido ni firmware adaptado |

Capturas del emulador: [panel simulado](evidencias/02_panel_simulacion.png) y [control simulado](evidencias/03_control_simulacion.png).

## Demostración para la entrega

| Paso | Acción | Resultado esperado |
|---|---|---|
| 1 | Instalar la misma APK en dos Android | Ambas instalaciones abren correctamente |
| 2 | Configurar accesos y fallar tres veces | Bloqueo visible por 60 s, también después de cerrar y abrir |
| 3 | Ingresar como Control y Monitor | Ambos acceden; Monitor no tiene controles |
| 4 | Conectar a la IP y claves correctas | Panel activo, lecturas en ambos equipos |
| 5 | Simular 15, 8 y 3 °C con `--cycle` | Verde, amarillo, rojo; automático activa relé a ≤5 °C |
| 6 | Enviar encender y apagar | ACK, cambio reflejado en los dos paneles |
| 7 | Volver a automático | Decide según temperatura, visible en ambos equipos |
| 8 | Detener el simulador o desconectar Wi-Fi | Aviso de pérdida, dato antiguo y controles bloqueados |
| 9 | Reiniciar con `--sensor-error` y reconectar | Sin temperatura inventada, aviso de sensor y control bloqueado |
| 10 | Repetir con módulo físico adaptado | Medidas reales, LEDs y clic del relé verificados en banco |

## Evidencia pendiente de hardware

No se afirma haber probado alcance Wi-Fi, ejecución en los dos celulares del equipo, funcionamiento de la bomba ni Arduino físico. Registrar modelo de placa/módulo, firmware, teléfonos, versión Android, red, distancias y resultados cuando se realice el laboratorio.

Para el informe, separar capturas etiquetadas de simulación de las pruebas reales del montaje.
