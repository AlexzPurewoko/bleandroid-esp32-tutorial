#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <EEPROM.h>

#define MEM_SAVED 0
#define BLE_DEVICENAME "D01"

// UUIDS
#define ADVERTISING_UUIDS "3ce05adf-2126-49ed-adca-fb29b1995378"
#define SENSOR_SERVICE "71e9d6df-17fd-4a06-bf06-d07387e7fcd6"
#define TEMP_CHARACTERISTIC "bafe94d0-4461-4fd1-b8e6-ce30bfb522e5"
#define SETTINGS_CHARACTERISTIC "14b0a352-7384-48a0-8e4c-bb7936f8e96e"
#define HUM_CHARACTERISTIC "edbec4d4-ab2c-4464-b837-94ab1d08db68"
/**
 * Reserved fields 
 */

bool deviceConnected = false;
bool oldConnected = false;
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

/**
 * Callback Implementations
 */

// This callback called when BLE Device is connected or disconnected
class SimpleBLECb : public BLEServerCallbacks
{
  void onConnect(BLEServer *pServer)
  {
    deviceConnected = true;
  };

  void onDisconnect(BLEServer *pServer)
  {
    deviceConnected = false;
  }
};

// This callback called when any changes in characteristics
// The changes related to sychronize duration.
class ChangePeriodCallbacks : public BLECharacteristicCallbacks
{

  // onWrite callbacks called when receiving data from other ble client's device
  void onWrite(BLECharacteristic *characteristic)
  {

    // get stored value from characteristic
    std::string value = characteristic->getValue();
    int receivedDuration = atoi(value.c_str());

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

  void onRead(BLECharacteristic *pCharacteristic)
  {
    char buff[2];
    buff[0] = getDuration();
    buff[1] = '\0';
    pCharacteristic->setValue(buff);
  }
};

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

void notify(BLECharacteristic *ch, char *value)
{
  if (!value)
    return;
  ch->setValue(value);
  ch->notify();
}

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
