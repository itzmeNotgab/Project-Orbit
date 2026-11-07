package app.orbit.client.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbit.client.AppStore
import app.orbit.client.SecureStore
import app.orbit.library.AccountApi
import app.orbit.library.Loadout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AccountScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenStore = remember { SecureStore(ctx, "account_token") }
    val prefs = remember { File(ctx.filesDir, "server.txt") }
    var server by rememberSaveable { mutableStateOf(runCatching { prefs.readText() }.getOrDefault("")) }
    var user by remember { mutableStateOf(runCatching { File(ctx.filesDir, "username.txt").readText() }.getOrNull()) }
    var token by remember { mutableStateOf(tokenStore.get()) }
    var name by rememberSaveable { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun run(block: () -> String) { busy = true; msg = null
        scope.launch { msg = withContext(Dispatchers.IO) { runCatching(block).getOrElse { it.message ?: "Failed" } }; busy = false } }

    fun saveServer() = runCatching { prefs.writeText(server.trim()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account", color = OrbitColors.Text, fontSize = 24.sp)
        Section("ORBIT server") {
            OutlinedTextField(server, { server = it }, label = { Text("https://…") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Text("Accounts need an ORBIT server (backend/ folder). This is not a Microsoft account.", color = OrbitColors.Muted, fontSize = 12.sp)
        }
        if (token == null) Section("Sign in") {
            OutlinedTextField(name, { name = it.trim() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pw, { pw = it }, label = { Text("Password (8+ characters)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Btn("Sign in", !busy) { saveServer(); val p = pw; run {
                    val r = AccountApi(server).signIn(name, p); tokenStore.put(r.token); token = r.token
                    File(ctx.filesDir, "username.txt").writeText(r.user.username); user = r.user.username; "Signed in." } ; pw = "" }
                Btn("Create account", !busy) { saveServer(); val p = pw; run {
                    val r = AccountApi(server).signUp(name, p); tokenStore.put(r.token); token = r.token
                    File(ctx.filesDir, "username.txt").writeText(r.user.username); user = r.user.username; "Account created." } ; pw = "" }
            }
        } else Section("Signed in as ${user ?: "?"}") {
            Text("Syncs your cosmetics, cape (including a custom cape) and pet choice.", color = OrbitColors.Muted, fontSize = 12.sp)
            Btn("Upload loadout", !busy) { val t = token!!; run {
                val v = AppStore.viewer.value
                AccountApi(server).pushLoadout(t, Loadout(v.cosmetics, v.capeId, v.capeTexDataUri, v.petId)); "Uploaded." } }
            Btn("Download loadout", !busy) { val t = token!!; run {
                val l = AccountApi(server).pullLoadout(t)
                if (l == null) "Nothing saved on the server yet." else {
                    AppStore.update { it.copy(cosmetics = l.cosmetics, capeId = l.capeId, petId = l.petId) }
                    l.capeData?.let { d -> runCatching { AppStore.setCustomCape(android.util.Base64.decode(d.substringAfter("base64,"), 0)) } }
                    "Downloaded." } } }
            Btn("Sign out") { tokenStore.put(null); token = null; File(ctx.filesDir, "username.txt").delete(); user = null }
        }
        msg?.let { Text(it, color = OrbitColors.Warn, fontSize = 13.sp) }
    }
}
