package bruce.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.io.File
import java.net.URI

data class CustomSerialCommand(val id: String, val name: String, val command: String)

object CustomCommandsStore {
    private val dir = File(System.getProperty("user.home"), ".bruce-app")
    private val file = File(dir, "custom_commands.json")

    fun load(): List<CustomSerialCommand> = try {
        if (!file.exists()) emptyList()
        else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let {
                    CustomSerialCommand(it.getString("id"), it.getString("name"), it.getString("command"))
                }
            }
        }
    } catch (_: Exception) { emptyList() }

    fun save(cmd: CustomSerialCommand) = saveAll(load() + cmd)

    fun delete(id: String) = saveAll(load().filter { it.id != id })

    private fun saveAll(commands: List<CustomSerialCommand>) {
        try {
            dir.mkdirs()
            val arr = JSONArray()
            commands.forEach { cmd ->
                arr.put(JSONObject().apply {
                    put("id", cmd.id)
                    put("name", cmd.name)
                    put("command", cmd.command)
                })
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }
}

/** Terminal lines are colour-coded by prefix so the log reads at a glance. */
private fun lineColor(line: String) = when {
    line.startsWith(">") -> Accent
    line.contains("Error", true) || line.contains("Fail", true) -> Hot
    line.contains("Connected") || line.contains("finished", true) -> Online
    else -> White.copy(alpha = 0.75f)
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val serialComm = remember { DesktopSerialCommunication() }
    val terminalOutput = remember { mutableStateListOf("Terminal ready...") }
    val terminalListState = rememberLazyListState()

    var availablePorts by remember { mutableStateOf(listOf<String>()) }
    var selectedPort by remember { mutableStateOf("") }
    var portDropdownExpanded by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var baudRate by remember { mutableStateOf("115200") }
    var serialCommand by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }

    var showBaudRateDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showSerialCmdDialog by remember { mutableStateOf(false) }
    var showAddCustomCmdDialog by remember { mutableStateOf(false) }
    var showDocDialog by remember { mutableStateOf(false) }
    var showInstallationCompleteDialog by remember { mutableStateOf(false) }

    var deviceList by remember { mutableStateOf(listOf<DeviceInfo>()) }
    var selectedDevice by remember { mutableStateOf("m5stack-cardputer") }
    var customCommands by remember { mutableStateOf(CustomCommandsStore.load()) }

    LaunchedEffect(Unit) {
        serialComm.setOutputListener { msg ->
            scope.launch(Dispatchers.Main) { terminalOutput.add(msg) }
        }
        availablePorts = withContext(Dispatchers.IO) { serialComm.listPorts() }
        if (availablePorts.isNotEmpty() && selectedPort.isEmpty()) {
            selectedPort = availablePorts.first()
        }
    }

    LaunchedEffect(terminalOutput.size) {
        if (terminalOutput.isNotEmpty()) {
            terminalListState.animateScrollToItem(terminalOutput.size - 1)
        }
    }

    DisposableEffect(Unit) {
        onDispose { serialComm.disconnect() }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, background = Ink, surface = PanelBg, onSurface = White
        ),
        typography = PixelTypography
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
                .padding(12.dp)
        ) {
            BrandHeader(connected = isConnected, port = selectedPort, baud = baudRate)

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Left rail: link, flashing, terminal ───────────────────────
                Column(
                    modifier = Modifier.width(400.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Panel(
                        title = "Serial link",
                        modifier = Modifier.fillMaxWidth(),
                        accent = if (isConnected) Online else Accent,
                        trailing = {
                            IconButton(onClick = { showBaudRateDialog = true }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Settings, "Baud rate", tint = Dim, modifier = Modifier.size(15.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = { showDocDialog = true }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Info, "Documentation", tint = Dim, modifier = Modifier.size(15.dp))
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                        .clip(BoxShape)
                                        .background(PanelHi)
                                        .border(1.dp, Hair, BoxShape)
                                        .clickable {
                                            scope.launch {
                                                availablePorts = withContext(Dispatchers.IO) { serialComm.listPorts() }
                                            }
                                            portDropdownExpanded = true
                                        }
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedPort.ifEmpty { "select port" },
                                        color = if (selectedPort.isEmpty()) Dim else White,
                                        fontSize = 17.sp
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text("▼", color = Accent, fontSize = 12.sp)
                                }
                                DropdownMenu(
                                    expanded = portDropdownExpanded,
                                    onDismissRequest = { portDropdownExpanded = false },
                                    modifier = Modifier.background(PanelBg)
                                ) {
                                    if (availablePorts.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("no ports found", color = Dim) },
                                            onClick = { portDropdownExpanded = false }
                                        )
                                    }
                                    availablePorts.forEach { port ->
                                        DropdownMenuItem(
                                            text = { Text(port, color = White) },
                                            onClick = {
                                                selectedPort = port
                                                portDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            BruceButton(
                                text = if (isConnected) "Detach" else "Attach",
                                primary = true,
                                color = if (isConnected) Online else Accent
                            ) {
                                if (isConnected) {
                                    serialComm.disconnect()
                                    isConnected = false
                                    terminalOutput.add("Disconnected from $selectedPort")
                                } else if (selectedPort.isEmpty()) {
                                    terminalOutput.add("Error: select a serial port first")
                                } else {
                                    val ok = serialComm.connect(selectedPort, baudRate.toIntOrNull() ?: 115200)
                                    isConnected = ok
                                    terminalOutput.add(
                                        if (ok) "Connected to $selectedPort at $baudRate bps"
                                        else "Failed to connect to $selectedPort"
                                    )
                                }
                            }
                        }
                    }

                    Panel(title = "Firmware", modifier = Modifier.fillMaxWidth()) {
                        BruceButton(
                            text = "Flash firmware",
                            modifier = Modifier.fillMaxWidth(),
                            primary = true,
                            height = 46.dp
                        ) {
                            showDeviceDialog = true
                            if (deviceList.isEmpty()) {
                                scope.launch { deviceList = FirmwareUploader.loadDeviceList() }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        BruceButton("Serial commands", modifier = Modifier.fillMaxWidth()) {
                            showSerialCmdDialog = true
                        }
                        if (isUploading) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = Hot, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("flashing — do not unplug", color = Hot, fontSize = 16.sp)
                            }
                        }
                    }

                    Panel(
                        title = "Terminal",
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        trailing = { Text("${terminalOutput.size} lines", color = Dim, fontSize = 13.sp) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(BoxShape)
                                .background(Color(0xFF06060A))
                                .border(1.dp, Hair, BoxShape)
                                .padding(8.dp)
                        ) {
                            SelectionContainer {
                                LazyColumn(state = terminalListState, modifier = Modifier.fillMaxSize()) {
                                    items(terminalOutput.toList()) { line ->
                                        Text(
                                            text = line,
                                            color = lineColor(line),
                                            fontSize = 16.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("❯", color = Accent, fontSize = 18.sp)
                            Spacer(Modifier.width(6.dp))
                            BasicPrompt(
                                value = serialCommand,
                                onValueChange = { serialCommand = it },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            BruceButton("Send") {
                                if (serialCommand.isNotEmpty()) {
                                    terminalOutput.add("> $serialCommand")
                                    serialComm.sendCommand(serialCommand)
                                    serialCommand = ""
                                }
                            }
                        }
                    }
                }

                // ── Right: native device screen mirror ────────────────────────
                ScreenMirrorPanel(
                    serialComm = serialComm,
                    isConnected = isConnected,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            // ── Status bar ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BRUCE APP v1.2.0", color = Dim, fontSize = 14.sp, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "$baudRate BPS · 8N1 · ${selectedDevice.uppercase()}",
                    color = Dim, fontSize = 14.sp, letterSpacing = 1.sp
                )
            }
        }
    }

    // --- Dialogs ---

    if (showBaudRateDialog) {
        BaudRateDialog(
            current = baudRate,
            onSelect = { rate ->
                baudRate = rate
                serialComm.setBaudRate(rate.toIntOrNull() ?: 115200)
            },
            onDismiss = { showBaudRateDialog = false }
        )
    }

    if (showDeviceDialog) {
        DeviceDialog(
            deviceList = deviceList,
            selectedDevice = selectedDevice,
            onSelect = { id ->
                selectedDevice = id
                terminalOutput.add("> Device selected: $id")
            },
            onInstall = {
                showDeviceDialog = false
                if (selectedPort.isEmpty()) {
                    terminalOutput.add("Error: No port selected. Connect to a serial port first.")
                } else {
                    // esptool needs the port to itself — drop our serial session first
                    if (isConnected) {
                        serialComm.disconnect()
                        isConnected = false
                        terminalOutput.add("Released $selectedPort for esptool")
                    }
                    isUploading = true
                    terminalOutput.add("> Starting firmware upload for $selectedDevice on $selectedPort...")
                    scope.launch {
                        FirmwareUploader.uploadFirmware(
                            deviceId = selectedDevice,
                            port = selectedPort,
                            baudRate = baudRate,
                            onOutput = { msg -> terminalOutput.add(msg) }
                        )
                        isUploading = false
                        showInstallationCompleteDialog = true
                    }
                }
            },
            onDismiss = { showDeviceDialog = false }
        )
    }

    if (showSerialCmdDialog) {
        SerialCmdDialog(
            commands = customCommands,
            baudRate = baudRate,
            onRun = { cmd ->
                terminalOutput.add("> ${cmd.command}")
                serialComm.sendCommand(cmd.command)
            },
            onDelete = { cmd ->
                CustomCommandsStore.delete(cmd.id)
                customCommands = customCommands.filter { it.id != cmd.id }
            },
            onAdd = { showAddCustomCmdDialog = true },
            onDismiss = { showSerialCmdDialog = false }
        )
    }

    if (showAddCustomCmdDialog) {
        AddCmdDialog(
            onAdd = { name, cmd ->
                val newCmd = CustomSerialCommand(System.currentTimeMillis().toString(), name, cmd)
                CustomCommandsStore.save(newCmd)
                customCommands = customCommands + newCmd
                showAddCustomCmdDialog = false
            },
            onDismiss = { showAddCustomCmdDialog = false }
        )
    }

    if (showDocDialog) {
        DocDialog(onDismiss = { showDocDialog = false })
    }

    if (showInstallationCompleteDialog) {
        InstallDoneDialog(onDismiss = { showInstallationCompleteDialog = false })
    }
}

// ─── Shared bits ───────────────────────────────────────────────────────────

/** Borderless pixel-font text field that reads as a shell prompt. */
@Composable
private fun BasicPrompt(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "type a serial command"
) {
    Box(
        modifier
            .height(34.dp)
            .clip(BoxShape)
            .background(PanelHi)
            .border(1.dp, Hair, BoxShape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(placeholder, color = Dim, fontSize = 17.sp)
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = White, fontFamily = Pixel, fontSize = 17.sp
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PixelDialog(width: Int, title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Panel(title = title, modifier = Modifier.width(width.dp), content = content)
    }
}

@Composable
private fun PixelField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String) {
    Column {
        Text(label.uppercase(), color = Accent, fontSize = 14.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(4.dp))
        BasicPrompt(value, onValueChange, Modifier.fillMaxWidth(), placeholder)
    }
}

/** Selectable row used by the baud / device / command lists. */
@Composable
private fun ListRow(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(BoxShape)
            .background(if (selected) Accent.copy(alpha = 0.18f) else PanelHi)
            .border(1.dp, if (selected) Accent else Hair, BoxShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ─── Dialog composables ────────────────────────────────────────────────────

@Composable
private fun BaudRateDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val rates = listOf("9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600", "1000000")
    PixelDialog(340, "Baud rate", onDismiss) {
        LazyColumn(modifier = Modifier.height(230.dp)) {
            items(rates) { rate ->
                ListRow(selected = rate == current, onClick = { onSelect(rate) }) {
                    Text(if (rate == current) "▸ $rate" else "  $rate", color = White, fontSize = 17.sp)
                    Spacer(Modifier.weight(1f))
                    Text("bps", color = Dim, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        BruceButton("Apply · $current bps", Modifier.fillMaxWidth(), primary = true, onClick = onDismiss)
    }
}

@Composable
private fun DeviceDialog(
    deviceList: List<DeviceInfo>,
    selectedDevice: String,
    onSelect: (String) -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    PixelDialog(430, "Select device", onDismiss) {
        if (deviceList.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("loading device list...", color = Dim, fontSize = 17.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(deviceList) { device ->
                    ListRow(selected = device.id == selectedDevice, onClick = { onSelect(device.id) }) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name, color = White, fontSize = 17.sp)
                            Text(device.category.uppercase(), color = Dim, fontSize = 13.sp, letterSpacing = 1.sp)
                        }
                        if (device.id == selectedDevice) Text("◉", color = Accent, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            BruceButton("Install", Modifier.fillMaxWidth(), primary = true, color = Hot, height = 44.dp, onClick = onInstall)
        }
        Spacer(Modifier.height(8.dp))
        BruceButton("Cancel", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

@Composable
private fun SerialCmdDialog(
    commands: List<CustomSerialCommand>,
    baudRate: String,
    onRun: (CustomSerialCommand) -> Unit,
    onDelete: (CustomSerialCommand) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    PixelDialog(470, "Custom serial commands", onDismiss) {
        if (commands.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("no commands stored", color = Dim, fontSize = 17.sp)
                    Text("add one to fire it over serial", color = Dim.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                items(commands) { cmd ->
                    ListRow(selected = false, onClick = { onRun(cmd) }) {
                        Column(Modifier.weight(1f)) {
                            Text(cmd.name, color = Accent, fontSize = 17.sp)
                            Text(cmd.command, color = White.copy(alpha = 0.75f), fontSize = 15.sp)
                        }
                        BruceButton("Run", height = 28.dp) { onRun(cmd) }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { onDelete(cmd) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, "Delete", tint = Dim, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("$baudRate BPS · 8N1 · NO FLOW CONTROL", color = Dim, fontSize = 13.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        BruceButton("+ Add command", Modifier.fillMaxWidth(), primary = true, onClick = onAdd)
        Spacer(Modifier.height(8.dp))
        BruceButton("Close", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

@Composable
private fun AddCmdDialog(onAdd: (name: String, command: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    PixelDialog(400, "Add command", onDismiss) {
        PixelField(name, { name = it }, "Name", "e.g. LED red")
        Spacer(Modifier.height(10.dp))
        PixelField(command, { command = it }, "Command", "e.g. led r 255")
        Spacer(Modifier.height(12.dp))
        Text("EXAMPLES", color = Accent, fontSize = 14.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(2.dp))
        listOf("led r 255", "say Hello World", "ir tx NEC 04000000").forEach {
            Text("  $it", color = Dim, fontSize = 15.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BruceButton("Cancel", Modifier.weight(1f), onClick = onDismiss)
            BruceButton("Save", Modifier.weight(1f), primary = true) {
                if (name.isNotEmpty() && command.isNotEmpty()) onAdd(name, command)
            }
        }
    }
}

@Composable
private fun DocDialog(onDismiss: () -> Unit) {
    PixelDialog(370, "Documentation", onDismiss) {
        listOf(
            "https://bruce.computer" to "bruce.computer",
            "https://wiki.bruce.computer/controlling-device/serial/" to "Serial wiki",
            "https://github.com/BruceDevices/App" to "App repo"
        ).forEach { (url, label) ->
            BruceButton("↗ $label", Modifier.fillMaxWidth()) {
                try { Desktop.getDesktop().browse(URI(url)) } catch (_: Exception) {}
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(4.dp))
        BruceButton("Close", Modifier.fillMaxWidth(), primary = true, onClick = onDismiss)
    }
}

@Composable
private fun InstallDoneDialog(onDismiss: () -> Unit) {
    PixelDialog(330, "Flash complete", onDismiss) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("BRUCE FIRMWARE UPDATED", color = Online, fontSize = 20.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text("reboot the device to start", color = Dim, fontSize = 15.sp)
        }
        Spacer(Modifier.height(12.dp))
        BruceButton("OK", Modifier.fillMaxWidth(), primary = true, height = 42.dp, onClick = onDismiss)
    }
}
