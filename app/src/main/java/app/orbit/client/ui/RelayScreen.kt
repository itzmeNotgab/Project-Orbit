package app.orbit.client.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbit.client.RelayController
import app.orbit.client.RelayService
import app.orbit.core.EngineState
import app.orbit.network.ServerInfo
import app.orbit.network.ServerProbe

@Composable
fun RelayScreen() {
    val ctx = LocalContext.current
    var host by rememberSaveable { mutableStateOf("") }
    var portText by rememberSaveable { mutableStateOf("19132") }
    var probe by remember { mutableStateOf<String?>(null) }
    val engine by RelayController.core.engine.collectAsState()
    val stats by RelayController.stats.collectAsState()
    val error by RelayController.error.collectAsState()
    val port = portText.toIntOrNull()
    val valid = host.isNotBlank() && port != null && port in 1..65535
    val running = engine == EngineState.Running || engine == EngineState.Starting

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Relay", color = OrbitColors.Text, fontSize = 24.sp)
        OutlinedTextField(host, { host = it.trim() }, label = { Text("Server address") }, singleLine = true,
            enabled = !running, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(portText, { v: String -> portText = v.filter { c -> c.isDigit() }.take(5) }, label = { Text("Port") },
            singleLine = true, enabled = !running, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = valid && !running, onClick = {
                probe = "pinging…"
                Thread {
                    probe = ServerProbe.ping(host, port!!).fold(
                        { i: ServerInfo -> "${i.motd}\nprotocol ${i.protocol ?: "?"}, version ${i.version}, players ${i.online}/${i.max}" },
                        { "failed: ${it.message}" })
                }.start()
            }) { Text("Ping server") }
            if (!running) Button(enabled = valid, onClick = { RelayService.start(ctx, host, port!!) }) { Text("Start relay") }
            else Button(onClick = { RelayService.stop(ctx) }) { Text("Stop relay") }
        }

        probe?.let { Text(it, color = OrbitColors.Text, fontSize = 13.sp) }
        error?.let { Text("Relay error: $it", color = OrbitColors.Warn, fontSize = 13.sp) }

        if (engine == EngineState.Running) {
            Text("In Minecraft, add a server at 127.0.0.1 port ${RelayController.LISTEN_PORT}.", color = OrbitColors.Accent, fontSize = 14.sp)
            Text("Sessions ${stats.sessions}   dropped ${stats.dropped}\n" +
                "→ server ${stats.clientToServerPackets} pkts / ${stats.clientToServerBytes} B\n" +
                "← client ${stats.serverToClientPackets} pkts / ${stats.serverToClientBytes} B",
                color = OrbitColors.Text, fontSize = 13.sp)
        }
    }
}
