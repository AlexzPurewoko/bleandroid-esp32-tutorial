package id.apwdevs.simpleble

import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _humidity = MutableLiveData<String>()
    private val _temperature = MutableLiveData<String>()
    private val _seekProgress = MutableLiveData<Int>()

    private val _saveDeliveredStatus = MutableLiveData<Boolean>()
    private val _connectionStatus = MutableLiveData<Boolean>()

    // indicate whether the process is on-the-way
    private val _isProcessing: MutableLiveData<Boolean> = MutableLiveData()

    val humidity: LiveData<String> = _humidity
    val temperature: LiveData<String> = _temperature
    val seekProgress: LiveData<Int> = _seekProgress

    val saveDeliveredStatus: LiveData<Boolean> = _saveDeliveredStatus
    val connectionStatus: LiveData<Boolean> = _connectionStatus
    val isProcessing: LiveData<Boolean> = _isProcessing


    companion object {

        // defined constants
        private val ADVERTISING_UUIDS: UUID =
            UUID.fromString("3ce05adf-2126-49ed-adca-fb29b1995378")
        val SENSOR_SERVICE_UUIDS: UUID =
            UUID.fromString("71e9d6df-17fd-4a06-bf06-d07387e7fcd6")

        val DEVICE_SERVICE_UUIDS: UUID = UUID.fromString("7fb4ca4b-42a6-4118-a316-6195481118f9")
        val TEMP_CHARACTERISTIC: UUID =
            UUID.fromString("bafe94d0-4461-4fd1-b8e6-ce30bfb522e5")
        val SETTINGS_CHARACTERISTIC: UUID =
            UUID.fromString("14b0a352-7384-48a0-8e4c-bb7936f8e96e")
        val HUM_CHARACTERISTIC: UUID = UUID.fromString("edbec4d4-ab2c-4464-b837-94ab1d08db68")


        val BLE2902_UUIDS: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    // Generic bluetooth adapter
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    // true if this device is support BLE
    private val isBLESupported: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        application.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    private var userDisconnect: Boolean = false
    private var connectedGatt: BluetoothGatt? = null

    fun connect() {
        userDisconnect = false
        if (!(isBLESupported && bluetoothAdapter?.isEnabled == true)) return
        if (connectedGatt == null) {
            GlobalScope.launch(Dispatchers.Main) {
                _isProcessing.postValue(true)
                val retDevices = scanDevicesAsync().await()

                if(retDevices != null){
                    val isConnected = connectDevicesAsync(retDevices).await()
                    Log.d("BLEViewModel", "Device is connected ? : $isConnected")
                    _connectionStatus.postValue(isConnected)
                }
                // process had been done, so automatically pick by the first list
                _isProcessing.postValue(false)
            }
        } else {
            GlobalScope.launch {
                _isProcessing.postValue(true)

                val isConnected =
                    connectedGatt?.connect() ?: false
                delay(500)
                _connectionStatus.postValue(isConnected)
                _isProcessing.postValue(false)
            }

        }
    }

    fun saveDuration(progress: Int) {
        connectedGatt?.apply {
            _isProcessing.postValue(true)
            val settings = getService(SENSOR_SERVICE_UUIDS).getCharacteristic(SETTINGS_CHARACTERISTIC)
            settings.value = "$progress".toByteArray()
            writeCharacteristic(settings)
        }
    }

    fun disconnect() {
        userDisconnect = true
        connectedGatt?.disconnect()
    }

    fun release() {
        disconnect()
        connectedGatt?.close()
        connectedGatt = null
    }

    private fun scanDevicesAsync() : Deferred<BluetoothDevice?> = GlobalScope.async {
        var bleDevice: BluetoothDevice? = null

        val bleScanner = bluetoothAdapter?.bluetoothLeScanner
        val scanSettings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        val scanFilter = listOf<ScanFilter>(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(ADVERTISING_UUIDS))
                .build()
        )
        var isFound = false

        val scanCb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.apply {
                    isFound = true
                    bleDevice = this
                }

            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(
                    "BluetoothHandler", "ERROR -> ${
                        when (errorCode) {
                            SCAN_FAILED_ALREADY_STARTED -> "SCAN_FAILED_ALREADY_STARTED"
                            SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
                            SCAN_FAILED_FEATURE_UNSUPPORTED -> "SCAN_FAILED_FEATURE_UNSUPPORTED"
                            SCAN_FAILED_INTERNAL_ERROR -> "SCAN_FAILED_INTERNAL_ERROR"
                            else -> "UNIDENTIFIED"
                        }
                    }"
                )
            }
        }

        bleScanner?.startScan(scanFilter, scanSettings, scanCb)
        for (x in 100..5000 step 100) {
            if (!isActive || isFound) {
                break
            }
            delay(100)
        }
        bleScanner?.stopScan(scanCb)
        bleDevice
    }

    private fun connectDevicesAsync(device: BluetoothDevice) : Deferred<Boolean> = GlobalScope.async{
        var isConnected = false
        var isFinished = false
        val gattConnectionCallback = object : BluetoothGattCallback() {
            private var tryCount = 0
            private val tryMax = 3
            // First, discover the connection state
            // if connected, then discover the services available on the target devices
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        connectedGatt = gatt
                        //isSelectedDeviceConnected = true
                        //runTask {
                            //isProcessing.postValue(true)
                            //currentLoadingState.postValue("Discovering Services!")
                            gatt?.discoverServices()
                        isConnected = true
                        //}
                    }
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        //isSelectedDeviceConnected = false
                        isConnected = false
                    }
                }
            }

            // Discover the Services
            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if(status == BluetoothGatt.GATT_SUCCESS){
                    gatt?.apply {
                        tryCount = 0
                        launch {

                            discoverThisServiceAsync(gatt).await()
                            isFinished = true
                        }
                    }
                } else {
                    if(++tryCount <= tryMax){
                        gatt?.connect()
                    } else {
                        isFinished = true
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                characteristic?.apply {
                    when(uuid) {
                        TEMP_CHARACTERISTIC -> {
                            // post to viewModel
                            _temperature.postValue(String(value))
                        }

                        HUM_CHARACTERISTIC -> {
                            // post to viewModel
                            _humidity.postValue(String(value))
                        }

                        SETTINGS_CHARACTERISTIC -> {
                            // post to viewModel
                            _isProcessing.postValue(false)
                            _saveDeliveredStatus.postValue(String(value).toBoolean())
                        }
                    }
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if(characteristic?.uuid == SETTINGS_CHARACTERISTIC) {
                    val h = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    Toast.makeText(getApplication(), "READ from characteristics -> $h", Toast.LENGTH_LONG).show()
                    _seekProgress.postValue(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0))
                }
            }
        }

        if (connectedGatt == null) {
            device.connectGatt(getApplication(), false, gattConnectionCallback)
        } else {
            connectedGatt?.connect()
        }

        for (x in 100..10000 step 100) {
            if (!isActive || isFinished) {
                break
            }
            delay(100)
        }

        isConnected
    }

    private fun discoverThisServiceAsync(gatt: BluetoothGatt): Deferred<Boolean> = GlobalScope.async{
        val sensorService = gatt.getService(SENSOR_SERVICE_UUIDS)

        // observe all characteristics on this service..
        for (characteristics in sensorService.characteristics){
            while (!enableNotificationValue(characteristics, gatt)) delay(500)
        }

        // read the current value of seek duration
        // and the value will retrieved by callbacks
        gatt.readCharacteristic(
            sensorService.getCharacteristic(SETTINGS_CHARACTERISTIC)
        )

        true
    }

    private fun enableNotificationValue(characteristic: BluetoothGattCharacteristic, gatt: BluetoothGatt): Boolean {
        characteristic.apply {
            Log.e("NOTIF_CHARA_ENABLE", "Enabling Characteristics value of UUID -> $uuid")
            val result = gatt.setCharacteristicNotification(this, true)
            val desc = getDescriptor(BLE2902_UUIDS).apply {
                value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            val resultDesc = gatt.writeDescriptor(desc)

            return result && resultDesc
        }
    }
}