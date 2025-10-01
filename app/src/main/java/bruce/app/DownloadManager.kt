package bruce.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

private fun saveAsFile(content: ByteArray): String {
    val tempFile = File.createTempFile("firmware", ".bin")
    tempFile.writeBytes(content)
    println("Temp File Byte Count: ${tempFile.length()}")
    return tempFile.absolutePath
}

fun uploadFirmware(context: android.content.Context, deviceId: String, baudRate: String, onStatusChange: (String) -> Unit, onLoadingChange: (Boolean) -> Unit, onInstallationComplete: () -> Unit) {
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

fun loadDeviceList(onResult: (List<DeviceInfo>) -> Unit) {
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