package id.apwdevs.simpleble

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_PERMISSION_LOCATION = 0x22a
        const val REQUEST_ENABLE_BT = 0x22F
    }
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleConnect.isEnabled = false

        val hasFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        if(hasFeature) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                reqPermission()
            }
            else
                enableBt()
        } else {
            Toast.makeText(this, "Your device doesn't support BLE features", Toast.LENGTH_LONG).show()
        }

        initialize()
    }

    override fun onDestroy() {
        mainViewModel.release()
        super.onDestroy()
    }

    private fun initialize() {
        mainViewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application)).get(MainViewModel::class.java)
        mainViewModel.temperature.observe(this) {
            temperature.text = "$it C"
        }

        mainViewModel.humidity.observe(this) {
            humidity.text = "$it %"
        }

        mainViewModel.isProcessing.observe(this) {
            toggleConnect.isEnabled = !it
            sendDuration.isEnabled = !it
            statusSaveText.text = ""
            statusDevice.text = if(it) "Connecting" else statusDevice.text
        }

        mainViewModel.connectionStatus.observe(this) {
            statusDevice.text = if(it) "Connected!" else "Disconnected!"
            toggleConnect.text = if(it) "Disconnect" else "Connect"
            toggleConnect.tag = it
        }

        mainViewModel.saveDeliveredStatus.observe(this) {
            statusSaveText.text = if(it) "Success" else "Failed"
        }

        mainViewModel.seekProgress.observe(this) {
            seekDuration.progress = it
        }

        seekDuration.apply {
            max = 10
            progress = 1
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    seekIndicator.text = "${progress}s"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }

            })
        }

        sendDuration.setOnClickListener {
            mainViewModel.saveDuration(seekDuration.progress)
        }



        toggleConnect.setOnClickListener {
            if(toggleConnect.tag == true){
                // disconnect...
                mainViewModel.disconnect()
                statusDevice.text = "Disconnected"
                toggleConnect.tag = false
                toggleConnect.text = "Connect"
            } else {
                mainViewModel.connect()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            // do a connect
            if (resultCode == Activity.RESULT_OK) {
                permitConnect()
            } else {
                enableBt()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableBt()
            } else {
                Toast.makeText(this,
                    "You cannot use this feature. Please enable default in app setting!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @RequiresApi(23)
    private fun reqPermission() {
        Utils.displayDialogRequest(this, "Request Permission", "Please Click Enable to run this app!"){
            requestPermissions(
                arrayOf(
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_FINE_LOCATION"
                ),
                REQUEST_PERMISSION_LOCATION
            )
        }

    }

    private fun enableBt() {
        if (!getBluetoothAdapter(this).isEnabled) {
            Toast.makeText(this, "Enabling Bluetooth..", Toast.LENGTH_LONG).show()
            Utils.displayDialogRequest(this, "Enable Bluetooth", "Please click allow to enable your bluetooth and enjoy this application"){
                startActivityForResult(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    REQUEST_ENABLE_BT
                )
            }

        } else {
            permitConnect()
        }
    }

    private fun enableGPS() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            Utils.displayDialogRequest(this, "Enable Location", "Please Enable your location and then click back."){
                startActivity(
                    Intent(
                        Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    )
                )
            }
        }


    }

    fun getBluetoothAdapter(context: Context): BluetoothAdapter {
        return (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private fun permitConnect() {
        enableGPS()
        toggleConnect.isEnabled = true
    }
}