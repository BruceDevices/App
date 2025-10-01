package bruce.app.ui.components

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import bruce.app.ui.theme.PurpleAccent
import bruce.app.ui.theme.White

@Composable
fun BruceIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .background(PurpleAccent, RoundedCornerShape(8.dp))
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = White)
    }
}
