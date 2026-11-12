package app.orbit.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import app.orbit.client.AccountState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Small circular profile button for the top-right corner. Default avatar when signed out. */
@Composable
fun AccountButton(onClick: () -> Unit) {
    val user = AccountState.user
    Box(Modifier.size(38.dp).glass(CircleShape, user != null).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (user != null) Text(user.first().uppercase(), color = OrbitColors.Text, fontSize = 16.sp)
        else Canvas(Modifier.size(22.dp)) {
            val c = OrbitColors.Muted
            drawCircle(c, size.minDimension * 0.22f, Offset(center.x, size.height * 0.34f))
            drawArc(c, 200f, 140f, false, Offset(size.width * 0.1f, size.height * 0.58f), Size(size.width * 0.8f, size.height * 0.8f), style = Stroke(size.minDimension * 0.12f))
        }
    }
}

@Composable
fun AccountMenu(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val user = AccountState.user

    fun run(block: () -> String) {
        busy = true; msg = null
        scope.launch { msg = withContext(Dispatchers.IO) { runCatching(block).getOrElse { it.message ?: "Failed" } }; busy = false }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(OrbitColors.Surface, RoundedCornerShape(18.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (user == null) {
                Text("Guest", color = OrbitColors.Text, fontSize = 18.sp)
                Note("Signed out. An ORBIT account syncs your cosmetic loadout between devices.")
                OutlinedTextField(AccountState.server, { AccountState.server = it }, label = { Text("ORBIT server (https://…)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(name, { name = it.trim() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Sign in", enabled = !busy, active = true) { val p = pw; pw = ""; run { AccountState.signIn(name, p); "Signed in." } }
                    GlassButton("Create account", enabled = !busy) { val p = pw; pw = ""; run { AccountState.signUp(name, p); "Account created." } }
                }
                Note("Microsoft sign-in is not available yet.")
            } else {
                Text(user, color = OrbitColors.Text, fontSize = 18.sp)
                Note("ORBIT account on ${AccountState.server.ifBlank { "unknown server" }}. No profile picture support yet.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Upload loadout", enabled = !busy) { run { AccountState.push(); "Uploaded." } }
                    GlassButton("Download", enabled = !busy) { run { if (AccountState.pull()) "Downloaded." else "Nothing saved on the server yet." } }
                }
                GlassButton("Sign out") { AccountState.signOut(); msg = null }
            }
            msg?.let { Text(it, color = OrbitColors.Warn, fontSize = 13.sp) }
        }
    }
}
