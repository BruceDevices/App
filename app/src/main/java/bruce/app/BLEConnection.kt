package bruce.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.startActivity
import kotlinx.coroutines.delay
import java.util.UUID
import android.bluetooth.BluetoothGattDescriptor
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController

interface BLEDataReadCallback {
    fun onDataRead(service: String, char: String, data: ByteArray)
}

data class BLEDevice (
    val name: String?,
    val address: String
)

class BLEConnection {
    private lateinit var  bluetoothAdapter: BluetoothAdapter

    private val _scanResults = mutableStateListOf<BluetoothDevice>()
    private lateinit var gattDevice: BluetoothGatt
    private var valueRead = false
    private var readData: ByteArray = byteArrayOf()
    private var bleDataReadCallback: BLEDataReadCallback? = null
    private var mtu: Int = 300  // Default MTU size

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!_scanResults.contains(result.device)) {
                Log.d("BLE", "New device found ${result.device.name}")
                _scanResults.add(result.device)
            }
        }
    }

    private var connected = false

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "Connected to ${gatt.device.name}")
                gattDevice = gatt
                gatt.requestMtu(mtu)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
            super.onMtuChanged(gatt, mtu, status)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("BLE", "Services discovered")
            connected = true
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            bleDataReadCallback?.onDataRead(
                characteristic.service.uuid.toString(),
                characteristic.uuid.toString(),
                value
            )
            super.onCharacteristicRead(gatt, characteristic, value, status)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            bleDataReadCallback?.onDataRead(
                characteristic.service.uuid.toString(),
                characteristic.uuid.toString(),
                value
            )
            super.onCharacteristicChanged(gatt, characteristic, value)
        }
    }

    private val permissions =
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }.toTypedArray()


    private fun isBluetoothEnabled(context: Context): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        return bluetoothAdapter?.isEnabled == true
    }

    private fun isGPSEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }


    @Composable
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun EnableRequiredService() {
        if(!isBluetoothEnabled(LocalContext.current)) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(LocalContext.current, enableBtIntent, null)
        }

        if(!isGPSEnabled(LocalContext.current)) {
            val enableGPSIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(LocalContext.current, enableGPSIntent, null)
        }
    }

    @Composable
    private fun requestPermissions(callback: () -> Unit): ManagedActivityResultLauncher<Array<String>, Map<String, @JvmSuppressWildcards Boolean>> {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { grantedMap ->
            val allGranted = grantedMap.values.all { it }
            if (allGranted) {
                callback()
            }
        }

        return launcher
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) {
                    scanDevices()
                }
            }
        }
    }

    @Composable
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun Setup() {
        if (ActivityCompat.checkSelfPermission(
                LocalContext.current,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false,
                    dismissOnClickOutside = false),
                title = null,
                confirmButton = {},
                text = {
                    Text("Please grant Bluetooth permissions in the settings in order to use the application")
                }
            )
            return
        }

        EnableRequiredService()
        val bluetoothManager = LocalContext.current.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if(!bluetoothAdapter.isEnabled) {   // Start a callback to wait for Bluetooth to be enabled
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            LocalContext.current.registerReceiver(bluetoothReceiver, filter)
        } else {
            scanDevices()
        }
    }

    @Composable
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun Setup(navController: NavController) {
        println("Setup")
        val showDialog = remember { mutableStateOf(true) }
        val context = LocalContext.current
        val permissionsGranted = remember { mutableStateOf(false) }
        val serviceEnabled = remember { mutableStateOf(false) }

        if(permissionsGranted.value) {
            if (ActivityCompat.checkSelfPermission(
                    LocalContext.current,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                println("Bluetooth permission granted")
                EnableRequiredService()
                serviceEnabled.value = true
                scanDevices()
                navController.navigate(Pages.BLEDevicesList)
            } else {
                println("Bluetooth permission not granted")
            }
        }

        val launcher = requestPermissions  {
            permissionsGranted.value = true
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
            if(!bluetoothAdapter.isEnabled) {   // Start a callback to wait for Bluetooth to be enabled
                val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                context.registerReceiver(bluetoothReceiver, filter)
            } else {
                serviceEnabled.value = true
                scanDevices()
                navController.navigate(Pages.BLEDevicesList)
            }
        }

        if(showDialog.value) {
            AlertDialog(    // This dialog is mandatory since launcher.launch must be called from UI event
                onDismissRequest = { showDialog.value = false },
                title = { Text("Would you start BLE Scan?") },
                text = { Text("Press OK if you wanna activate BLE and start scanning") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            launcher.launch(permissions)
                            println("Laucnhereikjansd")
                            showDialog.value = false
                        }
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog.value = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    fun setDataReadCallback(callback: BLEDataReadCallback) {
        bleDataReadCallback = callback
    }


    private lateinit var scanner: BluetoothLeScanner
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun scanDevices() {
        scanner = bluetoothAdapter.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val filters = emptyList<ScanFilter>()
        scanner.startScan(filters, settings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getScanResult(): List<BLEDevice> {
        return _scanResults.map { BLEDevice(it.name, it.address) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        scanner.stopScan(scanCallback)
    }

    @Composable
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun ConnectToDevice(address: String, mtuValue: Int) {
        val filteredDevice = _scanResults.filter { it.address == address }
        if(filteredDevice.isEmpty()) {
            Log.d("BLE", "No device found in list")
            return
        }

        filteredDevice[0].connectGatt(LocalContext.current, false, gattCallback)
        mtu = mtuValue
    }

    fun isBLEConnected(): Boolean {
        return connected
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun queryDevice(service: String, char: String): ByteArray {
        gattDevice.services.forEach {
            val characteristic = it.getCharacteristic(UUID.fromString(char))
            if(characteristic != null)
                Log.d("BLE", "SERVICE: ${it.uuid}, DATA: ${gattDevice.readCharacteristic(characteristic)}")
        }
        return readData
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeToDevice(service: String, char: String, data: ByteArray) {
        val services = gattDevice.services.filter { it.uuid == UUID.fromString(service) }
        if(services.isEmpty()) {
            Log.e("BruceBLE", "Service not found")
            return
        }
        services[0].let {
            val characteristic = it.getCharacteristic(UUID.fromString(char))
            if(characteristic != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gattDevice.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                } else {    // For old device
                    characteristic.value = data
                    gattDevice.writeCharacteristic(characteristic)
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun enableNotification(service: String, char: String) {
        val serviceUUID = UUID.fromString(service)
        val charUUID = UUID.fromString(char)
        val gattService = gattDevice.getService(serviceUUID)
        val characteristic = gattService?.getCharacteristic(charUUID)
        if (characteristic != null) {
            gattDevice.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gattDevice.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gattDevice.writeDescriptor(it)
                }
            }
        } else {
            Log.e("BruceBLE", "Service not found")
        }
    }

    // Disconnect from the connected bluetooth device
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        if (this::gattDevice.isInitialized) {
            gattDevice.disconnect()
            gattDevice.close()
        }
    }
}

fun initBLEConnection(): BLEConnection {
    return BLEConnection()
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun ConnectToBLEDevice(bleConnection: BLEConnection, navController: NavController, deviceAddress: String, showDialog: Boolean = true) {
    val isConnected = remember { mutableStateOf(false) }

    if(deviceAddress != "") {
        if(!isConnected.value && showDialog) {
            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false,
                    dismissOnClickOutside = false),
                title = null,
                confirmButton = {},
                text = {
                    Column (
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Please wait…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            )
        }

        bleConnection.ConnectToDevice(deviceAddress, 23)

        val storeDevice = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while(!isConnected.value) {
                isConnected.value = bleConnection.isBLEConnected()
                delay(300)
            }

            storeDevice.value = true
            navController.navigate(Pages.MainPage)
        }
    }
}