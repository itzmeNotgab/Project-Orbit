package app.orbit.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class Subscription internal constructor(private val onCancel: () -> Unit) { fun cancel() = onCancel() }

/** Synchronous, thread-safe event bus. A failing handler never affects other handlers. */
class EventBus(private val onError: (Throwable) -> Unit = {}) {
    private class Entry(val type: Class<*>, val handler: (Any) -> Unit)
    private val entries = CopyOnWriteArrayList<Entry>()
    private val sticky = ConcurrentHashMap<Class<*>, Any>()

    /** With replay=true the last sticky event of this type (if any) is delivered immediately. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> subscribe(type: Class<T>, replay: Boolean = false, handler: (T) -> Unit): Subscription {
        val e = Entry(type) { handler(it as T) }
        entries += e
        if (replay) sticky[type]?.let { safe(e, it) }
        return Subscription { entries.remove(e) }
    }

    fun post(event: Any) { for (e in entries) if (e.type.isInstance(event)) safe(e, event) }
    fun postSticky(event: Any) { sticky[event.javaClass] = event; post(event) }

    private fun safe(e: Entry, ev: Any) = try { e.handler(ev) } catch (t: Throwable) { onError(t) }
}

data class EngineStateChanged(val state: EngineState, val atMs: Long)

data class NetworkSample(
    val atMs: Long, val sessions: Int,
    val c2sPackets: Long, val s2cPackets: Long, val c2sBytes: Long, val s2cBytes: Long,
)
