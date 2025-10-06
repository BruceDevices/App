package bruce.app

import DocDialog
import Pages
import ScreenRender
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.webkit.HttpAuthHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import bruce.app.ui.components.BaudRateDialog
import bruce.app.ui.components.BluetoothConnectDialog
import bruce.app.ui.components.BruceIconButton
import bruce.app.ui.components.CommandChip
import bruce.app.ui.components.CustomDialogComponent
import bruce.app.ui.components.DeleteCommandComponent
import bruce.app.ui.components.EmptyCommand
import bruce.app.ui.components.InstallCompleteDialog
import bruce.app.ui.components.UploadingSpinner
import bruce.app.ui.components.showWebViewDialogCredentials
import bruce.app.ui.theme.Black
import bruce.app.ui.theme.DarkGray
import bruce.app.ui.theme.FirmwareFlasherTheme
import bruce.app.ui.theme.LightGray
import bruce.app.ui.theme.PurpleAccent
import bruce.app.ui.theme.PurpleGrey80
import bruce.app.ui.theme.White
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

data class DeviceInfo(
    val id: String,
    val name: String,
    val category: String
)

data class SerialCommand(
    val command: String,
    val description: String,
    val example: String
)

data class CustomSerialCommand(
    val id: String,
    val name: String,
    val command: String
)

class CustomCommandsDatabaseHelper(context: android.content.Context) : SQLiteOpenHelper(context, "custom_commands.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE custom_commands (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                command TEXT NOT NULL
            )
        """)
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS custom_commands")
        onCreate(db)
    }
    
    fun insertCommand(command: CustomSerialCommand) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", command.id)
            put("name", command.name)
            put("command", command.command)
        }
        db.insert("custom_commands", null, values)
    }
    
    fun getAllCommands(): List<CustomSerialCommand> {
        val db = readableDatabase
        val cursor = db.query("custom_commands", null, null, null, null, null, null)
        val commands = mutableListOf<CustomSerialCommand>()
        
        cursor.use {
            while (it.moveToNext()) {
                commands.add(
                    CustomSerialCommand(
                        id = it.getString(0),
                        name = it.getString(1),
                        command = it.getString(2)
                    )
                )
            }
        }
        return commands
    }
    
    fun deleteCommand(id: String) {
        val db = writableDatabase
        db.delete("custom_commands", "id = ?", arrayOf(id))
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FirmwareFlasherTheme {
                MainScreen()
            }
        }
    }

    @Composable
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun MainScreen() {
        val navController = rememberNavController()

        // Put here states that must not be reset when navigating between pages
        var bleConnection by remember { mutableStateOf<BLEConnection?>(null) }
        var bleClicked by remember { mutableStateOf(false) } // To trigger BLE device list refresh
        var useUSBConnection by remember { mutableStateOf(true) }
        var bleDeviceConnected by remember { mutableStateOf(false) }
        val deviceReady = remember { mutableStateOf(false) }

        NavHost(
            navController = navController,
            startDestination = Pages.MainPage,
            modifier = Modifier
                .fillMaxSize()
        ) {
            composable(route = Pages.MainPage) @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT) {
                val context = LocalContext.current
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val isTablet = configuration.screenWidthDp >= 600
                var terminalOutput by remember { mutableStateOf(listOf("Terminal ready...")) }
                var showDocDialog by remember { mutableStateOf(false) }
                var showWebViewCredentials by remember { mutableStateOf(false) }
                var showDeviceDialog by remember { mutableStateOf(false) }
                var serialCommand by remember { mutableStateOf("") }
                var selectedDevice by remember { mutableStateOf("m5stack-cardputer") }
                var deviceList by remember { mutableStateOf(listOf<DeviceInfo>()) }
                var isTerminalMaximized by remember { mutableStateOf(false) }
                var baudRate by remember { mutableStateOf("115200") }
                var showBaudRateDialog by remember { mutableStateOf(false) }
                var showSerialCmdDialog by remember { mutableStateOf(false) }
                var isUploading by remember { mutableStateOf(false) }
                var showInstallationCompleteDialog by remember { mutableStateOf(false) }
                var showAddCustomCmdDialog by remember { mutableStateOf(false) }
                var customCommands by remember { mutableStateOf(listOf<CustomSerialCommand>()) }
                val terminalListState = rememberLazyListState()

                // Serial communication
                var serialCommunication by remember { mutableStateOf<SerialCommunication?>(null) }
                var dbHelper by remember { mutableStateOf<CustomCommandsDatabaseHelper?>(null) }
                val store = KvStore()
                val tmp = store.read("ble_device")
                var res by remember { mutableStateOf(tmp) }
                val scanResult = remember { mutableStateOf(listOf(BLEDevice("", ""))) }

                if(bleClicked && !bleDeviceConnected) {
                    if(res != null && bleConnection != null) {
                        println("Setup BLE...")
                        bleConnection?.Setup()
                        LaunchedEffect(Unit) {
                            println("Start scanning...")
                            while(scanResult.value.none { it.address == res } || deviceReady.value) {
                                println("Scanning...")
                                scanResult.value = bleConnection!!.getScanResult()
                                delay(300)
                            }
                            println("Found saved device!")
                            deviceReady.value = true
                        }

                        if(deviceReady.value) {
                            println("Connecting to saved device...")
                            ConnectToBLEDevice(bleConnection!!, navController, res!!, false)
                            LaunchedEffect(Unit) {
                                while(!bleConnection!!.isBLEConnected()) {
                                    delay(300)
                                }
                                bleDeviceConnected = true
                                bleConnection?.stopScan()
                                bleClicked = false
                            }
                        }
                        BluetoothConnectDialog({ res = null })
                    } else {
                        bleConnection?.Setup(navController)
                    }
                }

                if(useUSBConnection) {
                    // Initialize serial communication and database
                    LaunchedEffect(Unit) {
                        serialCommunication = AndroidSerialCommunication(context)
                        serialCommunication?.setOutputListener { message ->
                            terminalOutput = terminalOutput + message
                        }
                        // Automatically try to connect when app starts
                        serialCommunication?.connect()

                        // Initialize database and load custom commands
                        dbHelper = CustomCommandsDatabaseHelper(context)
                        customCommands = dbHelper?.getAllCommands() ?: emptyList()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Main content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Black)
                            .padding(top = 100.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        if(useUSBConnection) {
                            // Upload Firmware button
                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    showDeviceDialog = true
                                    if (deviceList.isEmpty()) {
                                        loadDeviceList { devices ->
                                            deviceList = devices
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PurpleAccent,
                                    contentColor = White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                            ) {
                                Text(
                                    text = "Upload Firmware",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showWebViewCredentials = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PurpleGrey80,
                                        contentColor = White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Bruce WebView")
                                }

                                Button(
                                    onClick = { showSerialCmdDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PurpleGrey80,
                                        contentColor = White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Serial CMD")
                                }
                            }

                            // Loading indicator below action buttons
                            if (isUploading) {
                                UploadingSpinner()
                            }

                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }


                    // Top right corner buttons
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 60.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BruceIconButton(
                            onClick = {
                                useUSBConnection = !useUSBConnection
                                if(!useUSBConnection) {
                                    if(bleConnection == null)
                                        bleConnection = initBLEConnection()
                                    bleClicked = true
                                } else {
                                    bleConnection?.disconnect()
                                    bleClicked = false
                                    bleDeviceConnected = false
                                    deviceReady.value = false
                                }
                            },
                            icon = if(useUSBConnection) Icons.Filled.Bluetooth else Icons.Filled.Usb,
                            contentDescription = "Switch communication"
                        )
                        BruceIconButton(
                            onClick = { showBaudRateDialog = true },
                            icon = Icons.Default.Settings,
                            contentDescription = "Configuration"
                        )
                        BruceIconButton(
                            onClick = { showDocDialog = true },
                            icon = Icons.Default.Info,
                            contentDescription = "Documentation"
                        )
                    }

                    if(bleDeviceConnected) {
                        ScreenRender(bleConnection!!)
                    }

                    // Device Selection Dialog
                    if (showDeviceDialog) {
                        LaunchedEffect(showDeviceDialog) {
                            if (deviceList.isEmpty()) {
                                loadDeviceList { devices ->
                                    deviceList = devices
                                }
                            }
                        }

                        Dialog(onDismissRequest = { showDeviceDialog = false }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = (configuration.screenHeightDp.dp * 0.8f))
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkGray)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Select Device",
                                        color = White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    if (deviceList.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Loading devices...", color = White)
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.heightIn(max = (configuration.screenHeightDp.dp * 0.4f))
                                        ) {
                                            items(deviceList) { device ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (device.id == selectedDevice) PurpleAccent else LightGray
                                                    )
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            selectedDevice = device.id
                                                            terminalOutput = terminalOutput + "> Device selected: ${device.name} (${device.id})"
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                text = device.name,
                                                                color = White,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = device.category,
                                                                color = White.copy(alpha = 0.7f),
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Static Install button - always visible
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Divider line
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(LightGray)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "Selected: $selectedDevice",
                                            color = White,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        Button(
                                            onClick = {
                                                showDeviceDialog = false
                                                terminalOutput = terminalOutput + "> Selected device: $selectedDevice"
                                                uploadFirmware(
                                                    context = context,
                                                    deviceId = selectedDevice,
                                                    baudRate = baudRate,
                                                    onStatusChange = { status ->
                                                        terminalOutput = terminalOutput + "> $status"
                                                    },
                                                    onLoadingChange = { loading ->
                                                        isUploading = loading
                                                    },
                                                    onInstallationComplete = {
                                                        showInstallationCompleteDialog = true
                                                    }
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(60.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PurpleAccent,
                                                contentColor = White
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "INSTALL",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Cancel button
                                    Button(
                                        onClick = { showDeviceDialog = false },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PurpleGrey80,
                                            contentColor = White
                                        )
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }

                    if(useUSBConnection) {
                        // Terminal section at bottom
                        if (isTerminalMaximized) {
                            // Maximized terminal - fullscreen and keeps input visible
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Black.copy(alpha = 0.9f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                // Maximized terminal header
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            DarkGray,
                                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                                        )
                                        .border(
                                            2.dp,
                                            PurpleAccent,
                                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Terminal Output",
                                            color = White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Button(
                                            onClick = { isTerminalMaximized = false },
                                            modifier = Modifier.height(36.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PurpleAccent,
                                                contentColor = White
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "−",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Terminal display
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(
                                            DarkGray,
                                            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                        )
                                        .border(
                                            2.dp,
                                            PurpleAccent,
                                            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    LazyColumn(
                                        state = terminalListState,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(terminalOutput) { line ->
                                            Text(
                                                text = line,
                                                color = White,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            )
                                        }
                                    }

                                    // Auto-scroll to bottom when new items are added
                                    LaunchedEffect(terminalOutput.size) {
                                        if (terminalOutput.isNotEmpty()) {
                                            terminalListState.animateScrollToItem(terminalOutput.size - 1)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Serial command input in maximized view
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = serialCommand,
                                        onValueChange = { serialCommand = it },
                                        label = { Text("Serial Command", color = White) },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = White,
                                            unfocusedTextColor = White,
                                            focusedBorderColor = PurpleAccent,
                                            unfocusedBorderColor = LightGray,
                                            focusedLabelColor = PurpleAccent,
                                            unfocusedLabelColor = White
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            if (serialCommand.isNotEmpty()) {
                                                terminalOutput = terminalOutput + "> $serialCommand"
                                                serialCommunication?.sendCommand(serialCommand)
                                                serialCommand = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PurpleAccent,
                                            contentColor = White
                                        )
                                    ) {
                                        Text("Send")
                                    }
                                }
                            }
                        } else {
                            // Normal terminal at bottom
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                // Terminal controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Terminal Output:",
                                        color = White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Button(
                                        onClick = { isTerminalMaximized = true },
                                        modifier = Modifier.height(32.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PurpleAccent,
                                            contentColor = White
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "+",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Terminal display
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(
                                            if (isLandscape || isTablet) 200.dp else 120.dp
                                        )
                                        .background(
                                            DarkGray,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            PurpleAccent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp)
                                ) {
                                    LazyColumn(
                                        state = terminalListState
                                    ) {
                                        items(terminalOutput) { line ->
                                            Text(
                                                text = line,
                                                color = White,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    // Auto-scroll to bottom when new items are added
                                    LaunchedEffect(terminalOutput.size) {
                                        if (terminalOutput.isNotEmpty()) {
                                            terminalListState.animateScrollToItem(terminalOutput.size - 1)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Serial command input
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = serialCommand,
                                        onValueChange = { serialCommand = it },
                                        label = { Text("Serial Command", color = White) },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = White,
                                            unfocusedTextColor = White,
                                            focusedBorderColor = PurpleAccent,
                                            unfocusedBorderColor = LightGray,
                                            focusedLabelColor = PurpleAccent,
                                            unfocusedLabelColor = White
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            if (serialCommand.isNotEmpty()) {
                                                terminalOutput = terminalOutput + "> $serialCommand"
                                                // Send command via serial communication
                                                serialCommunication?.sendCommand(serialCommand)
                                                serialCommand = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PurpleAccent,
                                            contentColor = White
                                        )
                                    ) {
                                        Text("Send")
                                    }
                                }
                            }
                        }

                    }

                    // WebView Credentials Dialog
                    if(showWebViewCredentials) {
                        showWebViewDialogCredentials(onShowComponentChange = { showWebViewCredentials = it })
                    }

                    // Baud Rate Configuration Dialog
                    if (showBaudRateDialog) {
                        BaudRateDialog(serialCommunication,  onBaudRateChange = { newRate ->
                            baudRate = newRate
                        }, onDismiss = { showBaudRateDialog = it }, baudRate)
                    }

                    // Serial Commands Dialog
                    if (showSerialCmdDialog) {
                        Dialog(onDismissRequest = { showSerialCmdDialog = false }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = (configuration.screenHeightDp.dp * 0.85f))
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkGray)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Custom Serial Commands",
                                            color = White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Tap any command to execute it via serial",
                                        color = PurpleAccent,
                                        fontSize = 12.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    if (customCommands.isEmpty()) {
                                        EmptyCommand()
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            items(customCommands) { cmd ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    colors = CardDefaults.cardColors(containerColor = LightGray)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(12.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            CommandChip(cmd)
                                                        }

                                                        Row {
                                                            Button(
                                                                onClick = {
                                                                    terminalOutput = terminalOutput + "> ${cmd.command}"
                                                                    serialCommunication?.sendCommand(cmd.command)
                                                                    // Keep dialog open for easy reuse
                                                                },
                                                                modifier = Modifier.height(36.dp),
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = PurpleAccent,
                                                                    contentColor = White
                                                                ),
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Text("Run", fontSize = 13.sp)
                                                            }

                                                            Spacer(modifier = Modifier.width(2.dp))

                                                            DeleteCommandComponent(dbHelper, cmd, onDelete = { deletedCmd ->
                                                                customCommands =
                                                                    customCommands.filter { it.id != deletedCmd.id }
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "Connection: $baudRate bps, 8N1, No Flow Control",
                                        color = White.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Add Command button placed above Close
                                    Button(
                                        onClick = { showAddCustomCmdDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PurpleAccent,
                                            contentColor = White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Add Command",
                                            tint = White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add Command", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = { showSerialCmdDialog = false },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PurpleGrey80,
                                            contentColor = White
                                        )
                                    ) {
                                        Text("Close")
                                    }
                                }
                            }
                        }
                    }

                    // Add Custom Command Dialog
                    if (showAddCustomCmdDialog) {
                        CustomDialogComponent(dbHelper, onDismiss = { showAddCustomCmdDialog = it }, onNewCustomCommand = { newCmd ->
                            customCommands = customCommands + newCmd
                        })
                    }

                    // Documentation Dialog
                    if (showDocDialog) {
                        DocDialog(onShowComponentChange = { showDocDialog = it })
                    }

                    // Installation Complete Dialog
                    if (showInstallationCompleteDialog) {
                        InstallCompleteDialog(onShowComponentChange = { showInstallationCompleteDialog = it })
                    }
                }
            }
            composable(route = Pages.BLEDevicesList) {
                bleClicked = false
                bleConnection?.let { BLEDevicesView(navController, it) }
                LaunchedEffect(Unit) {
                    while(!bleConnection!!.isBLEConnected()) {
                        delay(300)
                    }
                    bleDeviceConnected = true
                    bleConnection?.stopScan()
                    bleClicked = false
                }
            }
        }


    }

    @Preview(showBackground = true)
    @Composable
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun MainScreenPreview() {
        FirmwareFlasherTheme {
            MainScreen()
        }
    }
}
