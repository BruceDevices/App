package bruce.app

import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
@RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
fun BLEDevicesView(navController: NavController, bleConnection: BLEConnection) {
    val scanResult = remember { mutableStateOf(bleConnection.getScanResult()) }
    val runTask = remember { mutableStateOf(true) }
    val deviceAddress = remember { mutableStateOf("") }
    val store = KvStore()

    MaterialTheme(
      //  colorScheme = Style.scheme
    ) {
        Column {
            Text("${scanResult.value.size} Devices found", modifier = Modifier.align(Alignment.CenterHorizontally))
            scanResult.value.map {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Column {
                        Text("Device: ${it.name ?: "Unknown"}")
                        Text("Address: ${it.address}")

                    }
                    Button(onClick = {
                        runTask.value = false
                        bleConnection.stopScan()
                        deviceAddress.value = it.address
                    }) {
                        Text("Connect")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

            }
        }

    }

    LaunchedEffect(Unit) {
        while(runTask.value) {
            scanResult.value = bleConnection.getScanResult()
            delay(300)
        }
    }


    ConnectToBLEDevice(bleConnection, navController, deviceAddress.value)
    store.write("ble_device", deviceAddress.value)
}