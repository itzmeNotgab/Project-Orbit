package app.orbit.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/** Parsed Bedrock server advertisement ("MCPE;motd;protocol;version;online;max;..."). */
data class ServerInfo(
    val edition: String, val motd: String, val protocol: Int?, val version: String,
    val online: Int?, val max: Int?, val raw: String,
) {
    companion object {
        fun parse(raw: String): ServerInfo {
            val p = raw.split(';')
            fun at(i: Int) = p.getOrNull(i).orEmpty()
            return ServerInfo(at(0), at(1), at(2).toIntOrNull(), at(3), at(4).toIntOrNull(), at(5).toIntOrNull(), raw)
        }
    }
}

object ServerProbe {
    /** Pings a real Bedrock server. This is how ORBIT learns the server's actual protocol number. */
    fun ping(host: String, port: Int, timeoutMs: Int = 3000): Result<ServerInfo> = runCatching {
        DatagramSocket().use { s ->
            s.soTimeout = timeoutMs
            val out = RakNet.buildPing(System.currentTimeMillis(), 0x0B17L)
            s.send(DatagramPacket(out, out.size, InetSocketAddress(host, port)))
            val buf = ByteArray(2048)
            val p = DatagramPacket(buf, buf.size)
            try { s.receive(p) } catch (e: SocketTimeoutException) { error("no pong from $host:$port within ${timeoutMs}ms") }
            val pong = RakNet.parsePong(buf, p.length) ?: error("reply was not a RakNet pong")
            ServerInfo.parse(pong.motd)
        }
    }
}
