package bruce.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bruce.app.CustomSerialCommand
import bruce.app.ui.theme.PurpleAccent
import bruce.app.ui.theme.White

@Composable
fun CommandChip(cmd: CustomSerialCommand) {
    Text(
        text = cmd.name,
        color = PurpleAccent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = cmd.command,
        color = White,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
fun EmptyCommand() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No custom commands yet",
                color = White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap '+ Add' to create your first command",
                color = White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}