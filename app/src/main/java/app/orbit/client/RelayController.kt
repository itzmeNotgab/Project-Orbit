package app.orbit.client

import android.content.Context
import app.orbit.core.ClientCore
import app.orbit.core.FileConfigStore
import app.orbit.network.RelayStats
import app.orbit.network.UdpRelay
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Process-wide holder shared by the UI and RelayService. */
object RelayController {
    lateinit var core: ClientCore
        private set
    @Volatile var relay: UdpRelay? = null
    val stats = MutableStateFlow(RelayStats())
    val error = MutableStateFlow<String?>(null)
    const val LISTEN_PORT = 19133

    fun init(ctx: Context) {
        if (!::core.isInitialized) core = ClientCore(FileConfigStore(File(ctx.filesDir, "orbit-config.properties")))
    }
}
