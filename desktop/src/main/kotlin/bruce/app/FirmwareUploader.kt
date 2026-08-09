package bruce.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URI

data class DeviceInfo(val id: String, val name: String, val category: String)

object FirmwareUploader {

    suspend fun loadDeviceList(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "https://raw.githubusercontent.com/pr3y/Bruce/refs/heads/WebPage/src/lib/data/manifests.json"
            val json = JSONObject(URI(url).toURL().readText())
            val result = mutableListOf<DeviceInfo>()
            json.keys().forEach { category ->
                val arr = json.getJSONArray(category)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    result.add(DeviceInfo(obj.getString("id"), obj.getString("name"), category))
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun uploadFirmware(
        deviceId: String,
        port: String,
        baudRate: String,
        onOutput: suspend (String) -> Unit
    ) {
        suspend fun emit(msg: String) = withContext(Dispatchers.Main) { onOutput(msg) }

        withContext(Dispatchers.IO) {
            try {
                // Download firmware
                emit("Downloading firmware for $deviceId...")
                val firmwareUrl = "https://github.com/pr3y/Bruce/releases/download/1.16/Bruce-$deviceId.bin"
                val firmwareData = URI(firmwareUrl).toURL().readBytes()
                emit("Downloaded ${firmwareData.size} bytes")

                val tempFile = File.createTempFile("bruce-firmware", ".bin")
                tempFile.writeBytes(firmwareData)
                emit("Saved to: ${tempFile.absolutePath}")

                val esptoolArgs = listOf(
                    "--chip", "esp32s3",
                    "--port", port,
                    "--baud", baudRate,
                    "--before", "default_reset",
                    "--after", "hard_reset",
                    "--no-stub",
                    "write_flash", "-z", "0x0",
                    tempFile.absolutePath
                )

                // Prefer bundled esptool binary (auto-downloads on first use), fall back to system
                val esptoolBin = EsptoolManager.ensureReady(onOutput)
                val command = if (esptoolBin != null) {
                    listOf(esptoolBin.absolutePath) + esptoolArgs
                } else {
                    systemFallbackCommand(esptoolArgs)
                }

                emit("Running: ${command.joinToString(" ")}")

                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

                val reader = process.inputStream.bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    emit(line!!)
                }

                val exitCode = process.waitFor()
                tempFile.delete()

                if (exitCode == 0) {
                    emit("Firmware upload completed successfully!")
                } else {
                    emit("esptool exited with code $exitCode")
                }
            } catch (e: Exception) {
                emit("Error: ${e.message}")
            }
        }
    }

    // Last-resort fallback: try system-installed esptool or python -m esptool
    private fun systemFallbackCommand(args: List<String>): List<String> {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val candidates = if (isWindows) listOf("esptool.exe", "esptool.py") else listOf("esptool", "esptool.py")
        for (cmd in candidates) {
            if (commandExists(cmd)) return listOf(cmd) + args
        }
        val python = if (commandExists("python3")) "python3" else "python"
        return listOf(python, "-m", "esptool") + args
    }

    private fun commandExists(cmd: String): Boolean = try {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        ProcessBuilder(if (isWindows) listOf("where", cmd) else listOf("which", cmd))
            .start().waitFor() == 0
    } catch (_: Exception) { false }
}
