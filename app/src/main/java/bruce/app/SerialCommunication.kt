package bruce.app

/**
 * A transport that speaks Bruce's serial command protocol.
 * USB OTG today ([AndroidSerialCommunication]); BLE UART is the next implementation,
 * so keep everything UI-side talking to this interface, never to a concrete transport.
 */
interface SerialCommunication {
    fun connect()
    fun disconnect()
    fun isConnected(): Boolean
    fun sendCommand(command: String)
    /** Raw bytes, no newline appended — keystrokes for the device UI. */
    fun sendRaw(bytes: ByteArray)
    fun setBaudRate(baudRate: Int)
    fun setOutputListener(listener: (String) -> Unit)
    /** 0xAA-framed display packets, already de-framed. Null to stop listening. */
    fun setScreenPacketListener(listener: ((ByteArray) -> Unit)?)
}
