package expo.modules.orbitnative

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class OrbitNativeModule : Module() {
  private fun readInfo(pkg: String): Map<String, Any>? {
    val ctx = appContext.reactContext ?: return null
    return try {
      val info = if (Build.VERSION.SDK_INT >= 33) {
        ctx.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
      } else {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(pkg, 0)
      }
      val code: Long = if (Build.VERSION.SDK_INT >= 28) {
        info.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
      }
      mapOf(
        "versionName" to (info.versionName ?: ""),
        "versionCode" to code.toDouble()
      )
    } catch (e: PackageManager.NameNotFoundException) {
      null
    } catch (e: Exception) {
      null
    }
  }

  private fun launch(pkg: String): Boolean {
    val ctx = appContext.reactContext ?: return false
    val intent: Intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
      ctx.startActivity(intent)
      true
    } catch (e: Exception) {
      false
    }
  }

  override fun definition() = ModuleDefinition {
    Name("OrbitNative")

    Function("getPackageInfo") { pkg: String -> readInfo(pkg) }

    Function("launchPackage") { pkg: String -> launch(pkg) }

    Function("getAndroidSdk") { Build.VERSION.SDK_INT }
  }
}
