package bruce.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.HttpAuthHandler
import bruce.app.Main
import bruce.app.ui.theme.*
import java.io.File
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.annotation.RequiresPermission
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun MainScreen() {
        val navController = rememberNavController()
        var bleConnection by remember { mutableStateOf<BLEConnection?>(null) }

        NavHost(
            navController = navController,
            startDestination = Pages.MainPage,
            modifier = Modifier
                .fillMaxSize()
        ) {
            composable(route = Pages.MainPage) {
                val context = LocalContext.current
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val isTablet = configuration.screenWidthDp >= 600
                var terminalOutput by remember { mutableStateOf(listOf("Terminal ready...")) }
                var showDocDialog by remember { mutableStateOf(false) }
                var showWebView by remember { mutableStateOf(false) }
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


                var useUSBConnection by remember { mutableStateOf(true) }
                var bleClicked by remember { mutableStateOf(false) } // To trigger BLE device list refresh

                // Serial communication
                var serialCommunication by remember { mutableStateOf<SerialCommunication?>(null) }
                var dbHelper by remember { mutableStateOf<CustomCommandsDatabaseHelper?>(null) }


                if(bleClicked) {
                    bleConnection?.Setup(navController)
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
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = PurpleAccent,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Please wait...",
                                        color = White,
                                        fontSize = 16.sp
                                    )
                                }
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
                        IconButton(
                            onClick = {
                                useUSBConnection = !useUSBConnection
                                if(!useUSBConnection) {
                                    bleConnection = initBLEConnection()
                                    bleClicked = true
                                }
                            },
                            modifier = Modifier
                                .background(
                                    PurpleAccent,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = "Switch communication",
                                tint = White
                            )
                        }
                        IconButton(
                            onClick = { showBaudRateDialog = true },
                            modifier = Modifier
                                .background(
                                    PurpleAccent,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Configuration",
                                tint = White
                            )
                        }

                        IconButton(
                            onClick = { showDocDialog = true },
                            modifier = Modifier
                                .background(
                                    PurpleAccent,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Documentation",
                                tint = White
                            )
                        }
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

                    } else {

                    }

                    // WebView Credentials Dialog
                    if (showWebViewCredentials) {
                        Dialog(onDismissRequest = { showWebViewCredentials = false }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkGray)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Bruce WebView Authentication",
                                        color = White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Connect to Bruce WebUI WiFi before!",
                                        color = White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    var username by remember { mutableStateOf("admin") }
                                    var password by remember { mutableStateOf("bruce") }

                                    OutlinedTextField(
                                        value = username,
                                        onValueChange = { username = it },
                                        label = { Text("Username", color = White) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = White,
                                            unfocusedTextColor = White,
                                            focusedBorderColor = PurpleAccent,
                                            unfocusedBorderColor = LightGray,
                                            focusedLabelColor = PurpleAccent,
                                            unfocusedLabelColor = White
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text("Password", color = White) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = White,
                                            unfocusedTextColor = White,
                                            focusedBorderColor = PurpleAccent,
                                            unfocusedBorderColor = LightGray,
                                            focusedLabelColor = PurpleAccent,
                                            unfocusedLabelColor = White
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showWebViewCredentials = false },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = LightGray,
                                                contentColor = Black
                                            )
                                        ) { Text("Cancel") }

                                        Button(
                                            onClick = {
                                                showWebViewCredentials = false
                                                showWebView = true
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PurpleAccent,
                                                contentColor = White
                                            )
                                        ) { Text("Connect") }
                                    }
                                }
                            }
                        }
                    }

                    // WebView Dialog (fullscreen)
                    if (showWebView) {
                        Dialog(
                            onDismissRequest = { showWebView = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            var username by remember { mutableStateOf("admin") }
                            var password by remember { mutableStateOf("bruce") }

                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        webViewClient = object : WebViewClient() {
                                            override fun onReceivedHttpAuthRequest(
                                                view: WebView?,
                                                handler: HttpAuthHandler?,
                                                host: String?,
                                                realm: String?
                                            ) {
                                                if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                                                    handler?.proceed(username, password)
                                                } else {
                                                    handler?.cancel()
                                                }
                                            }
                                        }
                                        // Note: setHttpAuthUsernamePassword is deprecated, using WebViewClient instead
                                        loadUrl("http://bruce.local")
                                    }
                                }
                            )
                        }
                    }

                    // Baud Rate Configuration Dialog
                    if (showBaudRateDialog) {
                        Dialog(onDismissRequest = { showBaudRateDialog = false }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkGray)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Baud Rate Configuration",
                                        color = White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "Select baud rate for serial communication:",
                                        color = White,
                                        fontSize = 14.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Common baud rates
                                    val baudRates = listOf("9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600", "1000000")

                                    LazyColumn(
                                        modifier = Modifier.height(200.dp)
                                    ) {
                                        items(baudRates) { rate ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (rate == baudRate) PurpleAccent else LightGray
                                                )
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        baudRate = rate
                                                        serialCommunication?.setBaudRate(rate.toInt())
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = rate,
                                                        color = White,
                                                        fontWeight = if (rate == baudRate) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "Current: $baudRate bps",
                                        color = PurpleAccent,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = { showBaudRateDialog = false },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PurpleAccent,
                                            contentColor = White
                                        )
                                    ) {
                                        Text("Apply")
                                    }
                                }
                            }
                        }
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
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "No custom commands yet",
                                                    color = White.copy(alpha = 0.7f),
                                                    fontSize = 14.sp
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "Tap '+ Add' to create your first command",
                                                    color = White.copy(alpha = 0.5f),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
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
                                                            Text(
                                                                text = cmd.name,
                                                                color = PurpleAccent,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = cmd.command,
                                                                color = White,
                                                                fontSize = 12.sp,
                                                                fontFamily = FontFamily.Monospace
                                                            )
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

                                                            IconButton(
                                                                onClick = {
                                                                    dbHelper?.deleteCommand(cmd.id)
                                                                    customCommands = customCommands.filter { it.id != cmd.id }
                                                                },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Close,
                                                                    contentDescription = "Delete",
                                                                    tint = White,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
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
                        Dialog(onDismissRequest = { showAddCustomCmdDialog = false }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkGray)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Add Custom Command",
                                        color = White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    var commandName by remember { mutableStateOf("") }
                                    var commandText by remember { mutableStateOf("") }

                                    OutlinedTextField(
                                        value = commandName,
                                        onValueChange = { commandName = it },
                                        label = { Text("Command Name", color = White) },
                                        placeholder = { Text("e.g., LED On", color = White.copy(alpha = 0.5f)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = White,
                                            unfocusedTextColor = White,
                                            focusedBorderColor = PurpleAccent,
                                            unfocusedBorderColor = LightGray,
                                            focusedLabelColor = PurpleAccent,
                                            unfocusedLabelColor = White
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedTextField(
                                        value = commandText,
                                        onValueChange = { commandText = it },
                                        label = { Text("Serial Command", color = White) },
                                        placeholder = { Text("e.g., led r 255", color = White.copy(alpha = 0.5f)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = White,
                                            unfocusedTextColor = White,
                                            focusedBorderColor = PurpleAccent,
                                            unfocusedBorderColor = LightGray,
                                            focusedLabelColor = PurpleAccent,
                                            unfocusedLabelColor = White
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "Examples:",
                                        color = PurpleAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "• Name: 'LED Red' → Command: 'led r 255'",
                                        color = White.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "• Name: 'Say Hello' → Command: 'say Hello World'",
                                        color = White.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "• Name: 'IR Send' → Command: 'ir tx NEC 04000000'",
                                        color = White.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showAddCustomCmdDialog = false },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = LightGray,
                                                contentColor = Black
                                            )
                                        ) {
                                            Text("Cancel")
                                        }

                                        Button(
                                            onClick = {
                                                if (commandName.isNotEmpty() && commandText.isNotEmpty()) {
                                                    val newCommand = CustomSerialCommand(
                                                        id = System.currentTimeMillis().toString(),
                                                        name = commandName,
                                                        command = commandText
                                                    )
                                                    dbHelper?.insertCommand(newCommand)
                                                    customCommands = customCommands + newCommand
                                                    showAddCustomCmdDialog = false
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PurpleAccent,
                                                contentColor = White
                                            )
                                        ) {
                                            Text("Add Command")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Documentation Dialog
                    if (showDocDialog) {
                        Dialog(onDismissRequest = { showDocDialog = false }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkGray)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Documentation",
                                        color = White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://bruce.computer"))
                                                context.startActivity(intent)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PurpleGrey80,
                                                contentColor = White
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Open https://bruce.computer") }

                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pr3y/Bruce/wiki/Serial"))
                                                context.startActivity(intent)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PurpleGrey80,
                                                contentColor = White
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Open Serial Wiki") }

                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pr3y/BruceApp"))
                                                context.startActivity(intent)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PurpleGrey80,
                                                contentColor = White
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("BruceApp repo") }

                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { showDocDialog = false },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PurpleAccent,
                                            contentColor = White
                                        )
                                    ) {
                                        Text("Close")
                                    }
                                }
                            }
                        }
                    }

                    // Installation Complete Dialog
                    if (showInstallationCompleteDialog) {
                        Dialog(onDismissRequest = { showInstallationCompleteDialog = false }) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkGray)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Installation finished",
                                        color = White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Bruce Firmware Updated!",
                                        color = White,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { showInstallationCompleteDialog = false },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PurpleAccent,
                                            contentColor = White
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "OK",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            composable(route = Pages.BLEDevicesList) {
                bleConnection?.let { BLEDevicesView(navController, it) }
            }
        }


    }

    private fun uploadFirmware(context: android.content.Context, deviceId: String, baudRate: String, onStatusChange: (String) -> Unit, onLoadingChange: (Boolean) -> Unit, onInstallationComplete: () -> Unit) {
        onLoadingChange(true)
        onStatusChange("Starting firmware download for device: $deviceId...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Download firmware from GitHub
                withContext(Dispatchers.Main) {
                    onStatusChange("Downloading firmware from GitHub...")
                }
                val firmwareUrl = "https://github.com/pr3y/Bruce/releases/download/1.11/Bruce-$deviceId.bin"
                val firmwareData = downloadFirmware(firmwareUrl)
                
                withContext(Dispatchers.Main) {
                    onStatusChange("Firmware downloaded (${firmwareData.size} bytes)")
                    onStatusChange("Saving firmware to temporary file...")
                }
                
                // Save downloaded firmware to temp file
                val firmwarePath = saveAsFile(firmwareData)
                
                withContext(Dispatchers.Main) {
                    onStatusChange("Firmware saved to: $firmwarePath")
                    onStatusChange("Preparing esptool with arguments:")
                    onStatusChange("   Chip: ESP32-S3")
                    onStatusChange("   Baud Rate: $baudRate bps")
                    onStatusChange("   File: ${firmwarePath.substringAfterLast("/")}")
                    onStatusChange("Checking USB connection...")
                }
                
                // Flash the firmware
                val argument = "--chip esp32s3 --baud $baudRate --before default_reset --after hard_reset --no-stub write_flash -z 0x0 $firmwarePath"
                
                withContext(Dispatchers.Main) {
                    onStatusChange("Starting firmware upload process...")
                    onStatusChange("Executing: esptool $argument")
                }
                
                val result = Main().uploadFirmware(context, argument)
                
                withContext(Dispatchers.Main) {
                    onStatusChange("Raw result: $result")
                    
                    // Parse and display the captured output line by line
                    if (result.isNotEmpty() && result != "Success") {
                        val lines = result.split("\n").filter { it.isNotEmpty() }
                        onStatusChange("Found ${lines.size} lines of output")
                        lines.forEach { line ->
                            onStatusChange("ESPTool: $line")
                        }
                    }
                    
                    if (result.contains("Success", ignoreCase = true) || result.contains("completed successfully", ignoreCase = true)) {
                        onStatusChange("Firmware upload completed successfully!")
                        onStatusChange("Device should restart automatically...")
                        onInstallationComplete()
                    } else if (result.contains("Exception", ignoreCase = true)) {
                        onStatusChange("Upload failed with error: $result")
                    } else {
                        onStatusChange("Firmware Updated!")
                        onInstallationComplete()
                    }
                    onLoadingChange(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onStatusChange("Error during upload: ${e.message}")
                    onStatusChange("Check USB connection and try again")
                    onLoadingChange(false)
                }
            }
        }
    }
    
    private fun loadDeviceList(onResult: (List<DeviceInfo>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonUrl = "https://raw.githubusercontent.com/pr3y/Bruce/refs/heads/WebPage/src/lib/data/manifests.json"
                val jsonString = URL(jsonUrl).readText()
                val jsonObject = JSONObject(jsonString)
                val deviceList = mutableListOf<DeviceInfo>()
                
                // Parse each category
                jsonObject.keys().forEach { category ->
                    val categoryArray = jsonObject.getJSONArray(category)
                    for (i in 0 until categoryArray.length()) {
                        val device = categoryArray.getJSONObject(i)
                        deviceList.add(
                            DeviceInfo(
                                id = device.getString("id"),
                                name = device.getString("name"),
                                category = category
                            )
                        )
                    }
                }
                
                withContext(Dispatchers.Main) {
                    onResult(deviceList)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(emptyList())
                }
            }
        }
    }
    
    private suspend fun downloadFirmware(url: String): ByteArray {
        return withContext(Dispatchers.IO) {
            URL(url).openStream().use { inputStream ->
                inputStream.readBytes()
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

    private fun saveAsFile(content: ByteArray): String {
        val tempFile = File.createTempFile("firmware", ".bin")
        tempFile.writeBytes(content)
        println("Temp File Byte Count: ${tempFile.length()}")
        return tempFile.absolutePath
    }
}
