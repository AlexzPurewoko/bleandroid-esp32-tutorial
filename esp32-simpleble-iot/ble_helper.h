#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <EEPROM.h>

#define MEM_SAVED 0
#define BLE_DEVICENAME "D01"

/**
 * Universally Unique Identifier (UUID)
 * 
 * Is an unique ID's to idetifying the devices.
 * In this case, we will use to identify Services, Characteristics, and Advertising
 * These value is unique, mean that any devices should be different from others
 * 
 **/
#define ADVERTISING_UUIDS "3ce05adf-2126-49ed-adca-fb29b1995378" // For advertising ID, identifies what the peripheral usages for in central devices
#define SENSOR_SERVICE "71e9d6df-17fd-4a06-bf06-d07387e7fcd6" // For sensor service
#define TEMP_CHARACTERISTIC "bafe94d0-4461-4fd1-b8e6-ce30bfb522e5" // For temperature characteristics
#define SETTINGS_CHARACTERISTIC "14b0a352-7384-48a0-8e4c-bb7936f8e96e" // For Settings Characteristics
#define HUM_CHARACTERISTIC "edbec4d4-ab2c-4464-b837-94ab1d08db68" // For Humidity Characteristics

/**
 * Reserved fields 
 */

/**
 * Field @see deviceConnected is used to define the current state of connect or not.
 **/
bool deviceConnected = false;

/**
 * Field @see oldConnected is used to define previous connection
 **/
bool oldConnected = false;

/**
 * Map all instance to a variable
 **/
BLEServer *bleServer = NULL;
BLEService *bleSensorService = NULL;
BLECharacteristic *bleTempChara = NULL;
BLECharacteristic *bleHumChara = NULL;
BLECharacteristic *bleSettingChara = NULL;

/**
 * Template Functions 
 */

void setDuration(int);
int getDuration();
void blinkLamp(int);

/**
 * Callback Implementations
 */

// This callback called when BLE Device is connected or disconnected
class SimpleBLECb : public BLEServerCallbacks
{
  void onConnect(BLEServer *pServer)
  {
    blinkLamp(2);
    deviceConnected = true;
  };

  void onDisconnect(BLEServer *pServer)
  {
    blinkLamp(2);
    deviceConnected = false;
  }
};

/**
 * An implementation for BLECharacteristicCallback
 * Which in here, we can receive any particular data from Central Devices
 * 
 */
class ChangePeriodCallbacks : public BLECharacteristicCallbacks
{

  // onWrite callbacks called when receiving data from other ble client's device
  void onWrite(BLECharacteristic *characteristic)
  {

    // get stored value from characteristic
    std::string value = characteristic->getValue();
    int receivedDuration = atoi(value.c_str());
    Serial.println(receivedDuration);

    if (receivedDuration < 1 && receivedDuration > 10)
    {

      // sends callback false because in wrong range
      characteristic->setValue("false");
    }
    else
    {

      // write and sends callback into true
      characteristic->setValue("true");
      setDuration(receivedDuration);
    }

    // notify the other devices
    characteristic->notify();
  }

/**
 * 
 * This callback will called if central devices request to read the characteristics
 * 
 */
  void onRead(BLECharacteristic *pCharacteristic)
  {
    char buff[2];
    buff[0] = getDuration() + '0';
    buff[1] = '\0';
    Serial.println(buff);
    pCharacteristic->setValue(buff);
  }
};
/**
 * Duration to performing operation data are stored in EEPROM
 * Which can guarantee by users/tester to change its value
 * 
 * The data are stored in memory location 0
 * which is in range 1 to 10
 */
int getDuration()
{
  return EEPROM.read(MEM_SAVED);
}

void setDuration(int duration)
{
  if (getDuration() == duration)
    return;
  EEPROM.write(MEM_SAVED, duration);
  EEPROM.commit();
}


/**
 * Helper methods to send/notify value from specified characteristics
 * 
 * @param ch The BLE Characteristics
 * @param value The string to send. Which the length shouldn't greater than 20
 * @return void
 */ 
void notify(BLECharacteristic *ch, char *value)
{
  if (!value)
    return;
  ch->setValue(value);
  ch->notify();
}

/**
 * Helper methods to initialize the Bluetooth Low Energy Service
 * Which initilizing advertising, characteristics, descriptor, GATT and more
 */
void initializeBLE()
{
  // initialize ble with specific names
  BLEDevice::init(BLE_DEVICENAME);

  // Create a service to handle 2 characteristics
  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new SimpleBLECb());

  bleSensorService = bleServer->createService(SENSOR_SERVICE);

  bleHumChara = bleSensorService->createCharacteristic(
      HUM_CHARACTERISTIC,
      BLECharacteristic::PROPERTY_NOTIFY);

  bleTempChara = bleSensorService->createCharacteristic(
      TEMP_CHARACTERISTIC,
      BLECharacteristic::PROPERTY_NOTIFY);

  bleSettingChara = bleSensorService->createCharacteristic(
      SETTINGS_CHARACTERISTIC,
      BLECharacteristic::PROPERTY_NOTIFY |
          BLECharacteristic::PROPERTY_WRITE |
          BLECharacteristic::PROPERTY_READ);

  // adding a callbacks for setting a periode sending data measurements
  // and a descriptor to perform I/O operation on a characteristics
  bleSettingChara->setCallbacks(new ChangePeriodCallbacks());
  bleSettingChara->addDescriptor(new BLE2902());

  bleTempChara->addDescriptor(new BLE2902());
  bleHumChara->addDescriptor(new BLE2902());

  // Start a BLE service
  bleSensorService->start();

  // Initialize, setting an option and start the advertising
  // So, another device can find this device.
  BLEAdvertising *_advertising = BLEDevice::getAdvertising();
  _advertising->addServiceUUID(ADVERTISING_UUIDS);
  _advertising->setScanResponse(false);
  _advertising->setMinPreferred(0x00);

  BLEDevice::startAdvertising();
}

/**
 * Initialize the lamp indicator
 * Which the lamp is embedded in ESP32.
 **/ 
void initializeLampIndicator() {
  pinMode(2, OUTPUT);
}

/**
 * Blink the lamp with the count
 * 
 * @param count indicates the count of the lamp should be blinked
 **/
void blinkLamp(int count){
  for(int i = 0; i < count; i++){
    digitalWrite(2, HIGH);
    delay(400);
    digitalWrite(2, LOW);
    delay(400);
  }
}