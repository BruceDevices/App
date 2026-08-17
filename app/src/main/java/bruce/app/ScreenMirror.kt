package bruce.app

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import bruce.app.ui.theme.Black
import bruce.app.ui.theme.DarkGray
import bruce.app.ui.theme.LightGray
import bruce.app.ui.theme.PurpleAccent
import bruce.app.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Render at 3× so text gets real pixels instead of 8-pixel blobs
private const val SCALE = 3

/** Display function ids from the firmware's serial display protocol. */
private object Fn {
    const val FILLSCREEN = 0
    const val DRAWRECT = 1
    const val FILLRECT = 2
    const val DRAWROUNDRECT = 3
    const val FILLROUNDRECT = 4
    const val DRAWCIRCLE = 5
    const val FILLCIRCLE = 6
    const val DRAWTRIANGLE = 7
    const val FILLTRIANGLE = 8
    const val DRAWELLIPSE = 9
    const val FILLELLIPSE = 10
    const val DRAWLINE = 11
    const val DRAWARC = 12
    const val DRAWWIDELINE = 13
    const val DRAWCENTRESTRING = 14
    const val DRAWRIGHTSTRING = 15
    const val DRAWSTRING = 16
    const val PRINT = 17
    const val DRAWIMAGE = 18
    const val DRAWPIXEL = 19
    const val DRAWFASTVLINE = 20
    const val DRAWFASTHLINE = 21
    const val SCREEN_INFO = 99
}

private fun u16(d: ByteArray, i: Int): Int {
    if (i + 1 >= d.size) return 0
    return ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)
}

private fun rgb565(c: Int): Int = android.graphics.Color.rgb(
    (c shr 11 and 0x1F) * 255 / 31,
    (c shr 5 and 0x3F) * 255 / 63,
    (c and 0x1F) * 255 / 31
)

private fun draw(packet: ByteArray, c: AndroidCanvas, paint: Paint, sw: Int, sh: Int) {
    if (packet.size < 3) return
    val fn = packet[2].toInt() and 0xFF
    val p = 3

    fun fill(color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = rgb565(color)
    }

    fun stroke(color: Int, width: Float = 1f) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = rgb565(color)
    }

    when (fn) {
        Fn.FILLSCREEN -> {
            fill(u16(packet, p))
            c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        }
        Fn.DRAWRECT, Fn.FILLRECT -> {
            val x = u16(packet, p); val y = u16(packet, p + 2)
            val w = u16(packet, p + 4); val h = u16(packet, p + 6)
            if (fn == Fn.FILLRECT) fill(u16(packet, p + 8)) else stroke(u16(packet, p + 8))
            c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
        }
        Fn.DRAWROUNDRECT, Fn.FILLROUNDRECT -> {
            val x = u16(packet, p); val y = u16(packet, p + 2)
            val w = u16(packet, p + 4); val h = u16(packet, p + 6)
            val r = u16(packet, p + 8).toFloat()
            if (fn == Fn.FILLROUNDRECT) fill(u16(packet, p + 10)) else stroke(u16(packet, p + 10))
            c.drawRoundRect(
                RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()), r, r, paint
            )
        }
        Fn.DRAWCIRCLE, Fn.FILLCIRCLE -> {
            val cx = u16(packet, p); val cy = u16(packet, p + 2); val r = u16(packet, p + 4)
            if (fn == Fn.FILLCIRCLE) fill(u16(packet, p + 6)) else stroke(u16(packet, p + 6))
            c.drawCircle(cx.toFloat(), cy.toFloat(), r.toFloat(), paint)
        }
        Fn.DRAWTRIANGLE, Fn.FILLTRIANGLE -> {
            val path = Path().apply {
                moveTo(u16(packet, p).toFloat(), u16(packet, p + 2).toFloat())
                lineTo(u16(packet, p + 4).toFloat(), u16(packet, p + 6).toFloat())
                lineTo(u16(packet, p + 8).toFloat(), u16(packet, p + 10).toFloat())
                close()
            }
            if (fn == Fn.FILLTRIANGLE) fill(u16(packet, p + 12)) else stroke(u16(packet, p + 12))
            c.drawPath(path, paint)
        }
        Fn.DRAWELLIPSE, Fn.FILLELLIPSE -> {
            val cx = u16(packet, p); val cy = u16(packet, p + 2)
            val rx = u16(packet, p + 4); val ry = u16(packet, p + 6)
            if (fn == Fn.FILLELLIPSE) fill(u16(packet, p + 8)) else stroke(u16(packet, p + 8))
            c.drawOval(
                RectF((cx - rx).toFloat(), (cy - ry).toFloat(), (cx + rx).toFloat(), (cy + ry).toFloat()),
                paint
            )
        }
        Fn.DRAWLINE -> {
            stroke(u16(packet, p + 8))
            c.drawLine(
                u16(packet, p).toFloat(), u16(packet, p + 2).toFloat(),
                u16(packet, p + 4).toFloat(), u16(packet, p + 6).toFloat(), paint
            )
        }
        Fn.DRAWARC -> {
            val cx = u16(packet, p); val cy = u16(packet, p + 2)
            val r1 = u16(packet, p + 4); val r2 = u16(packet, p + 6)
            val startAngle = u16(packet, p + 8)
            val endAngle = u16(packet, p + 10)
            val fgColor = u16(packet, p + 12)
            // A ring segment, stroked — not a filled pie. Icons stack concentric arcs
            // (WiFi waves, RF/LoRa/IR/RFID/BLE), so filling the hole with bgColor would
            // wipe out the arcs drawn underneath. Matches the firmware's own renderer
            // (sd_files/esp32_serial_navigator.html, case 12).
            val outerR = maxOf(r1, r2)
            val innerR = minOf(r1, r2)
            val radius = (outerR + innerR) / 2f
            val band = (outerR - innerR).coerceAtLeast(1).toFloat()
            // TFT_eSPI: 0° = 6 o'clock, clockwise → Android: 0° = 3 o'clock, clockwise positive.
            val start = 90f + startAngle
            val sweep = (if (endAngle >= startAngle) endAngle - startAngle else endAngle + 360 - startAngle).toFloat()
            stroke(fgColor, band)
            c.drawArc(
                RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                start, sweep, false, paint
            )
            paint.strokeWidth = 1f
        }
        Fn.DRAWWIDELINE -> {
            val ax = u16(packet, p); val ay = u16(packet, p + 2)
            val bx = u16(packet, p + 4); val by = u16(packet, p + 6)
            val wd = u16(packet, p + 8)
            val savedCap = paint.strokeCap
            paint.strokeCap = Paint.Cap.ROUND
            stroke(u16(packet, p + 10), maxOf(1, wd).toFloat())
            c.drawLine(ax.toFloat(), ay.toFloat(), bx.toFloat(), by.toFloat(), paint)
            paint.strokeCap = savedCap
            paint.strokeWidth = 1f
        }
        Fn.DRAWPIXEL -> {
            val x = u16(packet, p); val y = u16(packet, p + 2)
            fill(u16(packet, p + 4))
            c.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat(), paint)
        }
        Fn.DRAWFASTVLINE -> {
            val x = u16(packet, p); val y = u16(packet, p + 2); val h = u16(packet, p + 4)
            stroke(u16(packet, p + 6))
            c.drawLine(x.toFloat(), y.toFloat(), x.toFloat(), (y + h).toFloat(), paint)
        }
        Fn.DRAWFASTHLINE -> {
            val x = u16(packet, p); val y = u16(packet, p + 2); val w = u16(packet, p + 4)
            stroke(u16(packet, p + 6))
            c.drawLine(x.toFloat(), y.toFloat(), (x + w).toFloat(), y.toFloat(), paint)
        }
        Fn.DRAWSTRING, Fn.DRAWCENTRESTRING, Fn.DRAWRIGHTSTRING, Fn.PRINT -> {
            val xTft = u16(packet, p)
            val yTft = u16(packet, p + 2)
            val tSize = u16(packet, p + 4).coerceAtLeast(1)
            val tColor = u16(packet, p + 6)
            val bgColor = u16(packet, p + 8)
            val strOff = p + 10
            if (strOff >= packet.size) return
            val str = String(packet, strOff, packet.size - strOff, Charsets.UTF_8)
                .trimEnd(' ', ' ', '\n', '\r')
            if (str.isEmpty()) return

            paint.textSize = (tSize * 8).toFloat()
            val fm = paint.fontMetrics
            val strW = paint.measureText(str)
            val drawX = when (fn) {
                Fn.DRAWCENTRESTRING -> xTft - strW / 2f
                Fn.DRAWRIGHTSTRING -> xTft - strW
                else -> xTft.toFloat()
            }
            // TFT_eSPI sets textbgcolor == textcolor to mean "transparent background"
            if (bgColor != tColor) {
                fill(bgColor)
                c.drawRect(drawX, yTft.toFloat(), drawX + strW, yTft + (fm.descent - fm.ascent), paint)
            }
            fill(tColor)
            c.drawText(str, drawX, yTft - fm.ascent, paint)
        }
        Fn.DRAWIMAGE -> {
            // Image files live on the device; render a placeholder
            val x = u16(packet, p).toFloat(); val y = u16(packet, p + 2).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.rgb(50, 50, 70)
            c.drawRect(x, y, x + 32, y + 32, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = android.graphics.Color.rgb(100, 100, 140)
            c.drawRect(x, y, x + 32, y + 32, paint)
        }
    }
}

/** Thread-safe canvas — packets applied from the transport thread, read on the UI thread. */
private class ScreenCanvas(typeface: Typeface) {
    var logW = 240; var logH = 135
    private var bmp = newBuf(logW, logH)
    private val lock = Any()
    @Volatile var dirty = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = true
        isSubpixelText = true
        this.typeface = typeface
    }

    private fun newBuf(w: Int, h: Int) =
        Bitmap.createBitmap(w * SCALE, h * SCALE, Bitmap.Config.RGB_565)

    fun apply(packet: ByteArray) {
        if (packet.size < 3) return
        val fn = packet[2].toInt() and 0xFF
        synchronized(lock) {
            if (fn == Fn.SCREEN_INFO && packet.size >= 8) {
                val w = u16(packet, 3); val h = u16(packet, 5)
                if (w > 0 && h > 0 && (w != logW || h != logH)) {
                    logW = w; logH = h; bmp = newBuf(w, h)
                }
                return
            }
            val c = AndroidCanvas(bmp)
            c.scale(SCALE.toFloat(), SCALE.toFloat())
            draw(packet, c, paint, logW, logH)
            dirty = true
        }
    }

    // ponytail: copies the frame so Compose sees a new ImageBitmap; ~0.5 MB/frame at 240×135×3.
    // If that ever shows up in a profile, draw the live Bitmap in a Canvas keyed on a frame counter.
    fun snapshot(): ImageBitmap = synchronized(lock) {
        dirty = false
        bmp.copy(Bitmap.Config.RGB_565, false).asImageBitmap()
    }
}

/**
 * Live device screen over a serial transport (USB OTG today, BLE once the firmware
 * NUS bridge lands) — same "display start"/"nav …" commands the desktop app uses.
 */
@Composable
fun ScreenMirrorPanel(
    serial: SerialCommunication?,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val typeface = remember {
        runCatching { ResourcesCompat.getFont(context, R.font.vt323_regular) }.getOrNull()
            ?: Typeface.MONOSPACE
    }
    val canvas = remember { ScreenCanvas(typeface) }
    var isStreaming by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var packetCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    // The panel owns the port while it is open and releases it on exit (see onDispose),
    // so esptool can claim the device for flashing.
    var isConnected by remember { mutableStateOf(serial?.isConnected() == true) }

    LaunchedEffect(Unit) {
        if (!isConnected) withContext(Dispatchers.IO) { serial?.connect() }
        while (isActive && !isConnected) {          // USB permission grant is async
            delay(500)
            isConnected = serial?.isConnected() == true
        }
    }

    fun send(cmd: String) {
        scope.launch(Dispatchers.IO) { serial?.sendCommand(cmd) }
    }

    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            serial?.setScreenPacketListener { pkt ->
                canvas.apply(pkt)
                packetCount++
            }
            withContext(Dispatchers.IO) { serial?.sendCommand("display start") }
            while (isActive) {
                if (canvas.dirty) bitmap = withContext(Dispatchers.Default) { canvas.snapshot() }
                delay(33)
            }
        } else {
            withContext(Dispatchers.IO) { serial?.sendCommand("display stop") }
            serial?.setScreenPacketListener(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Every exit path lands here (Close, back button, dialog dismiss): tell the
            // device to stop streaming, then hand the port back — a claimed interface or a
            // device still spewing display packets makes esptool's flash fail.
            if (serial?.isConnected() == true) serial.sendCommand("display stop")
            serial?.setScreenPacketListener(null)
            serial?.disconnect()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Screen Mirror (USB OTG)",
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isStreaming) "$packetCount pkts" else "idle",
                color = if (isStreaming) PurpleAccent else White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onClose) { Text("Close", color = White) }
        }

        if (!isConnected) {
            Text(
                text = "No USB device attached — plug the Bruce device into the OTG cable and grant permission.",
                color = White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Live screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(DarkGray, RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    if (isStreaming) PurpleAccent else LightGray,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Bruce device screen",
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = when {
                        !isConnected -> "NO DEVICE"
                        isStreaming -> "Waiting for draw calls…\nnavigate the device to trigger a repaint"
                        else -> "Start the mirror to stream the device screen over USB"
                    },
                    color = White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { isStreaming = !isStreaming },
            enabled = isConnected,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isStreaming) LightGray else PurpleAccent,
                contentColor = White
            )
        ) { Text(if (isStreaming) "Stop mirror" else "Start mirror") }

        Spacer(Modifier.height(12.dp))

        // D-pad
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NavBtn("▲", isConnected) { send("nav up") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NavBtn("◄", isConnected) { send("nav prev") }
                NavBtn("OK", isConnected) { send("nav select") }
                NavBtn("►", isConnected) { send("nav next") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NavBtn("▼", isConnected) { send("nav down") }
                NavBtn("ESC", isConnected, wide = true) { send("nav esc") }
            }
        }
    }
}

@Composable
private fun NavBtn(label: String, enabled: Boolean, wide: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = if (wide) Modifier.width(86.dp).height(52.dp) else Modifier.size(52.dp),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LightGray,
            contentColor = White,
            disabledContainerColor = DarkGray,
            disabledContentColor = White.copy(alpha = 0.3f)
        )
    ) { Text(label, fontSize = 16.sp) }
}
