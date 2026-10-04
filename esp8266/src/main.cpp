#include <Arduino.h>
#include <TM1637Display.h>

#include <LM75A.h>
LM75A tempSensor;

#define CLK 5 // D1
#define DIO 4 // D2

TM1637Display display(CLK, DIO);

void setup() {
 display.setBrightness(7);
 display.clear();

 Serial.begin(115200);
}

void loop() {
  float temp = tempSensor.getTemperatureInDegrees();
  Serial.println(temp, 2);
  delay(100);

  display.showNumberDec(temp);
  delay(1000);
}