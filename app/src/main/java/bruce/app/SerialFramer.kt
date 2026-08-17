package bruce.app

/**
 * Splits Bruce's byte stream into text lines and binary display packets.
 * 0xAA is LOG_PACKET_HEADER (not printable ASCII, so it is safe as a start marker);
 * byte[1] of a packet is its total size.
 *
 * Pure Kotlin on purpose: USB feeds it bulk reads, BLE will feed it NUS notifications.
 * Not thread safe — feed it from one reader thread.
 */
class SerialFramer(
    private val onLine: (String) -> Unit,
    private val onPacket: (ByteArray) -> Unit
) {
    private val text = StringBuilder()
    private val pkt = ByteArray(256)   // MAX_LOG_SIZE is 128 with PSRAM
    private var pos = 0
    private var expected = 0

    fun feed(buf: ByteArray, length: Int = buf.size) {
        for (i in 0 until length) {
            val b = buf[i]
            val ub = b.toInt() and 0xFF

            if (pos > 0) {
                pkt[pos++] = b
                if (pos == 2) expected = ub
                if (expected > 0 && pos >= expected) {
                    onPacket(pkt.copyOf(pos))
                    pos = 0; expected = 0
                } else if (pos >= pkt.size) {   // corrupted stream, resync
                    pos = 0; expected = 0
                }
            } else if (ub == 0xAA) {
                flushText()
                pkt[0] = b
                pos = 1
            } else if (b == '\n'.code.toByte()) {
                onLine(text.toString().trimEnd('\r'))
                text.clear()
            } else {
                text.append(ub.toChar())
            }
        }
    }

    private fun flushText() {
        if (text.isNotEmpty()) {
            onLine(text.toString().trimEnd('\r'))
            text.clear()
        }
    }
}

// Self-check: java -cp <kotlin-stdlib> bruce.app.SerialFramerKt
fun main() {
    val lines = mutableListOf<String>()
    val packets = mutableListOf<ByteArray>()
    val f = SerialFramer({ lines += it }, { packets += it })

    // text line, then a 6-byte FILLSCREEN packet, split across two feeds mid-packet
    f.feed("hi\r\n".toByteArray() + byteArrayOf(0xAA.toByte(), 6, 0, 0x07))
    f.feed(byteArrayOf(0xE0.toByte(), 0x01) + "ok\n".toByteArray())

    check(lines == listOf("hi", "ok")) { "lines=$lines" }
    check(packets.size == 1) { "packets=${packets.size}" }
    check(packets[0].toList() == listOf<Byte>(0xAA.toByte(), 6, 0, 0x07, 0xE0.toByte(), 0x01))

    // text pending when a packet starts must be flushed, not swallowed
    lines.clear(); packets.clear()
    f.feed("noeol".toByteArray() + byteArrayOf(0xAA.toByte(), 4, 99, 0))
    check(lines == listOf("noeol")) { "lines=$lines" }
    check(packets.size == 1 && packets[0].size == 4)

    // oversized/garbage packet length must not overrun the buffer
    lines.clear(); packets.clear()
    f.feed(byteArrayOf(0xAA.toByte(), 0) + ByteArray(300) { 1 })
    check(packets.isEmpty()) { "expected resync, got ${packets.size} packets" }

    println("SerialFramer OK")
}
