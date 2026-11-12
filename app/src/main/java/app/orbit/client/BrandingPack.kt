package app.orbit.client

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Renders the ORBIT Nebula title image used by the optional branding resource pack.
 * Whether Minecraft 1.26.60.4 applies it to the title screen is NOT verified.
 */
object BrandingPack {
    fun renderTitlePng(accent: Int = Color.parseColor("#A855F7")): ByteArray {
        val w = 1024; val h = 256
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(w * 0.18f, h * 0.5f, h * 0.9f, Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(w * 0.18f, h * 0.5f, h * 0.9f, p)
        p.shader = null
        p.color = accent; c.drawCircle(w * 0.18f, h * 0.5f, h * 0.2f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = Color.parseColor("#EDE7F6")
        c.save(); c.rotate(-22f, w * 0.18f, h * 0.5f); c.drawOval(w * 0.18f - h * 0.42f, h * 0.5f - h * 0.12f, w * 0.18f + h * 0.42f, h * 0.5f + h * 0.12f, p); c.restore()
        p.style = Paint.Style.FILL; p.color = Color.parseColor("#EDE7F6"); p.textSize = h * 0.48f
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); p.letterSpacing = 0.25f
        c.drawText("ORBIT", w * 0.34f, h * 0.62f, p)
        p.textSize = h * 0.13f; p.letterSpacing = 0.3f; p.color = accent
        c.drawText("BEYOND VANILLA", w * 0.345f, h * 0.84f, p)
        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, out); bmp.recycle()
        return out.toByteArray()
    }
}
