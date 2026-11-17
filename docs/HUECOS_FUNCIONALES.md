# Huecos funcionales y ampliaciones pendientes

Anotación del 13-11-2026 (solicitud de la persona usuaria), a partir de la revisión del
prototipo entregado: **hoy la app solo muestra temperatura, humedad y estado del riego,
y no tiene un apartado propio de avisos de riesgo crítico ni más control sobre el huerto**.

Este documento es el backlog de ampliación. **No cambia el informe ya entregado**:
las funciones de aquí son ampliaciones y deben declararse como tales ante el docente.

## 1. Estado actual (línea base)

| Elemento | Hoy |
|---|---|
| Pantallas | Acceso, Conexión, Panel, Control (4) |
| Telemetría | `TEMP`, `HUM`, `RELE`, `MODO`, `ESTADO`, `SENSOR` (6 campos exactos) |
| Semáforo | Solo térmico: >10 °C normal, >5 y ≤10 °C prehelada, ≤5 °C crítico |
| Humedad de suelo | **No existe** (no hay sensor ni campo en el protocolo) |
| Alertas | No hay pantalla ni avisos dedicados; el riesgo aparece como una línea de texto en el panel |
| Control | Un solo relé con tres órdenes: `1` (encender), `0` (apagar), `A` (automático) |
| Configuración | Umbrales fijos en código (`Protocol.kt`, `DashboardActivity.kt`) |
| Historial | Solo "actividad de esta sesión" (eventos de texto, no persistido) |

## 2. Pedido y alcance de la ampliación

1. **Apartado de aviso de riesgos críticos**, con al menos tres factores:
   - frío excesivo / helada;
   - humedad ambiente demasiado alta;
   - tierra demasiado seca.
2. **Más control sobre el huerto**, más allá de encender/apagar el riego.

## 3. Propuesta de ampliación

### F1 — Pantalla «Alertas y riesgos» (nueva) — prioridad ALTA

Nueva pantalla `AlertsActivity`, alcanzable desde el panel, más un **banner persistente
en el panel** con el peor riesgo vigente.

Cada factor se evalúa por separado y se muestra con nivel propio (`Normal` / `Alerta` /
`Crítico`), color, valor medido y qué hacer al respecto:

| Factor | Normal | Alerta | Crítico |
|---|---|---|---|
| Temperatura | >10 °C | 5–10 °C (prehelada) | ≤5 °C (helada) |
| Humedad ambiente | 40–75 % | 75–85 % o <35 % | ≥85 % (moho, enfermedades) |
| Humedad de suelo | 40–75 % | 25–40 % o 75–85 % | ≤25 % (seco) o ≥90 % (encharcado) |

Reglas de la implementación:

- Los umbrales deben quedar en **un solo lugar configurable** (hoy están duplicados entre
  `Protocol.riskFor()` y los textos de `DashboardActivity`).
- Una alerta **solo se declara activa** si la lectura es fresca (ventana de 7 s ya usada en
  `DashboardActivity`) y `SENSOR:OK`. Con datos antiguos o sensor en error, se muestra
  «sin datos» y **no** se afirma que el huerto está bien ni mal.
- Alerta crítica ⇒ el color rojo debe verse en el panel sin abrir otra pantalla, y el
  botón «Controlar el riego» queda disponible pero con aviso explícito de la causa.
- Sin vibración, notificaciones push ni AlarmManager: el alcance declarado dice que la
  app **no** tiene alarms en segundo plano. Si se avisa en tiempo real, es con la app abierta.

### F2 — Humedad de suelo en el protocolo — prioridad ALTA

Campo opcional y retrocompatible: `SUELO:45%`.

- `Protocol.parse()` debe tolerar su ausencia (clientes/nodos viejos) **y** rechazarla si
  el valor está fuera de rango o duplica una clave.
- Sin sensor de suelo ⇒ la app lo rotula «Sensor de suelo no disponible» y **no** inventa
  valores ni permite umbrales de suelo.
- Hardware sugerido: sensor **capacitivo** de humedad de suelo (evita la corrosión del
  FC-28 en uso continuo) conectado al nodo; el DHT11 ya cubre temperatura y humedad ambiente.
- `simulator/server.py` debe poder simularlo (`--soil 22`), con ciclo que baje el valor
  para disparar la alerta de tierra seca en la demostración.

### F3 — Más control sobre el huerto — prioridad MEDIA/BAJA (requiere hardware)

Ordenados de menor a mayor complejidad de hardware:

| Control | Hardware extra | Comando |
|---|---|---|
| Riego por zonas (Sector 1 / Sector 2) | Relé extra por zona | `S1`, `S2`, `S0` |
| Ventilación (casa de cultivo) | Relé + ventilador | `V`, `N` |
| Programación horaria de riego | Relé + reloj en el nodo | `H` + parámetros |
| Umbrales configurables desde la app | Solo firmware del nodo | `T` + parámetros |

Mientras no exista hardware real, cada uno se demuestra **en el simulador** y debe quedar
etiquetado en pantalla igual que el riego actual («SIMULADOR · LAS ÓRDENES NO ACTIVAN
HARDWARE»). Regla que ya se respeta en `ControlActivity`: **el estado mostrado siempre es
el que confirma el nodo**, nunca un cambio optimista local.

### F4 — Extensión del protocolo — compatible hacia atrás

```text
TELEMETRIA|TEMP:3.0C|HUM:88%|SUELO:22%|RELE:APAGADO|MODO:AUTO|ESTADO:HELADA_CRITICA|SENSOR:OK|RIESGOS:TEMP_CRITICO,HUM_ALTA,SUELO_SECO
```

- Mantener ASCII, salto de línea `\n` y máximo 512 bytes por línea.
- `SUELO` y `RIESGOS` son opcionales: el parser los acepta si están y tolera que falten.
- `RIESGOS` lista los factores disparados, separados por coma, calculados por el nodo.
- Nuevos ACK: `ACK:S1`, `ACK:V`, etc., más `ERROR:COMANDO_INVALIDO` para lo no implementado.
- Actualizar `docs/PROTOCOLO_WIFI.md` (es el contrato con el futuro módulo Wi-Fi).

## 4. Impacto por archivo

| Archivo | Cambio necesario |
|---|---|
| `Protocol.kt` | Nuevos campos opcionales (`SUELO`, `RIESGOS`), umbrales centralizados, factor de riesgo compuesto |
| `DashboardActivity.kt` | Banner de alerta crítica, acceso a la pantalla de alertas, dejar de duplicar umbrales en el texto |
| `AlertsActivity.kt` *(nuevo)* | Pantalla de riesgos por factor, con color y valor |
| `ControlActivity.kt` | Controles por zona/ventilación, avisos de causa crítica, estado real confirmado |
| `NodeConnection.kt` | Validación de nuevos comandos y de nuevos ACK |
| `simulator/server.py` | Simular suelo, riesgos nuevos, comandos extra y flags CLI (`--soil`, `--scenario`) |
| `simulator/test_server.py` | Casos de prueba para la extensión del protocolo |
| `docs/PROTOCOLO_WIFI.md` | Trama extendida y comandos nuevos |
| `docs/REQUISITOS.md` | Tracedad de la ampliación, separada de los requisitos del informe |
| `README.md` | Mención de la pantalla nueva y del nuevo hardware |

## 5. Orden sugerido de trabajo

1. **F1 + F2 en el simulador** (se ve en la demo sin hardware nuevo): banner de alerta
   crítica, pantalla de alertas, campo `SUELO` y escenario de tierra seca.
2. **F4** documentada en `PROTOCOLO_WIFI.md` para que el firmware futuro la implemente.
3. **F3** por partes, empezando por zonas de riego, con el hardware real cuando se decida
   el módulo Wi-Fi.

## 6. Antes de implementar: confirmar con el docente

- El **informe ya está entregado**. Estas funciones son **ampliación**, no requisitos del
  informe: hay que decidir si se presentan como «trabajo futuro» o si se amplía el
  alcance y se actualiza el informe.
- La ampliación **no debe romper la trazabilidad** de `docs/REQUISITOS.md`: lo que hoy
  cumple el informe sigue igual, lo nuevo va aparte.
- Mantener la etiqueta visible de «SIMULACIÓN · NO SON MEDICIONES DEL HUERTO» mientras no
  exista sensor real; es parte de la honestidad del prototipo.
- Cada control nuevo que se anuncie en la demo debe existir en el simulador, aunque el
  hardware real todavía no esté decidido.

## 7. Estado

- [ ] F1 Pantalla de alertas y banner de riesgo crítico
- [ ] F2 Humedad de suelo en protocolo y simulador
- [ ] F3 Controles adicionales del huerto (zonas, ventilación, programación)
- [ ] F4 Protocolo extendido documentado
- [ ] Confirmación del docente sobre el alcance de la ampliación
