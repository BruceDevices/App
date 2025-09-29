import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp

private lateinit var textMeasurer: TextMeasurer
private lateinit var density: Density

fun pixelToDp(x: Int): Dp {
    val heightDp = with(density) { (x * 8).toDp() }   // Float obtained
    return heightDp
}

fun parseInt16(payload: List<Int>, index: Int): Dp {
    return pixelToDp((payload[index] shl 8 or payload[index + 1]))
}

fun parseInt16Int(payload: List<Int>, index: Int): Int {
    return (payload[index] shl 8 or payload[index + 1])
}

data class ScreenInfo(
    val width: Int,
    val height: Int,
    val rotation: Int
) {
    fun invalidScreen(): Boolean {
        return width == 0 && height == 0
    }
}

var screenInfo = ScreenInfo(0, 0, 0)

fun parseScreenInfo(args: List<Int>): ScreenInfo {
    val width = parseInt16Int(args, 0)
    val height = parseInt16Int(args, 2)

    val rotation = args[4]

    return ScreenInfo(width, height, rotation)
}

fun parseScreenInfo(width: Int, height: Int, rotation: Int): ScreenInfo {
    screenInfo = ScreenInfo(width, height, rotation)
    return ScreenInfo(width, height, rotation)
}

private fun parseFill(fill: Boolean): DrawStyle {
    return if(fill) Fill else Stroke(width = 4f)
}

private fun DrawScope.parseRoundRect(args: List<Int>, fill: Boolean) {
    val x = parseInt16(args, 0)
    val y = parseInt16(args, 2)
    val w = parseInt16(args, 4)
    val h = parseInt16(args, 6)
    val r = parseInt16(args, 8)
    val fg = parseInt16Int(args, 10)

    drawRoundRect(
        color = bruceColorToColor(fg),
        topLeft = Offset(x.value, y.value),
        size = Size(w.value, h.value),
        style = if(fill) Fill else Stroke(width = 4f),
        cornerRadius = CornerRadius(r.value, r.value)
    )
}

private fun DrawScope.parseRect(args: List<Int>, fill: Boolean) {
    val x = parseInt16(args, 0)
    val y = parseInt16(args, 2)
    val w = parseInt16(args, 4)
    val h = parseInt16(args, 6) + 10.dp // Add some padding to the rectangle to match the original screen
    val fg = parseInt16Int(args, 8)

    drawRect(
        color = bruceColorToColor(fg),
        topLeft = Offset(x.value, y.value),
        size = Size(w.value, h.value),
        style = if(fill) Fill else Stroke(width = 4f)
    )
}

private fun DrawScope.parseLine(args: List<Int>) {
    val x = parseInt16(args, 0).value
    val y = parseInt16(args, 2).value
    val x1 = parseInt16(args, 4).value
    val y1 = parseInt16(args, 6).value
    val fg = parseInt16Int(args, 8)

    drawLine(bruceColorToColor(fg), Offset(x, y), Offset(x1, y1))
}

private fun DrawScope.parseWideLine(args: List<Int>) {
    val x = parseInt16(args, 0)
    val y = parseInt16(args, 2)
    val bx = parseInt16(args, 4)
    val by = parseInt16(args, 6)
    val wd = parseInt16(args, 8)
    val fg = parseInt16Int(args, 10)
    val bg = parseInt16(args, 12)

    drawLine(
        bruceColorToColor(fg),
        Offset(x.value, y.value),
        Offset(bx.value, by.value),
        strokeWidth = wd.value
    )
}

private fun DrawScope.parseDrawArc(args: List<Int>) {
    val x = parseInt16(args, 0) - 40.dp
    val y = parseInt16(args, 2) - 40.dp
    val r = parseInt16(args, 4)
    val ir = parseInt16(args, 6)
    val startAngle = parseInt16Int(args, 8)
    val endAngle = parseInt16Int(args, 10)
    val fg = parseInt16Int(args, 12)
    val bg = parseInt16Int(args, 14)

    val strokeWidth = (r - ir).value
    val radius = ((r + ir) / 2).value
    drawArc(
        bruceColorToColor(fg),
        startAngle.toFloat(),
        endAngle.toFloat() - startAngle.toFloat(),
        false,
        topLeft = Offset(x.value, y.value),
        style = Stroke(width = if(strokeWidth.toInt() == 0) 1f else strokeWidth),
        size = Size(radius, radius)
    )
}

private fun DrawScope.parseCircle(args: List<Int>, fill: Boolean) {
    val x = parseInt16(args, 0)
    val y = parseInt16(args, 2)
    val r = parseInt16(args, 4)
    val fg = parseInt16Int(args, 6)

    drawCircle(
        color = bruceColorToColor(fg),
        radius = r.value,
        center = Offset(x.value, y.value),
        style = parseFill(fill)
    )
}

private fun bruceColorToColor(color: Int): Color {
    val r = ((color shr 11) and 0x1F) * 255 / 31;
    val g = ((color shr 5) and 0x3F) * 255 / 63;
    val b = (color and 0x1F) * 255 / 31;

    return Color(r, g, b)
}

private fun DrawScope.print(args: List<Int>, alignment: TextAlign) {
    val x = parseInt16(args, 0)
    val y = parseInt16(args, 2)
    val textSize = parseInt16(args, 4)
    val fg = parseInt16Int(args, 6)
    val bg = parseInt16Int(args, 8)
    val txt = args.subList(8, args.size).map { it.toChar() }.joinToString("").replace("\\n", "")

    val textStyle = TextStyle(
        color = bruceColorToColor(fg),
        fontSize = TextUnit((textSize.value - 1), TextUnitType.Em),
        fontWeight = FontWeight.Medium,
        textAlign = alignment,
        //background = bruceColorToColor(bg)    // It broke the text rendering sometimes
    )

    // Measure the text
    val measure = textMeasurer.measure(
        text = txt,
        style = textStyle
    )
    val centerX = (size.width - measure.size.width) / 2
    drawText(
        textMeasurer = textMeasurer,
        text = txt.slice(2 until txt.length), // Remove the first char, it's always a junk char
        topLeft = Offset(
            if (alignment == TextAlign.Center) centerX else x.value,
            if (alignment == TextAlign.Center) y.value - 35 else y.value - 10
        ),  // Add some padding to scale the text correctly
        style = textStyle
    )
}

private fun DrawScope.parseFillScreen(args: List<Int>) {
    val fg = parseInt16Int(args, 0)
    drawRect(
        bruceColorToColor(fg),
        size = Size(pixelToDp(screenInfo.width * 3).value, pixelToDp(screenInfo.height * 3).value)
    )
}

private fun DrawScope.parseEllipse(args: List<Int>, fill: Boolean) {
    val x = parseInt16(args, 0)
    val y = parseInt16(args, 2)
    val rx = parseInt16(args, 4)
    val ry = parseInt16(args, 6)
    val fg = parseInt16Int(args, 8)

    drawOval(
        color = bruceColorToColor(fg),
        topLeft = Offset(x.value, y.value),
        size = Size(rx.value, ry.value),
        style = parseFill(fill)
    )
}

private fun DrawScope.parseFastVLine(args: List<Int>) {
    val x = parseInt16(args, 0)
    val y = parseInt16(args, 2)
    val h = parseInt16(args, 4)
    val fg = parseInt16Int(args, 6)

    drawLine(
        bruceColorToColor(fg),
        Offset(x.value, y.value),
        Offset(x.value, (y + h).value)
    )
}

private fun DrawScope.parseFastHLine(args: List<Int>) {
    val x = parseInt16(args, 0)
    val y = parseInt16(args, 2)
    val w = parseInt16(args, 4)
    val fg = parseInt16Int(args, 6)

    drawLine(
        bruceColorToColor(fg),
        Offset(x.value, y.value),
        Offset((x + w).value, y.value)
    )
}

private fun DrawScope.parseTriangle(args: List<Int>, fill: Boolean) {
    val x = parseInt16(args, 0)
    val y = parseInt16(args, 2)
    val x2 = parseInt16(args, 4)
    val y2 = parseInt16(args, 6)
    val x3 = parseInt16(args, 8)
    val y3 = parseInt16(args, 10)
    val fg = parseInt16Int(args, 12)

    drawLine(
        bruceColorToColor(fg),
        Offset(x.value, y.value),
        Offset(x2.value, y2.value)
    )

    drawLine(
        bruceColorToColor(fg),
        Offset(x.value, y.value),
        Offset(x3.value, y3.value)
    )
}

private fun DrawScope.parseImage(args: List<Int>) {
    val x = parseInt16(args, 0)
    val y = parseInt16(args, 2)
    val center = parseInt16(args, 4)
    val ms = parseInt16(args, 6)
    // fs = args[7]
    val file = args.subList(8, args.size)
    // TODO: Read from BLE
}

fun DrawScope.parseFunction(func: Int, args: List<Int>) {
    when (func) {
        0 -> parseFillScreen(args)
        1 -> parseRect(args, false)
        2 -> parseRect(args, true)
        3 -> parseRoundRect(args, false)
        4 -> parseRoundRect(args, true)
        5 -> parseCircle(args, false)
        6 -> parseCircle(args, true)
        7 -> parseTriangle(args, false)
        8 -> parseTriangle(args, true)
        9 -> parseEllipse(args, false)
        10 -> parseEllipse(args, true)
        11 -> parseLine(args)
        12 -> parseDrawArc(args)
        13 -> parseWideLine(args)
        14 -> print(args, TextAlign.Center)
        15 -> print(args, TextAlign.Right)
        16 -> print(args, TextAlign.Unspecified)
        17 -> print(args, TextAlign.Unspecified)
        18 -> parseImage(args)  // Not implemented
        20 -> parseFastVLine(args)
        21 -> parseFastHLine(args)
        99 -> parseScreenInfo(args)
    }
}


fun setTextMeasurer(_textMeasurer: TextMeasurer) {
    textMeasurer = _textMeasurer
}

fun setDensity(_density: Density) {
    density = _density
}