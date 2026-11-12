package app.orbit.library

import java.nio.ByteBuffer

object Png {
    private val SIG = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    data class Info(val width: Int, val height: Int)

    fun info(b: ByteArray): Info? {
        if (b.size < 24) return null
        for (i in 0 until 8) if (b[i] != SIG[i]) return null
        val bb = ByteBuffer.wrap(b)
        if (bb.getInt(12) != 0x49484452) return null
        val w = bb.getInt(16); val h = bb.getInt(20)
        return if (w <= 0 || h <= 0 || w > 16384 || h > 16384) null else Info(w, h)
    }

    /**
     * Bedrock skin textures are 64x32 (legacy) or 64x64, or the same ratio scaled by a whole number (128x128, ...).
     * No file-size cap: only the shape is validated.
     */
    fun isValidSkin(b: ByteArray): Boolean {
        val i = info(b) ?: return false
        return i.width >= 64 && i.width % 64 == 0 && (i.height == i.width || i.height * 2 == i.width)
    }

    /** Capes are wider than tall (64x32 in vanilla, or scaled). */
    fun isValidCape(b: ByteArray): Boolean {
        val i = info(b) ?: return false
        return i.width >= 22 && i.width > i.height
    }
}
