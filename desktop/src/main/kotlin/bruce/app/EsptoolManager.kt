package bruce.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipInputStream

object EsptoolManager {
    private const val VERSION = "4.8.1"
    private val cacheDir = File(System.getProperty("user.home"), ".bruce-app/esptool-$VERSION")

    private val isWindows get() = System.getProperty("os.name").lowercase().contains("win")
    val executableName get() = if (isWindows) "esptool.exe" else "esptool"

    val cachedBinary: File?
        get() = File(cacheDir, executableName).takeIf { it.exists() && it.canExecute() }

    private fun downloadUrl(): String? {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val base = "https://github.com/espressif/esptool/releases/download/v$VERSION"
        return when {
            os.contains("linux") && arch in listOf("amd64", "x86_64") ->
                "$base/esptool-v${VERSION}-linux-amd64.zip"
            os.contains("mac") && arch == "aarch64" ->
                "$base/esptool-v${VERSION}-macos-arm64.zip"
            os.contains("mac") ->
                "$base/esptool-v${VERSION}-macos.zip"
            os.contains("win") ->
                "$base/esptool-v${VERSION}-win64.zip"
            else -> null  // Linux ARM etc. — fall back to system Python
        }
    }

    // Returns the ready-to-use binary, downloading and extracting it on first call.
    // Falls back gracefully by returning null (caller then tries system Python).
    suspend fun ensureReady(onOutput: suspend (String) -> Unit): File? {
        cachedBinary?.let { return it }

        val url = downloadUrl() ?: run {
            onOutput("No bundled esptool for this platform — install via: pip install esptool")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                cacheDir.mkdirs()
                val zipFile = File(cacheDir, "esptool.zip")

                emit("Downloading esptool v$VERSION (one-time, ~10 MB)...", onOutput)

                val conn = URI(url).toURL().openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connect()
                val total = conn.contentLengthLong
                var downloaded = 0L
                var lastReported = -1

                conn.inputStream.use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buf = ByteArray(65536)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) {
                                val pct = (downloaded * 100 / total).toInt()
                                if (pct / 10 != lastReported / 10) {
                                    lastReported = pct
                                    emit("Downloading esptool... $pct%", onOutput)
                                }
                            }
                        }
                    }
                }

                emit("Extracting esptool...", onOutput)

                val binary = File(cacheDir, executableName)
                ZipInputStream(zipFile.inputStream()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val isTarget = !entry.isDirectory &&
                            (entry.name == executableName || entry.name.endsWith("/$executableName"))
                        if (isTarget) {
                            FileOutputStream(binary).use { out -> zip.copyTo(out) }
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                zipFile.delete()

                if (!binary.exists()) {
                    emit("Extraction failed — falling back to system Python", onOutput)
                    return@withContext null
                }

                if (!isWindows) binary.setExecutable(true)

                emit("esptool ready", onOutput)
                binary
            } catch (e: Exception) {
                emit("esptool download failed: ${e.message}", onOutput)
                null
            }
        }
    }

    private suspend fun emit(msg: String, onOutput: suspend (String) -> Unit) =
        withContext(Dispatchers.Main) { onOutput(msg) }
}
