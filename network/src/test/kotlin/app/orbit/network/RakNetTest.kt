package app.orbit.network

import org.junit.Assert.*
import org.junit.Test

class RakNetTest {
    @Test fun pingLayout() {
        val p = RakNet.buildPing(5L, 9L)
        assertEquals(33, p.size)
        assertEquals(0x01, p[0].toInt())
        assertTrue(RakNet.isPing(p, p.size))
    }

    @Test fun pongRoundTrip() {
        val b = RakNet.buildPong(7L, 42L, "MCPE;Hi;800;1.26.60;1;10;42;W;Survival;1;19132;19133;")
        val pong = RakNet.parsePong(b, b.size)!!
        assertEquals(7L, pong.timeMs); assertEquals(42L, pong.serverGuid)
        assertTrue(pong.motd.startsWith("MCPE;Hi"))
    }

    @Test fun rejectsTruncatedAndWrongMagic() {
        val b = RakNet.buildPong(1, 2, "abc")
        assertNull(RakNet.parsePong(b, 20))
        assertNull(RakNet.parsePong(b, b.size - 1))
        b[20] = 0
        assertNull(RakNet.parsePong(b, b.size))
    }

    @Test fun motdParsing() {
        val i = ServerInfo.parse("MCPE;Hi;800;1.26.60;3;20;1;W;Survival;1;19132;19133;")
        assertEquals(800, i.protocol); assertEquals("1.26.60", i.version)
        assertEquals(3, i.online); assertEquals(20, i.max)
        assertNull(ServerInfo.parse("garbage").protocol)
    }
}
