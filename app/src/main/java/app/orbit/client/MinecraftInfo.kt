package app.orbit.client

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"
const val TARGET_MINECRAFT = "1.26.60.4"

data class MinecraftInfo(val installed: Boolean, val versionName: String?)

fun detectMinecraft(ctx: Context): MinecraftInfo = try {
    val pi = if (Build.VERSION.SDK_INT >= 33)
        ctx.packageManager.getPackageInfo(MINECRAFT_PACKAGE, PackageManager.PackageInfoFlags.of(0L))
    else @Suppress("DEPRECATION") ctx.packageManager.getPackageInfo(MINECRAFT_PACKAGE, 0)
    MinecraftInfo(true, pi.versionName)
} catch (e: PackageManager.NameNotFoundException) {
    MinecraftInfo(false, null)
}

fun launchMinecraft(ctx: Context): Boolean {
    val i = ctx.packageManager.getLaunchIntentForPackage(MINECRAFT_PACKAGE) ?: return false
    i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { ctx.startActivity(i) }.isSuccess
}

/** Hands a pack/world file to Minecraft through its own import path. Returns false if Minecraft can't be started. */
fun openInMinecraft(ctx: Context, file: java.io.File): Boolean = runCatching {
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/octet-stream").setPackage(MINECRAFT_PACKAGE)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)

/** Reads a picked document, refusing anything over [max] bytes. */
fun readCapped(ctx: Context, uri: android.net.Uri, max: Int): ByteArray? =
    ctx.contentResolver.openInputStream(uri)?.use { s ->
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16384); var total = 0
        while (true) { val r = s.read(buf); if (r < 0) break; total += r; if (total > max) return null; out.write(buf, 0, r) }
        out.toByteArray()
    }
