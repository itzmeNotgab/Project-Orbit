package app.orbit.client

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"

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

/** Reads a picked document fully. Returns null if it does not fit in memory. */
fun readAllBytes(ctx: Context, uri: android.net.Uri): ByteArray? = try {
    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
} catch (e: OutOfMemoryError) { null }

/** Streams a picked document into [dest] without loading it into memory. Returns bytes copied. */
fun copyUriToFile(ctx: Context, uri: android.net.Uri, dest: java.io.File): Long =
    (ctx.contentResolver.openInputStream(uri) ?: error("Could not open the selected file")).use { i ->
        dest.outputStream().use { o -> i.copyTo(o) }
    }

fun displayName(ctx: Context, uri: android.net.Uri, fallback: String): String =
    runCatching {
        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.replace(Regex("[^A-Za-z0-9._ -]"), "_")?.takeIf { it.isNotBlank() } ?: fallback

fun humanSize(b: Long): String = when {
    b < 1024 -> "$b B"; b < 1024 * 1024 -> "%.1f KB".format(java.util.Locale.US, b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(java.util.Locale.US, b / 1048576.0)
    else -> "%.2f GB".format(java.util.Locale.US, b / 1073741824.0)
}
