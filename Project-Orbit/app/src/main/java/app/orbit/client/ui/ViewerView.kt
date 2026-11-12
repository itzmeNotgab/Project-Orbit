package app.orbit.client.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.orbit.library.ViewerState
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val TAG = "OrbitViewer"

/** What the 3D viewer is doing right now. Shown in Settings > 3D viewer so a device test can be reported precisely. */
object ViewerDiagnostics {
    private val _webView = mutableStateOf("unknown")
    private val _page = mutableStateOf(false)
    private val _bridge = mutableStateOf(false)
    private val _drawn = mutableStateOf<String?>(null)
    private val _error = mutableStateOf<String?>(null)
    private val _sends = mutableIntStateOf(0)
    var webViewVersion: String get() = _webView.value; set(v) { _webView.value = v }
    var pageLoaded: Boolean get() = _page.value; set(v) { _page.value = v }
    var bridgeReady: Boolean get() = _bridge.value; set(v) { _bridge.value = v }
    /** e.g. "360x420 @2.0" once the renderer reports its first drawn frame. */
    var firstDraw: String? get() = _drawn.value; set(v) { _drawn.value = v }
    var lastError: String? get() = _error.value; set(v) { _error.value = v }
    var stateSends: Int get() = _sends.intValue; set(v) { _sends.intValue = v }
}

/**
 * Receives JSON messages from the page. Must be a PUBLIC class: WebView calls it through reflection and a
 * private/package-private class can make the call fail, which the page used to swallow silently.
 */
class ViewerBridge(private val onMessage: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface
    fun post(msg: String) { main.post { onMessage(msg) } }
}

/** Plain holder so state can be pushed from WebViewClient callbacks without depending on recomposition timing. */
private class ViewerHost {
    var js: String = ""
    var pageFinished = false
    var webView: WebView? = null
    fun push() {
        val w = webView ?: return
        if (!pageFinished) return
        ViewerDiagnostics.stateSends++
        w.evaluateJavascript(js, null)
    }
}

/**
 * The original ORBIT 3D viewer (software-rendered canvas in a WebView, no network, no file access).
 * State reaches the page on page load, on the page's "ready" message, and on every state change; the page applies
 * states idempotently, so any of those paths alone is enough and none can cause duplicate initialization.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ViewerView(state: ViewerState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val html = remember { ctx.assets.open("viewer.html").bufferedReader().use { it.readText() } }
    val js = remember(state) { "window.orbitSet(" + state.toJson().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029") + ");true;" }
    val host = remember { ViewerHost() }
    host.js = js
    var attempt by remember { mutableIntStateOf(0) }
    var drawn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var timedOut by remember { mutableStateOf(false) }

    key(attempt) {
        LaunchedEffect(Unit) {
            ViewerDiagnostics.webViewVersion = runCatching { WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } }.getOrNull() ?: "unknown"
            drawn = false; timedOut = false
            delay(6000)
            if (!drawn) {
                timedOut = true
                if (error == null) error = "The 3D renderer did not draw within 6 s " +
                    "(page loaded: ${ViewerDiagnostics.pageLoaded}, bridge ready: ${ViewerDiagnostics.bridgeReady})."
            }
        }
        Box(modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    ViewerDiagnostics.pageLoaded = false; ViewerDiagnostics.bridgeReady = false; ViewerDiagnostics.firstDraw = null
                    WebView(c).apply {
                        host.webView = this; host.pageFinished = false
                        setBackgroundColor(0)
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false; settings.allowContentAccess = false
                        settings.domStorageEnabled = false; settings.setSupportZoom(false)
                        addJavascriptInterface(ViewerBridge { msg ->
                            val o = runCatching { JSONObject(msg) }.getOrNull() ?: return@ViewerBridge
                            when (o.optString("type")) {
                                "ready" -> { ViewerDiagnostics.bridgeReady = true; host.push() }
                                "drawn" -> {
                                    ViewerDiagnostics.firstDraw = "${o.optInt("width")}x${o.optInt("height")} @${o.optDouble("dpr")}"
                                    drawn = true; error = null; timedOut = false
                                }
                                "error" -> { val m = o.optString("message"); Log.e(TAG, "js error: $m"); ViewerDiagnostics.lastError = m; if (!drawn) error = m }
                            }
                        }, "OrbitBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                ViewerDiagnostics.pageLoaded = true; host.pageFinished = true; host.push()
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, err: WebResourceError?) {
                                if (request?.isForMainFrame == true) { val m = "WebView load error ${err?.errorCode}: ${err?.description}"; Log.e(TAG, m); ViewerDiagnostics.lastError = m; error = m }
                            }
                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                // Returning true keeps the app alive when the WebView's renderer process dies (e.g. low memory).
                                val m = "WebView renderer process ended (crashed: ${detail?.didCrash()})"; Log.e(TAG, m)
                                ViewerDiagnostics.lastError = m; error = m; drawn = false; return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                                if (m == null) return true
                                val line = "${m.message()} (${m.sourceId()}:${m.lineNumber()})"
                                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) { Log.e(TAG, "console: $line"); ViewerDiagnostics.lastError = m.message() }
                                else Log.d(TAG, "console: $line")
                                return true
                            }
                        }
                        loadDataWithBaseURL("https://orbit.invalid/", html, "text/html", "utf-8", null)
                    }
                },
                update = { host.js = js; host.webView = it; host.push() },
                onRelease = { v ->
                    host.webView = null; host.pageFinished = false
                    runCatching { v.stopLoading(); v.removeJavascriptInterface("OrbitBridge"); v.destroy() }
                },
            )
            if (!drawn) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (error == null) Text("Loading 3D preview…", color = OrbitColors.Muted, fontSize = 13.sp)
                else {
                    Text("3D preview problem", color = OrbitColors.Warn, fontSize = 14.sp)
                    Text(error ?: "", color = OrbitColors.Muted, fontSize = 12.sp)
                    if (timedOut) Btn("Retry") { error = null; attempt++ }
                }
            }
        }
    }
}
