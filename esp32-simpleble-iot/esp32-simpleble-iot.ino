/** 
 * By @2020 Alexzander Purwoko Widiantoro <purwoko908@gmail.com>
 */
#include "ble_helper.h"
#include <DHT_U.h>
#include <DHT.h>

#define DEFAULT_DURATION 2 // in seconds

DHT dhtSensor(23, DHT22);
char buff[12];
int hum, temp;
void setup()
{
  // put your setup code here, to run once:
  Serial.begin(115200);
  dhtSensor.begin();
  EEPROM.begin(1);

  setDuration(DEFAULT_DURATION);
  if (getDuration() == 0)
  {
    setDuration(DEFAULT_DURATION);
  }

  initializeBLE();
}

void loop()
{
  // put your main code here, to run repeatedly:

  // flush out to client device if its already connected.
  if (deviceConnected)
  {
    hum = dhtSensor.readHumidity();
    temp = dhtSensor.readTemperature();

    gcvt(temp, 10, buff);
    notify(bleTempChara, buff);

    gcvt(hum, 10, buff);
    notify(bleHumChara, buff);
  }

  // used for managing the bluetooth connection
  // if the client's is disconnect, it can start
  // advertiser to make it found in next scan
  if (!deviceConnected && oldConnected)
  {

    bleServer->startAdvertising();
    oldConnected = deviceConnected;
    Serial.println("Start Advertising... Looking for connect!");
  }

  // copy new value to oldConnected
  if (deviceConnected && !oldConnected)
  {
    oldConnected = deviceConnected;
    Serial.print("Device Connected : ");
    Serial.print(oldConnected ? "True" : "False");
    Serial.print("\n");
  }

  delay(getDuration() * 1000);
}
