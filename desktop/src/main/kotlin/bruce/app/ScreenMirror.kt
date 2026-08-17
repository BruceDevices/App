package bruce.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Font as AwtFont
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.image.BufferedImage

// Render at 3× so text gets real pixels instead of 8-pixel blobs
private const val SCALE = 3

private val vt323Base: AwtFont by lazy {
    try {
        val stream = object {}.javaClass.getResourceAsStream("/fonts/VT323-Regular.ttf")!!
        AwtFont.createFont(AwtFont.TRUETYPE_FONT, stream)
    } catch (_: Exception) {
        AwtFont(AwtFont.MONOSPACED, AwtFont.BOLD, 12)
    }
}

private object Fn {
    const val FILLSCREEN      = 0
    const val DRAWRECT        = 1
    const val FILLRECT        = 2
    const val DRAWROUNDRECT   = 3
    const val FILLROUNDRECT   = 4
    const val DRAWCIRCLE      = 5
    const val FILLCIRCLE      = 6
    const val DRAWTRIANGLE    = 7
    const val FILLTRIANGLE    = 8
    const val DRAWELLIPSE     = 9
    const val FILLELLIPSE     = 10
    const val DRAWLINE        = 11
    const val DRAWARC         = 12
    const val DRAWWIDELINE    = 13
    const val DRAWCENTRESTRING = 14
    const val DRAWRIGHTSTRING  = 15
    const val DRAWSTRING       = 16
    const val PRINT            = 17
    const val DRAWIMAGE        = 18
    const val DRAWPIXEL        = 19
    const val DRAWFASTVLINE    = 20
    const val DRAWFASTHLINE    = 21
    const val SCREEN_INFO      = 99
}

private fun u16(d: ByteArray, i: Int): Int {
    if (i + 1 >= d.size) return 0
    return ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)
}

private fun rgb565(c: Int) = AwtColor(
    (c shr 11 and 0x1F) * 255 / 31,
    (c shr 5  and 0x3F) * 255 / 63,
    (c        and 0x1F) * 255 / 31
)

private fun draw(packet: ByteArray, g: Graphics2D, sw: Int, sh: Int) {
    if (packet.size < 3) return
    val fn = packet[2].toInt() and 0xFF
    val p  = 3

    when (fn) {
        Fn.FILLSCREEN -> {
            g.color = rgb565(u16(packet, p))
            g.fillRect(0, 0, sw, sh)
        }
        Fn.DRAWRECT -> {
            g.color = rgb565(u16(packet, p + 8))
            g.drawRect(u16(packet, p), u16(packet, p+2), u16(packet, p+4), u16(packet, p+6))
        }
        Fn.FILLRECT -> {
            g.color = rgb565(u16(packet, p + 8))
            g.fillRect(u16(packet, p), u16(packet, p+2), u16(packet, p+4), u16(packet, p+6))
        }
        Fn.DRAWROUNDRECT -> {
            val r = u16(packet, p+8)
            g.color = rgb565(u16(packet, p+10))
            g.drawRoundRect(u16(packet,p), u16(packet,p+2), u16(packet,p+4), u16(packet,p+6), r*2, r*2)
        }
        Fn.FILLROUNDRECT -> {
            val r = u16(packet, p+8)
            g.color = rgb565(u16(packet, p+10))
            g.fillRoundRect(u16(packet,p), u16(packet,p+2), u16(packet,p+4), u16(packet,p+6), r*2, r*2)
        }
        Fn.DRAWCIRCLE -> {
            val cx=u16(packet,p); val cy=u16(packet,p+2); val r=u16(packet,p+4)
            g.color = rgb565(u16(packet, p+6))
            g.drawOval(cx-r, cy-r, r*2, r*2)
        }
        Fn.FILLCIRCLE -> {
            val cx=u16(packet,p); val cy=u16(packet,p+2); val r=u16(packet,p+4)
            g.color = rgb565(u16(packet, p+6))
            g.fillOval(cx-r, cy-r, r*2, r*2)
        }
        Fn.DRAWTRIANGLE -> {
            val xs = intArrayOf(u16(packet,p), u16(packet,p+4), u16(packet,p+8))
            val ys = intArrayOf(u16(packet,p+2), u16(packet,p+6), u16(packet,p+10))
            g.color = rgb565(u16(packet, p+12))
            g.drawPolygon(xs, ys, 3)
        }
        Fn.FILLTRIANGLE -> {
            val xs = intArrayOf(u16(packet,p), u16(packet,p+4), u16(packet,p+8))
            val ys = intArrayOf(u16(packet,p+2), u16(packet,p+6), u16(packet,p+10))
            g.color = rgb565(u16(packet, p+12))
            g.fillPolygon(xs, ys, 3)
        }
        Fn.DRAWELLIPSE -> {
            val cx=u16(packet,p); val cy=u16(packet,p+2)
            val rx=u16(packet,p+4); val ry=u16(packet,p+6)
            g.color = rgb565(u16(packet, p+8))
            g.drawOval(cx-rx, cy-ry, rx*2, ry*2)
        }
        Fn.FILLELLIPSE -> {
            val cx=u16(packet,p); val cy=u16(packet,p+2)
            val rx=u16(packet,p+4); val ry=u16(packet,p+6)
            g.color = rgb565(u16(packet, p+8))
            g.fillOval(cx-rx, cy-ry, rx*2, ry*2)
        }
        Fn.DRAWLINE -> {
            g.color = rgb565(u16(packet, p+8))
            g.drawLine(u16(packet,p), u16(packet,p+2), u16(packet,p+4), u16(packet,p+6))
        }
        Fn.DRAWARC -> {
            val cx = u16(packet, p);    val cy = u16(packet, p+2)
            val r1 = u16(packet, p+4);  val r2 = u16(packet, p+6)
            val startAngle = u16(packet, p+8)
            val endAngle   = u16(packet, p+10)
            val fgColor    = u16(packet, p+12)
            // A ring segment, stroked — not a filled pie. Icons stack concentric arcs
            // (WiFi waves, RF/LoRa/IR/RFID/BLE), so filling the hole with bgColor would
            // wipe out the arcs drawn underneath. Matches the firmware's own renderer
            // (sd_files/esp32_serial_navigator.html, case 12).
            val outerR = maxOf(r1, r2)
            val innerR = minOf(r1, r2)
            val radius = (outerR + innerR) / 2.0
            val band   = maxOf(1, outerR - innerR).toFloat()
            // TFT_eSPI: 0°=6 o'clock, clockwise → Java2D: 0°=3 o'clock, counter-clockwise.
            // south is -90° in Java2D, and CW is negative there: jAngle = -90 - tftAngle.
            // Handle wrap-through-0° for the extent.
            val jStart   = -90 - startAngle
            val cwExtent = if (endAngle >= startAngle) endAngle - startAngle else endAngle + 360 - startAngle
            val jExtent  = -cwExtent
            val savedStroke = g.stroke
            g.stroke = BasicStroke(band)
            g.color = rgb565(fgColor)
            g.draw(Arc2D.Double(
                cx - radius, cy - radius, radius * 2, radius * 2,
                jStart.toDouble(), jExtent.toDouble(), Arc2D.OPEN
            ))
            g.stroke = savedStroke
        }
        Fn.DRAWWIDELINE -> {
            val ax = u16(packet, p);   val ay = u16(packet, p+2)
            val bx = u16(packet, p+4); val by = u16(packet, p+6)
            val wd = u16(packet, p+8)
            val savedStroke = g.stroke
            g.stroke = BasicStroke(maxOf(1, wd).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = rgb565(u16(packet, p+10))
            g.drawLine(ax, ay, bx, by)
            g.stroke = savedStroke
        }
        Fn.DRAWPIXEL -> {
            g.color = rgb565(u16(packet, p+4))
            g.fillRect(u16(packet, p), u16(packet, p+2), 1, 1)
        }
        Fn.DRAWFASTVLINE -> {
            val x=u16(packet,p); val y=u16(packet,p+2); val h=u16(packet,p+4)
            g.color = rgb565(u16(packet, p+6))
            g.drawLine(x, y, x, y+h)
        }
        Fn.DRAWFASTHLINE -> {
            val x=u16(packet,p); val y=u16(packet,p+2); val w=u16(packet,p+4)
            g.color = rgb565(u16(packet, p+6))
            g.drawLine(x, y, x+w, y)
        }
        Fn.DRAWSTRING, Fn.DRAWCENTRESTRING, Fn.DRAWRIGHTSTRING, Fn.PRINT -> {
            val xTft    = u16(packet, p)
            val yTft    = u16(packet, p+2)
            val tSize   = u16(packet, p+4).coerceAtLeast(1)
            val tColor  = u16(packet, p+6)
            val bgColor = u16(packet, p+8)
            val strOff  = p + 10
            if (strOff >= packet.size) return
            val str = String(packet, strOff, packet.size - strOff, Charsets.UTF_8)
                .trimEnd(' ', ' ', '\n', '\r')
            if (str.isEmpty()) return

            // Draw in TFT user-space (the 3× scale is already applied by the Graphics2D transform).
            // This avoids the clip issue that arises when resetting the transform to identity:
            // Java2D re-maps the user-space clip (0,0,sw,sh) through identity, restricting device
            // drawing to a 240×135 box and clipping text that falls below ~y=45 in TFT space.
            g.font = vt323Base.deriveFont((maxOf(1, tSize) * 8).toFloat())
            val fm   = g.getFontMetrics()
            val strW = fm.stringWidth(str)
            val drawX = when (fn) {
                Fn.DRAWCENTRESTRING -> xTft - strW / 2
                Fn.DRAWRIGHTSTRING  -> xTft - strW
                else                -> xTft
            }
            // TFT_eSPI sets textbgcolor == textcolor to mean "transparent background"
            if (bgColor != tColor) {
                g.color = rgb565(bgColor)
                g.fillRect(drawX, yTft, strW, fm.height)
            }
            g.color = rgb565(tColor)
            g.drawString(str, drawX, yTft + fm.ascent)
        }
        Fn.DRAWIMAGE -> {
            // Image files live on the device; render a placeholder
            val x=u16(packet,p); val y=u16(packet,p+2)
            g.color = AwtColor(50, 50, 70)
            g.fillRect(x, y, 32, 32)
            g.color = AwtColor(100, 100, 140)
            g.drawRect(x, y, 32, 32)
        }
    }
}

// Thread-safe canvas — packets applied from IO thread, read on Main thread
private class ScreenCanvas {
    var logW = 240; var logH = 135
    @Volatile var img = newBuf(logW, logH)
    val lock = Any()
    @Volatile var dirty = false

    private fun newBuf(w: Int, h: Int) =
        BufferedImage(w * SCALE, h * SCALE, BufferedImage.TYPE_INT_RGB)

    fun apply(packet: ByteArray) {
        if (packet.size < 3) return
        val fn = packet[2].toInt() and 0xFF
        synchronized(lock) {
            if (fn == Fn.SCREEN_INFO && packet.size >= 8) {
                val w = u16(packet, 3); val h = u16(packet, 5)
                if (w > 0 && h > 0 && (w != logW || h != logH)) {
                    logW = w; logH = h; img = newBuf(w, h)
                }
                return
            }
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY)
            g.scale(SCALE.toDouble(), SCALE.toDouble())
            draw(packet, g, logW, logH)
            g.dispose()
            dirty = true
        }
    }

    fun snapshot(): ImageBitmap = synchronized(lock) {
        dirty = false; img.toComposeImageBitmap()
    }
}

@Composable
fun ScreenMirrorPanel(
    serialComm: DesktopSerialCommunication,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val canvas = remember { ScreenCanvas() }
    var isStreaming  by remember { mutableStateOf(false) }
    var bitmap       by remember { mutableStateOf<ImageBitmap?>(null) }
    var packetCount  by remember { mutableStateOf(0) }
    var focused      by remember { mutableStateOf(false) }
    val scope        = rememberCoroutineScope()
    val focusReq     = remember { FocusRequester() }

    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            serialComm.setScreenPacketListener { pkt ->
                canvas.apply(pkt)
                packetCount++
            }
            serialComm.sendCommand("display start")
            try { focusReq.requestFocus() } catch (_: Exception) {}
            while (isActive) {
                if (canvas.dirty) {
                    val bmp = withContext(Dispatchers.IO) { canvas.snapshot() }
                    bitmap = bmp
                }
                delay(33)
            }
        } else {
            serialComm.sendCommand("display stop")
            serialComm.setScreenPacketListener(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isStreaming) serialComm.sendCommand("display stop")
            serialComm.setScreenPacketListener(null)
        }
    }

    Panel(
        title = "Screen mirror",
        modifier = modifier,
        accent = if (isStreaming) Online else Accent,
        trailing = {
            Text(
                if (isStreaming) "$packetCount PKTS" else "IDLE",
                color = if (isStreaming) Online else Dim,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
    ) {

        // ── Permission hint when not connected ───────────────────────────────
        if (!isConnected) {
            Column(
                Modifier.fillMaxWidth()
                    .background(PanelHi, BoxShape)
                    .border(1.dp, Hair, BoxShape)
                    .padding(10.dp)
            ) {
                Text("ATTACH A SERIAL PORT FIRST", color = Hot, fontSize = 16.sp, letterSpacing = 1.5.sp)
                Text(
                    "Linux: /dev/ttyACM0 or /dev/ttyUSB0\npermission denied?  sudo usermod -a -G dialout \$USER",
                    color = Dim, fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Start / Stop + D-pad ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BruceButton(
                text = if (isStreaming) "Stop mirror" else "Start mirror",
                modifier = Modifier.weight(1f),
                enabled = isConnected,
                primary = true,
                color = if (isStreaming) Online else Accent,
                height = 40.dp
            ) { isStreaming = !isStreaming }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                NavBtn("▲", isConnected) { scope.launch(Dispatchers.IO) { serialComm.sendCommand("nav up") } }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    NavBtn("◄", isConnected) { scope.launch(Dispatchers.IO) { serialComm.sendCommand("nav prev") } }
                    NavBtn("OK", isConnected, accent = true) { scope.launch(Dispatchers.IO) { serialComm.sendCommand("nav select") } }
                    NavBtn("►", isConnected) { scope.launch(Dispatchers.IO) { serialComm.sendCommand("nav next") } }
                }
                NavBtn("▼", isConnected) { scope.launch(Dispatchers.IO) { serialComm.sendCommand("nav down") } }
            }
            NavBtn("ESC", isConnected, wide = true) { scope.launch(Dispatchers.IO) { serialComm.sendCommand("nav esc") } }
        }

        Spacer(Modifier.height(10.dp))

        // ── Live screen canvas ───────────────────────────────────────────────
        val bmp = bitmap

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF06060A), BoxShape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = when {
                        focused      -> Hot
                        isStreaming   -> Accent
                        else         -> Hair
                    },
                    shape = BoxShape
                )
                .onKeyEvent { ev ->
                    if (!isConnected || ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    scope.launch(Dispatchers.IO) {
                        when (ev.key) {
                            Key.DirectionUp             -> serialComm.sendCommand("nav up")
                            Key.DirectionDown           -> serialComm.sendCommand("nav down")
                            Key.DirectionRight          -> serialComm.sendCommand("nav next")
                            Key.DirectionLeft           -> serialComm.sendCommand("nav prev")
                            Key.Enter, Key.NumPadEnter  -> serialComm.sendCommand("nav select")
                            Key.Escape                  -> serialComm.sendCommand("nav esc")
                            Key.Backspace               -> serialComm.sendRaw(byteArrayOf(0x08))
                            else -> {
                                val cp = ev.utf16CodePoint
                                if (cp in 0x20..0x7E) serialComm.sendRaw(byteArrayOf(cp.toByte()))
                            }
                        }
                    }
                    true
                }
                .focusRequester(focusReq)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                // Plain tap, not clickable(): clickable consumes Enter/Space as "activate"
                // before onKeyEvent ever sees them, eating nav select.
                .pointerInput(Unit) { detectTapGestures { focusReq.requestFocus() } },
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Bruce device screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                // CRT scanlines — pure decoration, sells the "little screen" feel
                Canvas(Modifier.matchParentSize()) {
                    var y = 0f
                    while (y < size.height) {
                        drawLine(Color.Black.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), 1f)
                        y += 3f
                    }
                }
            } else {
                Text(
                    when {
                        !isConnected -> "NO DEVICE"
                        isStreaming  -> "WAITING FOR DRAW CALLS\nnavigate the device to trigger a repaint"
                        else         -> "START MIRROR TO STREAM\nthe device screen over USB serial"
                    },
                    color = Dim,
                    fontSize = 17.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Keyboard-active hint
            if (isConnected && bmp != null) {
                Text(
                    if (focused) "⌨ KEYBOARD LIVE — ARROWS NAVIGATE" else "CLICK TO GRAB KEYBOARD",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), BoxShape)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    color = if (focused) Hot else Dim,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/** Keycap-style button for the D-pad. */
@Composable
private fun NavBtn(label: String, enabled: Boolean, wide: Boolean = false, accent: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = if (wide) Modifier.width(52.dp).height(34.dp) else Modifier.size(34.dp),
        contentPadding = PaddingValues(0.dp),
        shape = BoxShape,
        border = BorderStroke(1.dp, if (enabled) (if (accent) Accent else Hair) else Hair),
        colors = ButtonDefaults.buttonColors(
            containerColor = PanelHi,
            contentColor = if (accent) Accent else White,
            disabledContainerColor = PanelBg,
            disabledContentColor = Dim.copy(alpha = 0.4f)
        )
    ) {
        Text(label, fontSize = 16.sp)
    }
}
