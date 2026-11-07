package app.orbit.network

import java.nio.ByteBuffer

/** Minimal RakNet offline-message helpers (unconnected ping/pong), used to probe real servers. */
object RakNet {
    const val ID_UNCONNECTED_PING = 0x01
    const val ID_UNCONNECTED_PONG = 0x1C

    val MAGIC: ByteArray = byteArrayOf(
        0x00, 0xFF.toByte(), 0xFF.toByte(), 0x00, 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(),
        0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0x12, 0x34, 0x56, 0x78,
    )

    /** id(1) | time(8) | magic(16) | clientGuid(8) */
    fun buildPing(timeMs: Long, clientGuid: Long): ByteArray =
        ByteBuffer.allocate(33).put(ID_UNCONNECTED_PING.toByte()).putLong(timeMs).put(MAGIC).putLong(clientGuid).array()

    /** id(1) | time(8) | serverGuid(8) | magic(16) | len(2) | motd */
    fun buildPong(timeMs: Long, serverGuid: Long, motd: String): ByteArray {
        val s = motd.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(35 + s.size).put(ID_UNCONNECTED_PONG.toByte()).putLong(timeMs)
            .putLong(serverGuid).put(MAGIC).putShort(s.size.toShort()).put(s).array()
    }

    class Pong(val timeMs: Long, val serverGuid: Long, val motd: String)

    fun isPing(d: ByteArray, len: Int) = len >= 33 && d[0].toInt() == ID_UNCONNECTED_PING && magicAt(d, 9)

    fun parsePong(d: ByteArray, len: Int): Pong? {
        if (len < 35 || d[0].toInt() != ID_UNCONNECTED_PONG || !magicAt(d, 17)) return null
        val bb = ByteBuffer.wrap(d, 0, len)
        val time = bb.getLong(1)
        val guid = bb.getLong(9)
        val slen = bb.getShort(33).toInt() and 0xFFFF
        if (35 + slen > len) return null
        return Pong(time, guid, String(d, 35, slen, Charsets.UTF_8))
    }

    private fun magicAt(d: ByteArray, off: Int): Boolean {
        if (d.size < off + 16) return false
        for (i in 0 until 16) if (d[off + i] != MAGIC[i]) return false
        return true
    }
}
