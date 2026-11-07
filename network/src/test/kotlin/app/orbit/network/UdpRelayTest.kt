package app.orbit.network

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.net.*

class UdpRelayTest {
    private val closers = mutableListOf<AutoCloseable>()
    @After fun tearDown() = closers.forEach { runCatching { it.close() } }

    /** Server that replies to every datagram with "echo:" + payload. */
    private fun echoServer(): DatagramSocket {
        val s = DatagramSocket(0, InetAddress.getByName("127.0.0.1")); closers += s
        Thread {
            val b = ByteArray(2048); val p = DatagramPacket(b, b.size)
            while (!s.isClosed) {
                try { p.length = b.size; s.receive(p) } catch (e: Exception) { break }
                val r = "echo:".toByteArray() + b.copyOf(p.length)
                s.send(DatagramPacket(r, r.size, p.socketAddress))
            }
        }.apply { isDaemon = true }.start()
        return s
    }

    private fun relay(target: Int, idle: Long = 30_000, max: Int = 8): UdpRelay {
        val r = UdpRelay(RelayConfig("127.0.0.1", target, listenPort = 0, idleTimeoutMs = idle, maxSessions = max))
        r.start(); closers += r; return r
    }

    private fun roundTrip(c: DatagramSocket, port: Int, msg: String): String {
        val out = msg.toByteArray()
        c.send(DatagramPacket(out, out.size, InetAddress.getByName("127.0.0.1"), port))
        val b = ByteArray(2048); val p = DatagramPacket(b, b.size)
        c.receive(p); return String(b, 0, p.length)
    }

    private fun client() = DatagramSocket().apply { soTimeout = 3000; closers += this }

    @Test fun forwardsBothWaysAndCounts() {
        val r = relay(echoServer().localPort)
        assertEquals("echo:hello", roundTrip(client(), r.localPort, "hello"))
        val s = r.snapshot()
        assertEquals(1, s.sessions); assertEquals(1, s.clientToServerPackets); assertEquals(1, s.serverToClientPackets)
        assertEquals(5L, s.clientToServerBytes)
    }

    @Test fun preservesOrder() {
        val r = relay(echoServer().localPort)
        val c = client()
        val got = (1..20).map { roundTrip(c, r.localPort, "m$it") }
        assertEquals((1..20).map { "echo:m$it" }, got)
    }

    @Test fun separateClientsGetSeparateSessions() {
        val r = relay(echoServer().localPort)
        roundTrip(client(), r.localPort, "a"); roundTrip(client(), r.localPort, "b")
        assertEquals(2, r.snapshot().sessions)
    }

    @Test fun enforcesSessionLimit() {
        val r = relay(echoServer().localPort, max = 1)
        roundTrip(client(), r.localPort, "a")
        val c2 = client().apply { soTimeout = 500 }
        c2.send(DatagramPacket(byteArrayOf(1), 1, InetAddress.getByName("127.0.0.1"), r.localPort))
        try { c2.receive(DatagramPacket(ByteArray(10), 10)); fail("should not be answered") } catch (_: SocketTimeoutException) {}
        assertEquals(1L, r.snapshot().dropped)
    }

    @Test fun idleSessionsAreClosed() {
        val r = relay(echoServer().localPort, idle = 200)
        roundTrip(client(), r.localPort, "a")
        val end = System.currentTimeMillis() + 3000
        while (r.snapshot().sessions > 0 && System.currentTimeMillis() < end) Thread.sleep(50)
        assertEquals(0, r.snapshot().sessions)
    }

    @Test fun probeReadsMotdThroughRelay() {
        val srv = DatagramSocket(0, InetAddress.getByName("127.0.0.1")); closers += srv
        Thread {
            val b = ByteArray(2048); val p = DatagramPacket(b, b.size)
            srv.receive(p)
            val pong = RakNet.buildPong(1, 99, "MCPE;T;800;1.26.60;0;5;99;W;Survival;1;19132;19133;")
            srv.send(DatagramPacket(pong, pong.size, p.socketAddress))
        }.apply { isDaemon = true }.start()
        val r = relay(srv.localPort)
        val info = ServerProbe.ping("127.0.0.1", r.localPort).getOrThrow()
        assertEquals(800, info.protocol)
    }

    @Test fun startFailsCleanlyOnBadTarget() {
        val r = UdpRelay(RelayConfig("invalid.host.invalid", listenPort = 0))
        assertThrows(Exception::class.java) { r.start() }
    }
}
