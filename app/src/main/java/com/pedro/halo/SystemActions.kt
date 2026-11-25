package com.pedro.halo

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/** Optional accessibility service used ONLY for lock-screen and notification-panel global actions. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onUnbind(intent: Intent?): Boolean { if (instance === this) instance = null; return super.onUnbind(intent) }

    companion object {
        @Volatile var instance: LockService? = null
    }
}

object SystemActions {
    fun isAccessibilityOn(ctx: Context): Boolean {
        if (LockService.instance != null) return true
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, LockService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun openAccessibilitySettings(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Returns false when the lock action isn't available (service off). */
    fun lockScreen(): Boolean {
        val s = LockService.instance ?: return false
        return if (Build.VERSION.SDK_INT >= 28) s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) else false
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications(ctx: Context): Boolean {
        LockService.instance?.let { if (it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) return true }
        return runCatching {
            val sbs = ctx.getSystemService("statusbar")
            val cls = Class.forName("android.app.StatusBarManager")
            cls.getMethod("expandNotificationsPanel").invoke(sbs)
            true
        }.getOrDefault(false)
    }

    fun isDefaultLauncher(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = ctx.packageManager.resolveActivity(i, 0)
        return r?.activityInfo?.packageName == ctx.packageName
    }

    fun openHomeSettings(ctx: Context) {
        val tries = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (t in tries) {
            if (runCatching { ctx.startActivity(t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)) return
        }
    }

    fun openWallpaperPicker(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
