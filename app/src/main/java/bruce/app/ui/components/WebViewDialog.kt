package bruce.app.ui.components

import android.webkit.HttpAuthHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import bruce.app.ui.theme.Black
import bruce.app.ui.theme.DarkGray
import bruce.app.ui.theme.LightGray
import bruce.app.ui.theme.PurpleAccent
import bruce.app.ui.theme.White

@Composable
fun showWebViewDialogCredentials(onShowComponentChange: (Boolean) -> Unit) {
    var showWebView by remember { mutableStateOf(false) }

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
                        text = "Bruce WebView Authentication",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Connect to Bruce WebUI WiFi before!",
                        color = White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    var username by remember { mutableStateOf("admin") }
                    var password by remember { mutableStateOf("bruce") }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username", color = White) },
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
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", color = White) },
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

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onShowComponentChange(false) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LightGray,
                                contentColor = Black
                            )
                        ) { Text("Cancel") }

                        Button(
                            onClick = {
                                onShowComponentChange(false)
                                showWebView = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurpleAccent,
                                contentColor = White
                            )
                        ) { Text("Connect") }
                    }
                }
            }
    }

    if (showWebView) {
        Dialog(
            onDismissRequest = { showWebView = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var username by remember { mutableStateOf("admin") }
            var password by remember { mutableStateOf("bruce") }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedHttpAuthRequest(
                                view: WebView?,
                                handler: HttpAuthHandler?,
                                host: String?,
                                realm: String?
                            ) {
                                if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                                    handler?.proceed(username, password)
                                } else {
                                    handler?.cancel()
                                }
                            }
                        }
                        // Note: setHttpAuthUsernamePassword is deprecated, using WebViewClient instead
                        loadUrl("http://bruce.local")
                    }
                }
            )
        }
    }
}
