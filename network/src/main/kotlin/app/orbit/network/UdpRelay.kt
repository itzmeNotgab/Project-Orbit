package app.orbit.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class RelayConfig(
    val targetHost: String,
    val targetPort: Int = 19132,
    val listenPort: Int = 19133,
    val bindAddress: String = "127.0.0.1",
    val idleTimeoutMs: Long = 30_000,
    val maxSessions: Int = 8,
)

data class RelayStats(
    val sessions: Int = 0,
    val clientToServerPackets: Long = 0, val serverToClientPackets: Long = 0,
    val clientToServerBytes: Long = 0, val serverToClientBytes: Long = 0,
    val dropped: Long = 0,
)

/**
 * Transport-level relay: one upstream UDP socket per local client, datagrams forwarded unchanged in
 * both directions (ordering and RakNet state are preserved because nothing is rewritten).
 * It does NOT decode RakNet or Bedrock packets. That begins in Phase 3.
 */
class UdpRelay(private val cfg: RelayConfig, private val log: (String) -> Unit = {}) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private var listen: DatagramSocket? = null
    private lateinit var target: InetSocketAddress
    private val sessions = ConcurrentHashMap<SocketAddress, Session>()
    private val c2sP = AtomicLong(); private val s2cP = AtomicLong()
    private val c2sB = AtomicLong(); private val s2cB = AtomicLong()
    private val dropped = AtomicLong()

    val localPort: Int get() = listen?.localPort ?: -1

    private inner class Session(val client: SocketAddress, val upstream: DatagramSocket) {
        @Volatile var lastActive = System.currentTimeMillis()
        fun close() = upstream.close()
    }

    /** Blocking: resolves the target and binds. Do not call on the Android main thread. */
    fun start() {
        check(running.compareAndSet(false, true)) { "already started" }
        try {
            target = InetSocketAddress(InetAddress.getByName(cfg.targetHost), cfg.targetPort)
            val s = DatagramSocket(InetSocketAddress(InetAddress.getByName(cfg.bindAddress), cfg.listenPort))
            s.soTimeout = minOf(1000L, cfg.idleTimeoutMs).toInt()
            listen = s
            Thread({ listenLoop(s) }, "orbit-relay-listen").apply { isDaemon = true }.start()
            log("relay ${cfg.bindAddress}:${s.localPort} -> $target")
        } catch (e: Exception) {
            running.set(false); listen?.close(); throw e
        }
    }

    private fun listenLoop(sock: DatagramSocket) {
        val buf = ByteArray(2048)
        val pkt = DatagramPacket(buf, buf.size)
        while (running.get()) {
            pkt.length = buf.size
            try { sock.receive(pkt) }
            catch (e: SocketTimeoutException) { sweep(); continue }
            catch (e: SocketException) { break }
            val client = pkt.socketAddress
            val s = sessions[client] ?: newSession(sock, client)
            if (s == null) { dropped.incrementAndGet(); continue }
            s.lastActive = System.currentTimeMillis()
            try {
                s.upstream.send(DatagramPacket(buf, 0, pkt.length))
                c2sP.incrementAndGet(); c2sB.addAndGet(pkt.length.toLong())
            } catch (e: Exception) { dropped.incrementAndGet() }
        }
    }

    private fun newSession(sock: DatagramSocket, client: SocketAddress): Session? {
        if (sessions.size >= cfg.maxSessions) { log("session limit reached, dropping $client"); return null }
        return try {
            val up = DatagramSocket().apply { connect(target); soTimeout = 1000 }
            val s = Session(client, up)
            sessions[client] = s
            Thread({ upstreamLoop(sock, s) }, "orbit-relay-up").apply { isDaemon = true }.start()
            log("session opened for $client")
            s
        } catch (e: Exception) { log("session failed: ${e.message}"); null }
    }

    private fun upstreamLoop(sock: DatagramSocket, s: Session) {
        val buf = ByteArray(2048)
        val p = DatagramPacket(buf, buf.size)
        while (running.get() && !s.upstream.isClosed) {
            p.length = buf.size
            try { s.upstream.receive(p) }
            catch (e: SocketTimeoutException) { continue }
            catch (e: Exception) { break }
            try {
                sock.send(DatagramPacket(buf, 0, p.length, s.client))
                s2cP.incrementAndGet(); s2cB.addAndGet(p.length.toLong())
                s.lastActive = System.currentTimeMillis()
            } catch (e: Exception) { dropped.incrementAndGet() }
        }
    }

    private fun sweep() {
        val now = System.currentTimeMillis()
        for ((k, s) in sessions) if (now - s.lastActive > cfg.idleTimeoutMs) {
            if (sessions.remove(k, s)) { s.close(); log("session idle-closed: $k") }
        }
    }

    fun snapshot() = RelayStats(sessions.size, c2sP.get(), s2cP.get(), c2sB.get(), s2cB.get(), dropped.get())

    override fun close() {
        if (!running.getAndSet(false)) return
        listen?.close()
        sessions.values.forEach { it.close() }
        sessions.clear()
    }
}
