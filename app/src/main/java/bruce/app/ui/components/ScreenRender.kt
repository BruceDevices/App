import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import bruce.app.BLEConnection
import bruce.app.BLEDataReadCallback
import kotlin.concurrent.thread

@Composable
fun NavigationWidget(bleConnection: BLEConnection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = {  // Esc button
            bleConnection.writeToDevice(
                "4371ec0b-3d43-49f9-b731-7c72a4a7bb91",
                "d555ed97-bf2a-4f46-b3eb-d1fcdd7325e9",
                "navigation up".toByteArray()
            )
        }) {
            Text(text = "▲")
        }

        Button(onClick = {  // Esc button
            bleConnection.writeToDevice(
                "4371ec0b-3d43-49f9-b731-7c72a4a7bb91",
                "d555ed97-bf2a-4f46-b3eb-d1fcdd7325e9",
                "navigation esc".toByteArray()
            )
        }) {
            Text(text = "ESC")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            bleConnection.writeToDevice(
                "4371ec0b-3d43-49f9-b731-7c72a4a7bb91",
                "d555ed97-bf2a-4f46-b3eb-d1fcdd7325e9",
                "navigation prev".toByteArray()
            )
        }) {
            Text(text = "◀")
        }
        Button(onClick = {
            bleConnection.writeToDevice(
                "4371ec0b-3d43-49f9-b731-7c72a4a7bb91",
                "d555ed97-bf2a-4f46-b3eb-d1fcdd7325e9",
                "navigation select".toByteArray()
            )
        }) {
            Text(text = "●")
        }
        Button(onClick = {
            bleConnection.writeToDevice(
                "4371ec0b-3d43-49f9-b731-7c72a4a7bb91",
                "d555ed97-bf2a-4f46-b3eb-d1fcdd7325e9",
                "navigation next".toByteArray()
            )
        }) {
            Text(text = "▶")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            bleConnection.writeToDevice(
                "4371ec0b-3d43-49f9-b731-7c72a4a7bb91",
                "d555ed97-bf2a-4f46-b3eb-d1fcdd7325e9",
                "navigation down".toByteArray()
            )
        }) {
            Text(text = "▼")
        }
    }
}

@Composable
fun PowerWidget(bleConnection: BLEConnection) {
    Row {
        Button({
            bleConnection.writeToDevice(
                "4371ec0b-3d43-49f9-b731-7c72a4a7bb91",
                "d555ed97-bf2a-4f46-b3eb-d1fcdd7325e9",
                "poweroff".toByteArray()
            )
        }) {
            Text("Shutdown")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button({
            bleConnection.writeToDevice(
                "4371ec0b-3d43-49f9-b731-7c72a4a7bb91",
                "d555ed97-bf2a-4f46-b3eb-d1fcdd7325e9",
                "reboot".toByteArray()
            )
        }) {
            Text("Reboot")
        }
    }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun ScreenRender(bleConnection: BLEConnection) {
    var payload = remember { mutableStateListOf<Int>() }
    val screenInfo = remember { mutableStateOf(ScreenInfo(0, 0, 0)) }
    bleConnection.enableNotification(
        "4371ec0b-3d43-49f9-b731-7c72a4a7bb91",
        "d555ed97-bf2a-4f46-b3eb-d1fcdd7325e9"
    )
    var cleanTime = false
    var lastTimeBLECommunication: Long = 0
    val invalidView = remember { mutableStateOf(false) }

    thread {
        while (true) {   // Cleanup payload list for the next read if no data received for 5 second. This avoid mixing data from different scenes but ensure smooth rendering
            if ((System.currentTimeMillis() - lastTimeBLECommunication > 5000)) {
                cleanTime = true
            }
        }
    }

    object : BLEDataReadCallback {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDataRead(service: String, char: String, data: ByteArray) {
            if (data[0] == 0x23.toByte() && data[1] == 0x20.toByte()) {   // Ignore 0x2320 messages
                println("Ignore")
                return
            }
            if (screenInfo.value.invalidScreen()) {
                val res = data.toString(Charsets.UTF_8).split("x").map { it.trim() }
                if (res.size != 3) {
                    invalidView.value = true    // Invalid message receiver
                } else {
                    println("Parsing screen info: ${res[1]}x${res[0]}x${res[2]}")
                    parseScreenInfo(res[1].toInt(), res[0].toInt(), res[2].toInt())
                    invalidView.value = false
                    screenInfo.value = ScreenInfo(res[1].toInt(), res[0].toInt(), res[2].toInt())
                    bleConnection.writeToDevice(
                        "4371ec0b-3d43-49f9-b731-7c72a4a7bb91",
                        "d555ed97-bf2a-4f46-b3eb-d1fcdd7325e9",
                        "display start".toByteArray()
                    )
                }
                return
            }

            if (cleanTime) {
                cleanTime = false
                payload.clear()
            }
            lastTimeBLECommunication = System.currentTimeMillis()

            payload.addAll(data.map { it.toInt() and 0xFF })
        }
    }.let {
        bleConnection.setDataReadCallback(it)
    }

    LaunchedEffect(Unit) {
        bleConnection.writeToDevice(
            "4371ec0b-3d43-49f9-b731-7c72a4a7bb91",
            "d555ed97-bf2a-4f46-b3eb-d1fcdd7325e9", "display info".toByteArray()
        )
    }

    val textMeasurer = rememberTextMeasurer()
    setTextMeasurer(textMeasurer)
    setDensity(LocalDensity.current)

    Box(
        modifier = Modifier
            .fillMaxSize()      // fill the available space
            .wrapContentSize()  // make the box only as big as its children
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                if (invalidView.value) {
                    Text("Invalid header received, can't render the screen")
                }
                val density = LocalDensity.current
                val heightDp = with(density) { (screenInfo.value.height * 3).toDp() }
                val widthDp = with(density) { (screenInfo.value.width * 3).toDp() }
                Canvas(
                    modifier = Modifier
                        .width(widthDp)
                        .height(heightDp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    var i = 0
                    while (i < payload.size) {
                        if (payload[i] != 0xAA) {
                            println("Invalid header at $i")
                            invalidView.value = true
                            return@Canvas
                        } else if (invalidView.value && payload[i] == 0xAA) {
                            invalidView.value = false
                        }

                        i++

                        val size = payload[i++] - 4
                        val func = payload[i++]
                        val funcArg: MutableList<Int> = mutableListOf()
                        for (j in i..(i + size)) {
                            funcArg.add(payload[j])
                        }

                        i += size + 1

                        if (funcArg.isEmpty()) {
                            continue
                        }
                        parseFunction(func, funcArg)
                    }
                }
                NavigationWidget(bleConnection)
                PowerWidget(bleConnection)
        }
    }
}