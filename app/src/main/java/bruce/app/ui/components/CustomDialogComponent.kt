package bruce.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import bruce.app.CustomCommandsDatabaseHelper
import bruce.app.CustomSerialCommand
import bruce.app.ui.theme.Black
import bruce.app.ui.theme.DarkGray
import bruce.app.ui.theme.LightGray
import bruce.app.ui.theme.PurpleAccent
import bruce.app.ui.theme.White

@Composable
fun CustomDialogComponent(dbHelper: CustomCommandsDatabaseHelper?, onDismiss: (Boolean) -> Unit, onNewCustomCommand: (CustomSerialCommand) -> Unit) {
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
                    text = "Add Custom Command",
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                var commandName by remember { mutableStateOf("") }
                var commandText by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = commandName,
                    onValueChange = { commandName = it },
                    label = { Text("Command Name", color = White) },
                    placeholder = { Text("e.g., LED On", color = White.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = PurpleAccent,
                        unfocusedBorderColor = LightGray,
                        focusedLabelColor = PurpleAccent,
                        unfocusedLabelColor = White
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    label = { Text("Serial Command", color = White) },
                    placeholder = { Text("e.g., led r 255", color = White.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = PurpleAccent,
                        unfocusedBorderColor = LightGray,
                        focusedLabelColor = PurpleAccent,
                        unfocusedLabelColor = White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Examples:",
                    color = PurpleAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Name: 'LED Red' → Command: 'led r 255'",
                    color = White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
                Text(
                    text = "• Name: 'Say Hello' → Command: 'say Hello World'",
                    color = White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
                Text(
                    text = "• Name: 'IR Send' → Command: 'ir tx NEC 04000000'",
                    color = White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onDismiss(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LightGray,
                            contentColor = Black
                        )
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (commandName.isNotEmpty() && commandText.isNotEmpty()) {
                                val newCommand = CustomSerialCommand(
                                    id = System.currentTimeMillis().toString(),
                                    name = commandName,
                                    command = commandText
                                )
                                dbHelper?.insertCommand(newCommand)
                                onNewCustomCommand(newCommand)
                                onDismiss(false)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleAccent,
                            contentColor = White
                        )
                    ) {
                        Text("Add Command")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteCommandComponent(dbHelper: CustomCommandsDatabaseHelper?, cmd: CustomSerialCommand, onDelete: (CustomSerialCommand) -> Unit){
    IconButton(
        onClick = {
            dbHelper?.deleteCommand(cmd.id)
            onDelete(cmd)
        },
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = "Delete",
            tint = White,
            modifier = Modifier.size(18.dp)
        )
    }
}