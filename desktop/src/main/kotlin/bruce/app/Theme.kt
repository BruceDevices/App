package bruce.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Bruce identity: near-black canvas, hairline boxes, one hot accent ──────
internal val Ink     = Color(0xFF08080C)  // window canvas
internal val PanelBg = Color(0xFF101018)  // panel body
internal val PanelHi = Color(0xFF191926)  // panel header / inert control
internal val Hair    = Color(0xFF2E2640)  // 1px borders
internal val Accent  = Color(0xFFA855F7)  // Bruce violet
internal val Hot     = Color(0xFFE11D8F)  // shark magenta
internal val Online  = Color(0xFF22C55E)
internal val White   = Color(0xFFEDEDF2)
internal val Dim     = Color(0xFF7C7C90)

internal val Pixel = FontFamily(Font("fonts/VT323-Regular.ttf"))

internal val BoxShape = RoundedCornerShape(3.dp)

// VT323 runs small and tight — scale every Material token up and space it out.
private fun TextStyle.pixel() = copy(
    fontFamily = Pixel,
    fontSize = fontSize * 1.3f,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.6.sp
)

internal val PixelTypography = Typography().run {
    copy(
        displayLarge = displayLarge.pixel(), displayMedium = displayMedium.pixel(), displaySmall = displaySmall.pixel(),
        headlineLarge = headlineLarge.pixel(), headlineMedium = headlineMedium.pixel(), headlineSmall = headlineSmall.pixel(),
        titleLarge = titleLarge.pixel(), titleMedium = titleMedium.pixel(), titleSmall = titleSmall.pixel(),
        bodyLarge = bodyLarge.pixel(), bodyMedium = bodyMedium.pixel(), bodySmall = bodySmall.pixel(),
        labelLarge = labelLarge.pixel(), labelMedium = labelMedium.pixel(), labelSmall = labelSmall.pixel()
    )
}

/** Boxed section with a title strip — the app's basic building block. */
@Composable
internal fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = Accent,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(BoxShape)
            .background(PanelBg)
            .border(1.dp, Hair, BoxShape)
    ) {
        Row(
            Modifier.fillMaxWidth().background(PanelHi).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(4.dp, 12.dp).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), color = accent, fontSize = 16.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            trailing()
        }
        HorizontalDivider(color = Hair)
        Column(Modifier.padding(10.dp), content = content)
    }
}

@Composable
internal fun BruceButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    color: Color = Accent,
    height: Dp = 34.dp,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = BoxShape,
        contentPadding = PaddingValues(horizontal = 12.dp),
        border = if (primary) null else androidx.compose.foundation.BorderStroke(1.dp, if (enabled) color else Hair),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) color else PanelHi,
            contentColor = if (primary) Ink else color,
            disabledContainerColor = PanelHi,
            disabledContentColor = Dim
        )
    ) {
        Text(text.uppercase(), fontSize = 17.sp, letterSpacing = 1.5.sp)
    }
}

/** Shark mark + wordmark + live link readout. */
@Composable
internal fun BrandHeader(connected: Boolean, port: String, baud: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource("shark.png"), contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("BRUCE", color = White, fontSize = 30.sp, letterSpacing = 8.sp)
            Text("DEVICES · COMPANION", color = Hot, fontSize = 13.sp, letterSpacing = 3.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .clip(BoxShape)
                .background(PanelBg)
                .border(1.dp, if (connected) Online else Hair, BoxShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (connected) Online else Dim))
            Spacer(Modifier.width(8.dp))
            Text(
                if (connected) "LINK UP · ${port.substringAfterLast('/')} · $baud" else "LINK DOWN",
                color = if (connected) Online else Dim,
                fontSize = 16.sp,
                letterSpacing = 1.sp
            )
        }
    }
}
