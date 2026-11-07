package app.orbit.client.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.orbit.library.ViewerState

private class Bridge(val onReady: () -> Unit) {
    @JavascriptInterface fun post(msg: String) { if (msg.contains("\"ready\"")) Handler(Looper.getMainLooper()).post(onReady) }
}

/** The original ORBIT 3D viewer (software-rendered canvas in a WebView, no network, no file access). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ViewerView(state: ViewerState, height: Dp = 300.dp) {
    val ctx = LocalContext.current
    val html = remember { ctx.assets.open("viewer.html").bufferedReader().use { it.readText() } }
    var ready by remember { mutableStateOf(false) }
    val js = remember(state) { "window.orbitSet(" + state.toJson().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029") + ");true;" }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(height),
        factory = { c ->
            WebView(c).apply {
                setBackgroundColor(0)
                settings.javaScriptEnabled = true; settings.allowFileAccess = false
                settings.allowContentAccess = false; settings.domStorageEnabled = false
                addJavascriptInterface(Bridge { ready = true }, "OrbitBridge")
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        update = { if (ready) it.evaluateJavascript(js, null) },
        onRelease = { it.destroy() },
    )
}
