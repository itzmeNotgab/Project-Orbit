package app.orbit.core.modules

import app.orbit.core.*

/** Only modules backed by data ORBIT genuinely has. No coordinates/FPS: those need packet decoding or game access. */
fun registerBuiltins(m: ModuleManager) {
    m.register(SessionTimer()); m.register(NetworkStats()); m.register(MemoryMonitor())
}

class SessionTimer : Module("Relay Uptime", "Time since the ORBIT relay started.", Category.HUD) {
    private val seconds = bool("Show seconds", true)
    @Volatile private var startedAt: Long? = null
    @Volatile var nowMs: () -> Long = System::currentTimeMillis

    override fun onEnable() = listen(EngineStateChanged::class.java, replay = true) {
        startedAt = if (it.state == EngineState.Running) it.atMs else null
    }

    override fun hudLines(): List<String> {
        val s = startedAt ?: return emptyList()
        val t = ((nowMs() - s) / 1000).coerceAtLeast(0)
        val h = t / 3600; val mm = (t % 3600) / 60; val ss = t % 60
        val base = if (h > 0) "%d:%02d".format(h, mm) else "%d".format(mm)
        return listOf("Relay " + if (seconds.value) "$base:%02d".format(ss) else "${base}m")
    }
}

class NetworkStats : Module("Network Stats", "Packets and throughput through the relay.", Category.PERFORMANCE) {
    private val throughput = bool("Show throughput", true)
    @Volatile private var last: NetworkSample? = null
    @Volatile private var upBps = 0.0
    @Volatile private var downBps = 0.0

    override fun onEnable() = listen(NetworkSample::class.java) { s ->
        last?.let { p ->
            val dt = (s.atMs - p.atMs) / 1000.0
            if (dt > 0) { upBps = (s.c2sBytes - p.c2sBytes) / dt; downBps = (s.s2cBytes - p.s2cBytes) / dt }
        }
        last = s
    }

    override fun hudLines(): List<String> {
        val s = last ?: return emptyList()
        val out = mutableListOf("Packets ↑${s.c2sPackets} ↓${s.s2cPackets}")
        if (throughput.value) out += String.format(java.util.Locale.ROOT, "Rate ↑%.1f ↓%.1f KB/s", upBps / 1024, downBps / 1024)
        return out
    }
}

class MemoryMonitor : Module("Memory (ORBIT)", "Heap used by ORBIT itself, not Minecraft.", Category.PERFORMANCE) {
    private val warnMb = float("Warn above MB", 200f, 50f, 1000f)
    override fun hudLines(): List<String> {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        return listOf("ORBIT heap $used MB" + if (used > warnMb.value) " (high)" else "")
    }
}
