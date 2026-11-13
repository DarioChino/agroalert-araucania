# Contrato TCP AgroAlert v1 (propuesto)

Este documento define la interfaz que implementan la app y el simulador. **Todavía no está implementada en un módulo Wi-Fi físico.** No reemplaza el cableado ni el sketch original de su Arduino.

## Transporte

- TCP IPv4, puerto predeterminado `5000`, configurable en la app.
- Un socket persistente por celular, al menos dos clientes simultáneos en el nodo.
- Texto ASCII/UTF-8 compatible, una trama por línea `\n`; el receptor tolera `\r\n`.
- Límite app: 512 caracteres por línea. Límite simulador: 512 bytes.
- No asumir que cada lectura del socket equivale a una trama: puede llegar fragmentada o agrupada.
- Muestreo: cada 2 segundos. La app invalida el enlace al superar 7 segundos sin telemetría válida (hasta ~1 s adicional por timeout de lectura).

## Autenticación

Primera línea enviada por el celular:

```text
AUTH|ROL:CONTROL|TOKEN:<clave-control>
```

o:

```text
AUTH|ROL:MONITOR|TOKEN:<clave-monitor>
```

Las claves deben ser distintas y contener 16–128 caracteres entre letras, números, `-` y `_`. El nodo valida la clave **y su correspondencia con el rol**. El rol enviado por el cliente no debe aceptarse sin verificar esa correspondencia.

Respuesta del hardware:

```text
AUTH:OK
```

Respuesta exclusiva del simulador:

```text
AUTH:OK|SIMULADOR
```

Fallo: `AUTH:ERROR`, luego cerrar el socket. No se envían las contraseñas locales de la app. La clave del nodo solo permanece en memoria del campo mientras se conecta; no se guarda en preferencias.

## Telemetría

Formato original compatible:

```text
TELEMETRIA|TEMP:8.0C|HUM:65%|RELE:APAGADO|MODO:AUTO|ESTADO:PRE_HELADA
```

Formato extendido recomendado:

```text
TELEMETRIA|TEMP:8.0C|HUM:65%|RELE:APAGADO|MODO:AUTO|ESTADO:PRE_HELADA|SENSOR:OK
```

Falla de sensor:

```text
TELEMETRIA|TEMP:NA|HUM:NA|RELE:APAGADO|MODO:AUTO|ESTADO:SENSOR_ERROR|SENSOR:ERROR
```

Valores permitidos:

- `RELE`: `ACTIVADO` o `APAGADO` (estado lógico, no nivel eléctrico Active LOW).
- `MODO`: `AUTO` o `MANUAL`.
- `ESTADO`: `OK`, `PRE_HELADA`, `HELADA_CRITICA`, `SENSOR_ERROR`.
- `SENSOR`: `OK` o `ERROR`.
- Temperatura numérica finita entre −50 y 80 °C y humedad de 0 a 100 %. Son límites del parser; no amplían el rango físico del DHT11.
- El estado debe coincidir con la temperatura; se rechazan campos repetidos, desconocidos, incompletos o inconsistentes.
- Con sensor en error, TEMP y HUM deben ser `NA`. Los errores de formato no renuevan la vigencia del último dato.

## Órdenes

Solo cliente Control autenticado:

| Línea | Acción | Respuesta |
|---|---|---|
| `1` | Relé encendido, modo manual | `ACK:1` |
| `0` | Relé apagado, modo manual | `ACK:0` |
| `A` | Volver a automático | `ACK:A` |

El nodo aplica el comando, responde ACK y transmite el nuevo estado a **todos los clientes**. Un ACK no es una medición de caudal ni prueba de funcionamiento de la bomba; confirma que el nodo procesó la orden.

Errores: `ERROR:ROL_SIN_PERMISO`, `ERROR:COMANDO_INVALIDO`, `ERROR:SENSOR_ERROR`.

Solo puede haber una orden pendiente por conexión. Sin confirmación en 5 s, la app desconecta, señala la incertidumbre y requiere reconectar. **No reenvía automáticamente órdenes**, para evitar acciones duplicadas tras cortes.

## Autonomía del nodo

La lógica térmica debe ejecutarse en el Arduino/nodo aunque no haya celulares conectados. Propuesta del simulador: modo manual con vigencia máxima de 30 s; después vuelve a AUTO. También vuelve a AUTO si se desconecta el último controlador. Esta política debe acordarse e implementarse en el firmware físico.

El puente Wi-Fi debe validar permisos antes de transmitir `1`, `0`, `A` al Arduino. Si detecta pérdida de enlace o vence la vigencia manual, debe transmitir `A`. El Arduino no puede depender de las órdenes periódicas del teléfono para evaluar el sensor.

## Arquitectura física pendiente

```text
Celulares → red Wi-Fi → servidor TCP del módulo → enlace serie → Uno → sensor / LEDs / relé
```

También se podría concentrar el control en una placa Wi-Fi, pero cambiaría el montaje. No se asignan pines, niveles de tensión ni bibliotecas a una placa aún desconocida. El firmware debe ajustarse una vez confirmado el modelo.

El tráfico TCP actual carece de TLS. Las claves de rol son credenciales portadoras del prototipo y requieren una red local controlada. Un despliegue remoto requeriría otro diseño de acceso y transporte cifrado.
