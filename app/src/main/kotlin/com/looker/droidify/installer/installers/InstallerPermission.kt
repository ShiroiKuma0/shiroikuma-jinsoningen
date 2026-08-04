package com.looker.droidify.installer.installers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.looker.droidify.utility.common.extension.getLauncherActivities
import com.looker.droidify.utility.common.extension.getPackageInfoCompat
import com.looker.droidify.utility.common.extension.intent
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import rikka.sui.Sui
import kotlin.coroutines.resume

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 87263

/**
 * shiroikuma fork: our own Shizuku, 白い熊 雫 — the one this app prefers to talk to.
 *
 * It is the `shizukuplus` flavor of shiroikuma-shizuku, which installs **beside** stock Shizuku.
 * Because both can be present at once it deliberately does not define
 * `moe.shizuku.manager.permission.API_V23` (that would fail install with -126, duplicate
 * permission); it declares `af.shizuku.plus.permission.API_V23` instead. So neither the package
 * name nor the permission name that upstream resolves by will find it, and the resolution below
 * looks for it by package first.
 */
private const val SHIROIKUMA_SHIZUKU_PACKAGE = "shiroikuma.shizuku"

/** The client permission 白い熊 雫 grants, in place of the stock one. */
const val SHIROIKUMA_SHIZUKU_PERMISSION = "af.shizuku.plus.permission.API_V23"

/**
 * Resolution order, per 白い熊: **ours first, the legacy one only as a fallback.**
 *
 * 1. `shiroikuma.shizuku`, if installed;
 * 2. whichever package declares the stock client permission (stock Shizuku, Sui, a rename);
 * 3. `moe.shizuku.privileged.api`, the hardcoded stock package.
 *
 * The binder itself is package-agnostic — a manager pushes it to our `ShizukuProvider` whoever it
 * is — so this ordering decides which app we *name*: what "Open Shizuku" launches and what the
 * installed-check believes.
 */
private fun Context.preferredShizukuPackage(): String? = when {
    isPackageInstalled(SHIROIKUMA_SHIZUKU_PACKAGE) -> SHIROIKUMA_SHIZUKU_PACKAGE
    else -> shizukuPermissionInfo()?.packageName
}

private fun Context.isPackageInstalled(packageName: String) =
    packageManager.getPackageInfoCompat(packageName) != null

fun launchShizuku(context: Context) {
    val packageName = context.preferredShizukuPackage()
        ?: ShizukuProvider.MANAGER_APPLICATION_ID
    val activities = context.packageManager.getLauncherActivities(packageName)
    if (activities.isEmpty()) return
    val intent = intent(Intent.ACTION_MAIN) {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setComponent(
            ComponentName(
                packageName,
                activities.first().first,
            ),
        )
        setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun initSui(context: Context) = Sui.init(context.packageName)

fun isSuiAvailable() = Sui.isSui()

private fun Context.shizukuPermissionInfo() =
    runCatching {
        packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0)
    }.getOrNull()

/**
 * shiroikuma fork: 白い熊 雫 counts as installed even though it declares neither the stock
 * package name nor the stock permission — checked first, so it is what an "is Shizuku there?"
 * question answers with.
 */
fun isShizukuInstalled(context: Context) =
    context.isPackageInstalled(SHIROIKUMA_SHIZUKU_PACKAGE) ||
        context.shizukuPermissionInfo() != null ||
        context.packageManager.getPackageInfoCompat(ShizukuProvider.MANAGER_APPLICATION_ID) != null

fun isShizukuAlive() = Shizuku.pingBinder()

fun isShizukuGranted() = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

suspend fun requestPermissionListener() = suspendCancellableCoroutine {
    val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            it.resume(grantResult == PackageManager.PERMISSION_GRANTED)
        }
    }
    Shizuku.addRequestPermissionResultListener(listener)
    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
    it.invokeOnCancellation {
        Shizuku.removeRequestPermissionResultListener(listener)
    }
}

fun requestShizuku() {
    Shizuku.shouldShowRequestPermissionRationale()
    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
}

fun isMagiskGranted(): Boolean {
    com.topjohnwu.superuser.Shell.getCachedShell() ?: com.topjohnwu.superuser.Shell.getShell()
    return com.topjohnwu.superuser.Shell.isAppGrantedRoot() == true
}
