/*
 * ===========================================================================
 *  PROYECTO: AGROALERT ARAUCANIA - SISTEMA IOT DE PREVENCION DE HELADAS
 *  ASIGNATURA: TI3042 - Aplicaciones Moviles para IoT (INACAP)
 *  ESTUDIANTES: Sebastian Torres - Dario Candia
 *  PINOUT: DHT11=D2 | LEDs=D3,D4,D5 | Rele=D8 (Active LOW) | HC-05=D10,D11
 *
 *  Verificado compilado para Arduino Uno R3: 10.128 bytes (31% flash),
 *  456 bytes RAM (22%). Fuente: web oficial del proyecto (metodo millis()).
 * ===========================================================================
 */

#include <SoftwareSerial.h>
#include <DHT.h>

// DEFINICION DE PINES HARDWARE
const int PIN_DHT11 = 2;        // Sensor de Temperatura y Humedad DHT11
const int PIN_LED_VERDE = 3;    // LED Verde: Microclima Seguro (> 10 C)
const int PIN_LED_AMARILLO = 4; // LED Amarillo: Alerta Preventiva (5-10 C)
const int PIN_LED_ROJO = 5;     // LED Rojo: Alerta Critica de Helada (<= 5 C)
const int PIN_RELE_BOMBA = 8;   // Modulo Rele 5V (Active LOW: LOW activa, HIGH apaga)
const int PIN_BT_RX = 10;       // Conectado a TXD del HC-05
const int PIN_BT_TX = 11;       // Conectado a RXD del HC-05

// UMBRALES TERMICOS DE LA LAMINA OFICIAL
const float UMBRAL_SEGURO = 10.0;
const float UMBRAL_HELADA = 5.0;
#define DHTTYPE DHT11

SoftwareSerial bluetooth(PIN_BT_RX, PIN_BT_TX);
DHT dht(PIN_DHT11, DHTTYPE);

float temperatura = 0.0;
float humedadAire = 0.0;
bool modoManual = false;
bool estadoRele = false; // false = Apagado (HIGH), true = Activo (LOW)

unsigned long tiempoPrevio = 0;
const long INTERVALO_MUESTREO = 2000; // Muestreo cada 2.0 s

void setup() {
  pinMode(PIN_LED_VERDE, OUTPUT);
  pinMode(PIN_LED_AMARILLO, OUTPUT);
  pinMode(PIN_LED_ROJO, OUTPUT);
  pinMode(PIN_RELE_BOMBA, OUTPUT);
  // ESTADO INICIAL SEGURO (FAIL-SAFE OT)
  digitalWrite(PIN_RELE_BOMBA, HIGH);
  digitalWrite(PIN_LED_VERDE, HIGH);
  digitalWrite(PIN_LED_AMARILLO, LOW);
  digitalWrite(PIN_LED_ROJO, LOW);
  Serial.begin(9600);
  bluetooth.begin(9600);
  dht.begin();
  Serial.println(F("======================================================"));
  Serial.println(F(" AGROALERT ARAUCANIA - SISTEMA IOT ANTI-HELADAS "));
  Serial.println(F(" Pines: DHT11=D2 | LEDs=D3,D4,D5 | Rele=D8 | BT=D10,D11"));
  Serial.println(F("======================================================"));
  bluetooth.println(F("=== AGROALERT ARAUCANIA CONECTADO ==="));
  bluetooth.println(F("Comandos: '1'=Encender Rele, '0'=Apagar Rele, 'A'=Modo Auto"));
}

void loop() {
  if (bluetooth.available() > 0) {
    char cmd = bluetooth.read();
    procesarComando(cmd);
  }
  if (Serial.available() > 0) {
    char cmd = Serial.read();
    procesarComando(cmd);
  }
  unsigned long tiempoActual = millis();
  if (tiempoActual - tiempoPrevio >= INTERVALO_MUESTREO) {
    tiempoPrevio = tiempoActual;
    leerSensores();
    if (!modoManual) {
      evaluarLogicaHelada();
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
  } else {
    Serial.println(F("[ADVERTENCIA OT] Sensor DHT11 no responde o cable suelto"));
    digitalWrite(PIN_LED_AMARILLO, HIGH);
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
      modoManual = true;
      accionarRele(true);
      digitalWrite(PIN_LED_ROJO, HIGH);
      digitalWrite(PIN_LED_VERDE, LOW);
      Serial.println(F("[REMOTO] Rele forzado a ENCENDIDO (Manual)"));
      bluetooth.println(F("RELE:ENCENDIDO_MANUAL"));
      break;
    case '0':
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
  String trama = "TELEMETRIA|TEMP:" + String(temperatura, 1) + "C" +
    "|HUM:" + String(humedadAire, 0) + "%" +
    "|RELE:" + (estadoRele ? "ACTIVADO" : "APAGADO") +
    "|MODO:" + (modoManual ? "MANUAL" : "AUTO") +
    "|ESTADO:" + (temperatura <= 5.0 ? "HELADA_CRITICA" : (temperatura <= 10.0 ? "PRE_HELADA" : "OK"));
  Serial.println(trama);
  bluetooth.println(trama);
}
