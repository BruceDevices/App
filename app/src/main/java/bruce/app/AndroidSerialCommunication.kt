package bruce.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.nio.ByteBuffer

class AndroidSerialCommunication(private val context: Context) : SerialCommunication {
    companion object {
        private const val TAG = "SerialComm"
        private const val DEFAULT_BAUDRATE = 115200
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var receiverRegistered = false
    
    private val ACTION_USB_PERMISSION = "bruce.app.USB_PERMISSION"
    private val TIMEOUT = 1000
    private var currentBaudRate = DEFAULT_BAUDRATE
    
    private var outputListener: ((String) -> Unit)? = null
    private var screenPacketListener: ((ByteArray) -> Unit)? = null
    private var readThread: Thread? = null
    @Volatile private var reading = false

    init {
        Log.d(TAG, "Initializing USB communication with baudrate: $currentBaudRate")
    }

    override fun setBaudRate(baudRate: Int) {
        currentBaudRate = baudRate
        Log.d(TAG, "Setting new baudrate: $baudRate")
        if (usbConnection != null && usbInterface != null) {
            initializeSerialConnection() // Reinitialize with new baudrate
        }
    }

    private fun setupUsbPermission() {
        if (receiverRegistered) return
        
        try {
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            
            ContextCompat.registerReceiver(
                context,
                usbPermissionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
            Log.d(TAG, "USB permission receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering USB receiver", e)
            notifyOutput("Error setting up USB permissions: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received USB intent: ${intent.action}")
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.d(TAG, "USB Permission granted for device: ${device?.deviceName}")
                            device?.let {
                                setupDevice(it)
                            }
                        } else {
                            Log.e(TAG, "USB Permission denied for device: ${device?.deviceName}")
                            notifyOutput("USB Permission denied. Please grant permission when prompted.")
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d(TAG, "USB Device attached")
                    connect()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d(TAG, "USB Device detached")
                    disconnect()
                }
            }
        }
    }

    override fun setOutputListener(listener: (String) -> Unit) {
        outputListener = listener
    }

    private fun notifyOutput(message: String) {
        outputListener?.invoke(message)
    }

    override fun connect() {
        notifyOutput("\n=== Starting USB Serial Connection ===")
        
        try {
            setupUsbPermission()
            
            val device = findConnectedDevice()
            if (device != null) {
                if (usbManager.hasPermission(device)) {
                    notifyOutput("Already have permission for device")
                    setupDevice(device)
                } else {
                    notifyOutput("Requesting permission for device...")
                    val permissionIntent = PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(ACTION_USB_PERMISSION),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                    )
                    usbManager.requestPermission(device, permissionIntent)
                }
            } else {
                notifyOutput("No compatible USB device found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in connect()", e)
            notifyOutput("Error connecting: ${e.message}")
        }
    }

    private fun findConnectedDevice(): UsbDevice? {
        val deviceList = usbManager.deviceList
        notifyOutput("Found ${deviceList.size} USB devices")
        
        deviceList.values.forEach { device ->
            notifyOutput("Device found: VID=0x${device.vendorId.toString(16)}, PID=0x${device.productId.toString(16)}")
            notifyOutput("  - Manufacturer: ${device.manufacturerName ?: "Unknown"}")
            notifyOutput("  - Product: ${device.productName ?: "Unknown"}")
            notifyOutput("  - Device Class: ${device.deviceClass}")
            notifyOutput("  - Device Subclass: ${device.deviceSubclass}")
            
            if (isESP32Device(device)) {
                notifyOutput("Found compatible device!")
                return device
            }
        }
        
        if (deviceList.isEmpty()) {
            notifyOutput("No USB devices found. Make sure:")
            notifyOutput("1. USB debugging is enabled")
            notifyOutput("2. USB is connected in data/file transfer mode")
            notifyOutput("3. Device has USB OTG support")
            notifyOutput("4. You have granted USB permission to the app")
        }
        
        return null
    }

    private fun isESP32Device(device: UsbDevice): Boolean {
        // Common USB-Serial converter vendor IDs
        val supportedVendorIds = setOf(
            0x1a86,  // QinHeng Electronics (CH340)
            0x10c4,  // Silicon Labs (CP210x)
            0x0403,  // FTDI
            0x067b,  // Prolific
            0x303a   // ESP32
        )
        
        notifyOutput("Checking if device is compatible:")
        notifyOutput("  - Vendor ID: 0x${device.vendorId.toString(16)}")
        
        // Check if it's a known USB-Serial converter
        if (device.vendorId in supportedVendorIds) {
            notifyOutput("  - Supported vendor ID found!")
            return true
        }
        
        // Check if it's a CDC device
        if (device.deviceClass == UsbConstants.USB_CLASS_CDC_DATA ||
            device.deviceClass == UsbConstants.USB_CLASS_COMM ||
            device.interfaceCount > 0 && device.getInterface(0).interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
            notifyOutput("  - CDC device detected!")
            return true
        }
        
        notifyOutput("  - Device not compatible")
        return false
    }

    private fun setupDevice(device: UsbDevice) {
        Log.d(TAG, "Setting up device: ${device.deviceName}")
        usbDevice = device
        usbConnection = usbManager.openDevice(device)
        
        if (usbConnection == null) {
            Log.e(TAG, "Failed to open device connection")
            Log.e(TAG, "Make sure the app has USB permissions")
            return
        }

        // Try each interface
        for (i in 0 until device.interfaceCount) {
            try {
                val intf = device.getInterface(i)
                Log.d(TAG, "Trying interface $i: class=${intf.interfaceClass}")
                
                if (usbConnection!!.claimInterface(intf, true)) {
                    usbInterface = intf
                    Log.d(TAG, "Successfully claimed interface $i")
                    
                    // Find endpoints
                    for (j in 0 until intf.endpointCount) {
                        val endpoint = intf.getEndpoint(j)
                        Log.d(TAG, "Endpoint $j:")
                        Log.d(TAG, "- Type: ${endpoint.type}")
                        Log.d(TAG, "- Direction: ${endpoint.direction}")
                        Log.d(TAG, "- Address: 0x${endpoint.address.toString(16)}")
                        Log.d(TAG, "- Attributes: 0x${endpoint.attributes.toString(16)}")
                        Log.d(TAG, "- Max Packet Size: ${endpoint.maxPacketSize}")
                        
                        if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                                inEndpoint = endpoint
                                Log.d(TAG, "Found IN endpoint")
                            } else if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                                outEndpoint = endpoint
                                Log.d(TAG, "Found OUT endpoint")
                            }
                        }
                    }
                    
                    if (inEndpoint != null && outEndpoint != null) {
                        Log.d(TAG, "Found both endpoints, initializing serial connection")
                        initializeSerialConnection()
                        return
                    }
                } else {
                    Log.e(TAG, "Failed to claim interface $i")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up interface $i", e)
                e.printStackTrace()
            }
        }
        
        Log.e(TAG, "Failed to find suitable interface with endpoints")
        disconnect()
    }

    private fun initializeSerialConnection() {
        try {
            Log.d(TAG, "Initializing serial connection with baud rate: $currentBaudRate")
            
            // Reset device
            usbConnection?.controlTransfer(
                UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_DIR_OUT,
                0x22,  // CDC_SET_CONTROL_LINE_STATE
                0,     // Disable carrier and DTE
                0,     // Interface number
                null,
                0,
                TIMEOUT
            )
            
            Thread.sleep(100) // Give device time to reset
            
            // Enable device
            usbConnection?.controlTransfer(
                UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_DIR_OUT,
                0x22,  // CDC_SET_CONTROL_LINE_STATE
                0x01,  // Enable carrier and DTE
                0,     // Interface number
                null,
                0,
                TIMEOUT
            )
            
            // Set baud rate and serial parameters (8N1)
            val baudrateArray = ByteArray(7)
            baudrateArray[0] = (currentBaudRate and 0xff).toByte()
            baudrateArray[1] = (currentBaudRate shr 8 and 0xff).toByte()
            baudrateArray[2] = (currentBaudRate shr 16 and 0xff).toByte()
            baudrateArray[3] = (currentBaudRate shr 24 and 0xff).toByte()
            baudrateArray[4] = 0  // 1 stop bit
            baudrateArray[5] = 0  // No parity
            baudrateArray[6] = 8  // 8 data bits

            val result = usbConnection?.controlTransfer(
                UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_DIR_OUT,
                0x20,  // CDC_SET_LINE_CODING
                0,     // Value
                0,     // Interface number
                baudrateArray,
                baudrateArray.size,
                TIMEOUT
            )
            
            if (result != baudrateArray.size) {
                Log.e(TAG, "Failed to set baud rate: $result")
            } else {
                Log.d(TAG, "Successfully set baud rate to $currentBaudRate")
            }
            
            // Flush buffers
            val emptyBuf = ByteArray(1)
            usbConnection?.bulkTransfer(inEndpoint, emptyBuf, 1, 1)

            startReading()

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing serial connection", e)
            e.printStackTrace()
        }
    }

    override fun sendCommand(command: String) {
        try {
            if (usbConnection == null || outEndpoint == null) {
                notifyOutput("USB not connected. Attempting to reconnect...")
                connect()
                return
            }
            
            val data = "$command\n".toByteArray()
            notifyOutput("Sending command: $command")
            val result = usbConnection?.bulkTransfer(outEndpoint, data, data.size, TIMEOUT) ?: -1
            
            if (result < 0) {
                notifyOutput("Failed to send command: $command")
                // Try to reconnect and send again
                disconnect()
                connect()
                Thread.sleep(1000) // Wait for connection to establish
                val retryResult = usbConnection?.bulkTransfer(outEndpoint, data, data.size, TIMEOUT) ?: -1
                if (retryResult < 0) {
                    notifyOutput("Retry failed to send command: $command")
                } else {
                    notifyOutput("Retry successful: $command, Bytes sent: $retryResult")
                }
            } else {
                notifyOutput("Command sent successfully: $command, Bytes sent: $result")
            }
        } catch (e: Exception) {
            notifyOutput("Error sending command: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun setScreenPacketListener(listener: ((ByteArray) -> Unit)?) {
        screenPacketListener = listener
    }

    override fun isConnected(): Boolean = usbConnection != null && outEndpoint != null

    /** Raw bytes, no newline appended — keystrokes for the device UI. */
    override fun sendRaw(bytes: ByteArray) {
        val conn = usbConnection ?: return
        val ep = outEndpoint ?: return
        try {
            conn.bulkTransfer(ep, bytes, bytes.size, TIMEOUT)
        } catch (e: Exception) {
            Log.e(TAG, "sendRaw failed", e)
        }
    }

    /**
     * Single reader for the IN endpoint: text lines go to the terminal, 0xAA-framed
     * binary blocks (LOG_PACKET_HEADER, byte[1] = total size) go to the screen mirror.
     */
    private fun startReading() {
        stopReading()
        reading = true
        readThread = Thread {
            val rawBuf = ByteArray(4096)
            val framer = SerialFramer(
                onLine = { notifyOutput(it) },
                onPacket = { screenPacketListener?.invoke(it) }
            )
            while (reading) {
                val conn = usbConnection
                val ep = inEndpoint
                if (conn == null || ep == null) break
                val read = try {
                    conn.bulkTransfer(ep, rawBuf, rawBuf.size, 100)
                } catch (e: Exception) {
                    break
                }
                if (read > 0) framer.feed(rawBuf, read)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun stopReading() {
        reading = false
        readThread?.join(400)
        readThread = null
    }

    override fun disconnect() {
        try {
            Log.d(TAG, "Disconnecting USB device")
            stopReading()
            usbConnection?.releaseInterface(usbInterface)
            usbConnection?.close()
            usbConnection = null
            usbInterface = null
            inEndpoint = null
            outEndpoint = null
            usbDevice = null
            
            try {
                if (receiverRegistered) {
                    context.unregisterReceiver(usbPermissionReceiver)
                    receiverRegistered = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
    }
}
