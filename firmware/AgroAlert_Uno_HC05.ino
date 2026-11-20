/*
 * ===========================================================================
 *  PROYECTO: AGROALERT ARAUCANIA - SISTEMA IOT DE PREVENCION DE HELADAS
 *  ASIGNATURA: TI3042 - Aplicaciones Moviles para IoT (INACAP)
 *  ESTUDIANTES: Sebastian Torres - Dario Candia
 *  PINOUT: DHT11=D2 | LEDs=D3,D4,D5 | Rele=D8 (Active LOW) | HC-05=D10,D11
 *
 *  VERSION 1.4 (reparacion del enlace Bluetooth):
 *  - Negociacion automatica de baud del HC-05: prueba 38400 y 9600 hasta que
 *    la app confirma con '!'. Antes estaba fijo en 9600 y, con modulos que
 *    vienen a 38400, el celular nunca recibia telemetria valida.
 *  - No transmite telemetria hasta confirmar el baud (evita tramas basura).
 *
 *  VERSION 1.3 (corregida tras retroalimentacion del docente):
 *  - Fail-safe de sensor: sensorValido parte en false; si el DHT11 no
 *    responde, la trama reporta SENSOR:ERROR con TEMP:NA/HUM:NA y nunca
 *    reutiliza datos viejos. La app ya validaba ese caso (Protocol.kt).
 *  - Rele inicializado en HIGH (apagado, active LOW) antes de habilitar
 *    la logica: el sistema nunca arranca regando solo.
 *  - Ordenes manuales rechazadas con ERROR:SENSOR_ERROR si no hay sensor.
 *  - La logica termica con millis() sigue autonoma del enlace: base del
 *    plan de continuidad operativa.
 *  Verificado compilado para Arduino Uno R3 (IDE 1.8 + avr-gcc).
 * ===========================================================================
 */

#include <SoftwareSerial.h>
#include <DHT.h>

const int PIN_DHT11        = 2;
const int PIN_LED_VERDE    = 3;
const int PIN_LED_AMARILLO = 4;
const int PIN_LED_ROJO     = 5;
const int PIN_RELE_BOMBA   = 8;
const int PIN_BT_RX        = 10;
const int PIN_BT_TX        = 11;

const float UMBRAL_SEGURO = 10.0;
const float UMBRAL_HELADA = 5.0;
const long BAUDS[] = {38400, 9600}; // fabrica: la mayoria de HC-05 clonados usa 38400
const int NUM_BAUDS = 2;
#define DHTTYPE DHT11

SoftwareSerial bluetooth(PIN_BT_RX, PIN_BT_TX);
DHT dht(PIN_DHT11, DHTTYPE);

float temperatura = 0.0;
float humedadAire = 0.0;
bool sensorValido = false; // false hasta la primera lectura valida: nunca operar con datos viejos
bool modoManual = false;
bool estadoRele = false;   // false = Apagado (HIGH), true = Activo (LOW)

unsigned long tiempoPrevio = 0;
const long INTERVALO_MUESTREO = 2000; // Muestreo cada 2.0 s
int baudIndex = 0;
int baudIntentos = 0;
bool baudConfirmado = false;
unsigned long baudTiempo = 0;

void setup() {
  pinMode(PIN_LED_VERDE, OUTPUT);
  pinMode(PIN_LED_AMARILLO, OUTPUT);
  pinMode(PIN_LED_ROJO, OUTPUT);
  pinMode(PIN_RELE_BOMBA, OUTPUT);
  // ESTADO INICIAL SEGURO (FAIL-SAFE): rele apagado y alarma preventiva
  digitalWrite(PIN_RELE_BOMBA, HIGH);
  digitalWrite(PIN_LED_VERDE, LOW);
  digitalWrite(PIN_LED_AMARILLO, HIGH);
  digitalWrite(PIN_LED_ROJO, LOW);
  Serial.begin(9600);
  bluetooth.begin(BAUDS[0]);
  dht.begin();
  Serial.println(F("======================================================"));
  Serial.println(F(" AGROALERT ARAUCANIA - SISTEMA IOT ANTI-HELADAS v1.4 "));
  Serial.println(F(" Pines: DHT11=D2 | LEDs=D3,D4,D5 | Rele=D8 | BT=D10,D11"));
  Serial.println(F("======================================================"));
}

// Prueba 38400 y 9600; la app responde '!' al '?' cuando el baud coincide.
// Tras 6 intentos sigue con el actual para no dejar sin datos a una app antigua.
void negociarBaud() {
  if (baudConfirmado || millis() - baudTiempo < 700) return;
  baudTiempo = millis();
  while (bluetooth.available() > 0) {
    if ((char)bluetooth.read() == '!') {
      baudConfirmado = true;
      bluetooth.println(F("=== AGROALERT ARAUCANIA CONECTADO ==="));
      bluetooth.println(F("Comandos: '1'=Encender Rele, '0'=Apagar Rele, 'A'=Modo Auto"));
      return;
    }
  }
  if (++baudIntentos >= 6) {
    baudConfirmado = true;
    Serial.println(F("[BT] Baud fijado tras 6 intentos"));
    return;
  }
  baudIndex = (baudIndex + 1) % NUM_BAUDS;
  bluetooth.end();
  bluetooth.begin(BAUDS[baudIndex]);
  bluetooth.print('?');
}

void loop() {
  negociarBaud();
  if (bluetooth.available() > 0) {
    procesarComando(bluetooth.read());
  }
  if (Serial.available() > 0) {
    procesarComando(Serial.read());
  }
  unsigned long tiempoActual = millis();
  if (tiempoActual - tiempoPrevio >= INTERVALO_MUESTREO) {
    tiempoPrevio = tiempoActual;
    leerSensores();
    if (sensorValido) {
      if (!modoManual) {
        evaluarLogicaHelada();
      }
    } else {
      // CONTINGENCIA: sin sensor, rele apagado y alarma amarilla fija
      digitalWrite(PIN_LED_ROJO, LOW);
      digitalWrite(PIN_LED_VERDE, LOW);
      digitalWrite(PIN_LED_AMARILLO, HIGH);
      accionarRele(false);
    }
    transmitirTelemetria();
  }
}

void leerSensores() {
  float tempLeida = dht.readTemperature();
  float humLeida = dht.readHumidity();
  if (!isnan(tempLeida) && !isnan(humLeida)) {
    temperatura = tempLeida;
    humedadAire = humLeida;
    sensorValido = true;
  } else {
    sensorValido = false;
    Serial.println(F("[CONTINGENCIA] Sensor DHT11 no responde o cable suelto"));
  }
}

void evaluarLogicaHelada() {
  if (temperatura <= UMBRAL_HELADA) {
    digitalWrite(PIN_LED_ROJO, HIGH);
    digitalWrite(PIN_LED_AMARILLO, LOW);
    digitalWrite(PIN_LED_VERDE, LOW);
    accionarRele(true); // Activa bomba de aspersion anti-helada
  } else if (temperatura <= UMBRAL_SEGURO) {
    digitalWrite(PIN_LED_ROJO, LOW);
    digitalWrite(PIN_LED_AMARILLO, HIGH);
    digitalWrite(PIN_LED_VERDE, LOW);
    accionarRele(false);
  } else {
    digitalWrite(PIN_LED_ROJO, LOW);
    digitalWrite(PIN_LED_AMARILLO, LOW);
    digitalWrite(PIN_LED_VERDE, HIGH);
    accionarRele(false);
  }
}

void accionarRele(bool activar) {
  estadoRele = activar;
  digitalWrite(PIN_RELE_BOMBA, activar ? LOW : HIGH);
}

void procesarComando(char cmd) {
  switch (cmd) {
    case '1':
      if (!sensorValido) {
        bluetooth.println(F("ERROR:SENSOR_ERROR"));
        Serial.println(F("[CONTINGENCIA] Orden rechazada: sin lectura del sensor"));
        break;
      }
      modoManual = true;
      accionarRele(true);
      digitalWrite(PIN_LED_ROJO, HIGH);
      digitalWrite(PIN_LED_VERDE, LOW);
      Serial.println(F("[REMOTO] Rele forzado a ENCENDIDO (Manual)"));
      bluetooth.println(F("RELE:ENCENDIDO_MANUAL"));
      break;
    case '0':
      if (!sensorValido) {
        bluetooth.println(F("ERROR:SENSOR_ERROR"));
        Serial.println(F("[CONTINGENCIA] Orden rechazada: sin lectura del sensor"));
        break;
      }
      modoManual = true;
      accionarRele(false);
      digitalWrite(PIN_LED_ROJO, LOW);
      digitalWrite(PIN_LED_VERDE, HIGH);
      Serial.println(F("[REMOTO] Rele forzado a APAGADO (Manual)"));
      bluetooth.println(F("RELE:APAGADO_MANUAL"));
      break;
    case 'A':
    case 'a':
      modoManual = false;
      Serial.println(F("[REMOTO] Modo AUTOMATICO reactivado"));
      bluetooth.println(F("MODO:AUTOMATICO"));
      break;
    default:
      break;
  }
}

void transmitirTelemetria() {
  if (!baudConfirmado) return; // nunca enviar telemetria a un baud que el celular no entiende
  String trama;
  if (sensorValido) {
    String estado = temperatura <= 5.0 ? "HELADA_CRITICA" : (temperatura <= 10.0 ? "PRE_HELADA" : "OK");
    trama = String("TELEMETRIA|TEMP:") + String(temperatura, 1) + "C" +
      "|HUM:" + String(humedadAire, 0) + "%" +
      "|RELE:" + (estadoRele ? "ACTIVADO" : "APAGADO") +
      "|MODO:" + (modoManual ? "MANUAL" : "AUTO") +
      "|ESTADO:" + estado + "|SENSOR:OK";
  } else {
    trama = String("TELEMETRIA|TEMP:NA|HUM:NA") +
      "|RELE:" + (estadoRele ? "ACTIVADO" : "APAGADO") +
      "|MODO:" + (modoManual ? "MANUAL" : "AUTO") +
      "|ESTADO:SENSOR_ERROR|SENSOR:ERROR";
  }
  Serial.println(trama);
  bluetooth.println(trama);
}
