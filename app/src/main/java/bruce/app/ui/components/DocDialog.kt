import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import bruce.app.ui.theme.DarkGray
import bruce.app.ui.theme.PurpleAccent
import bruce.app.ui.theme.PurpleGrey80
import bruce.app.ui.theme.White
import androidx.core.net.toUri

@Composable
fun DocDialog(onShowComponentChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    
    Dialog(onDismissRequest = { onShowComponentChange(false) }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkGray)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Documentation",
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, "https://bruce.computer".toUri())
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleGrey80,
                            contentColor = White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open https://bruce.computer") }

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pr3y/Bruce/wiki/Serial"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleGrey80,
                            contentColor = White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open Serial Wiki") }

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pr3y/BruceApp"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleGrey80,
                            contentColor = White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("BruceApp repo") }

                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onShowComponentChange(false) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurpleAccent,
                        contentColor = White
                    )
                ) {
                    Text("Close")
                }
            }
        }
    }
}