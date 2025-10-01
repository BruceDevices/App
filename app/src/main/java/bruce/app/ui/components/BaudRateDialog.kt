package bruce.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import bruce.app.SerialCommunication
import bruce.app.ui.theme.DarkGray
import bruce.app.ui.theme.LightGray
import bruce.app.ui.theme.PurpleAccent
import bruce.app.ui.theme.White

@Composable
fun BaudRateDialog(serialCommunication: SerialCommunication?, onBaudRateChange: (String) -> Unit, onDismiss: (Boolean) -> Unit, initialBaudRate: String) {
    var baudRate by remember { mutableStateOf(initialBaudRate) }

    Dialog(onDismissRequest = { onDismiss(false) }) {
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
                    text = "Baud Rate Configuration",
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select baud rate for serial communication:",
                    color = White,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Common baud rates
                val baudRates = listOf("9600", "19200", "38400", "57600", "115200", "230400", "460800", "921600", "1000000")

                LazyColumn(
                    modifier = Modifier.height(200.dp)
                ) {
                    items(baudRates) { rate ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (rate == baudRate) PurpleAccent else LightGray
                            )
                        ) {
                            TextButton(
                                onClick = {
                                    baudRate = rate
                                    onBaudRateChange(baudRate)
                                    serialCommunication?.setBaudRate(rate.toInt())
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = rate,
                                    color = White,
                                    fontWeight = if (rate == baudRate) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Current: $baudRate bps",
                    color = PurpleAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onDismiss(false) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurpleAccent,
                        contentColor = White
                    )
                ) {
                    Text("Apply")
                }
            }
        }
    }
}