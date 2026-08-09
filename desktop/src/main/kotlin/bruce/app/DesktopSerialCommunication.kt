package bruce.app

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.*

class DesktopSerialCommunication {
    private var serialPort: SerialPort? = null
    private var outputListener: ((String) -> Unit)? = null
    private var screenPacketListener: ((ByteArray) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null

    // systemPortPath, not systemPortName: esptool needs the real device path (/dev/ttyACM0),
    // and jSerialComm's getCommPort() accepts it too.
    fun listPorts(): List<String> = SerialPort.getCommPorts().map { it.systemPortPath }

    fun connect(portName: String, baudRate: Int): Boolean {
        return try {
            val port = SerialPort.getCommPort(portName)
            port.baudRate = baudRate
            port.numDataBits = 8
            port.numStopBits = SerialPort.ONE_STOP_BIT
            port.parity = SerialPort.NO_PARITY
            if (port.openPort()) {
                serialPort = port
                startReading()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            outputListener?.invoke("Connect error: ${e.message}")
            false
        }
    }

    fun disconnect() {
        readJob?.cancel()
        serialPort?.closePort()
        serialPort = null
    }

    fun isConnected(): Boolean = serialPort?.isOpen == true

    fun sendCommand(command: String) {
        val port = serialPort?.takeIf { it.isOpen } ?: return
        val data = "$command\n".toByteArray(Charsets.UTF_8)
        port.writeBytes(data, data.size)
    }

    fun sendRaw(bytes: ByteArray) {
        val port = serialPort?.takeIf { it.isOpen } ?: return
        port.writeBytes(bytes, bytes.size)
    }

    fun setBaudRate(baudRate: Int) {
        serialPort?.baudRate = baudRate
    }

    fun setOutputListener(listener: (String) -> Unit) {
        outputListener = listener
    }

    fun setScreenPacketListener(listener: ((ByteArray) -> Unit)?) {
        screenPacketListener = listener
    }

    private fun startReading() {
        readJob = scope.launch {
            val rawBuf = ByteArray(4096)
            val textBuf = StringBuilder()
            // Binary packet accumulator — max packet is MAX_LOG_SIZE (128 bytes with PSRAM)
            val pktBuf = ByteArray(256)
            var pktPos = 0
            var pktExpectedSize = 0

            while (isActive && serialPort?.isOpen == true) {
                val available = serialPort?.bytesAvailable() ?: -1
                if (available > 0) {
                    val toRead = minOf(available, rawBuf.size)
                    val read = serialPort?.readBytes(rawBuf, toRead) ?: 0
                    for (i in 0 until read) {
                        val b = rawBuf[i]
                        val ub = b.toInt() and 0xFF

                        if (pktPos > 0) {
                            // Accumulating a binary packet
                            pktBuf[pktPos++] = b
                            if (pktPos == 2) pktExpectedSize = ub
                            if (pktExpectedSize > 0 && pktPos >= pktExpectedSize) {
                                screenPacketListener?.invoke(pktBuf.copyOf(pktPos))
                                pktPos = 0
                                pktExpectedSize = 0
                            }
                            // Safety: reset if overrun (corrupted stream)
                            if (pktPos >= pktBuf.size) {
                                pktPos = 0
                                pktExpectedSize = 0
                            }
                        } else if (ub == 0xAA) {
                            // 0xAA is LOG_PACKET_HEADER — not printable ASCII, safe to treat as packet start
                            if (textBuf.isNotEmpty()) {
                                outputListener?.invoke(textBuf.toString().trimEnd('\r'))
                                textBuf.clear()
                            }
                            pktBuf[0] = b
                            pktPos = 1
                        } else if (b == '\n'.code.toByte()) {
                            outputListener?.invoke(textBuf.toString().trimEnd('\r'))
                            textBuf.clear()
                        } else {
                            textBuf.append(ub.toChar())
                        }
                    }
                } else {
                    delay(10)
                }
            }
        }
    }
}
