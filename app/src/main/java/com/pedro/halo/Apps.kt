package com.pedro.halo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.Collator

/** One launchable activity. [key] = "package/class#userSerial". */
data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWork: Boolean,
) {
    val packageName: String get() = component.packageName
}

data class IconPackInfo(val packageName: String, val label: String)

class AppRepository private constructor(private val ctx: Context) {
    private val launcherApps = ctx.getSystemService(LauncherApps::class.java)
    private val userManager = ctx.getSystemService(UserManager::class.java)
    private val pm = ctx.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps
    private val _version = MutableStateFlow(0)
    /** Bumped when packages change so icon caches can refresh. */
    val version: StateFlow<Int> = _version

    private val infoByKey = HashMap<String, LauncherActivityInfo>()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh(packageName)
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh(packageName)
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh(packageName)
        override fun onPackagesAvailable(p: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh(null)
        override fun onPackagesUnavailable(p: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh(null)
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh(null)
    }

    fun refresh(changedPkg: String?) {
        scope.launch {
            val list = ArrayList<AppEntry>()
            val map = HashMap<String, LauncherActivityInfo>()
            val me = Process.myUserHandle()
            for (user in userManager.userProfiles) {
                val serial = userManager.getSerialNumberForUser(user)
                for (info in runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())) {
                    if (info.componentName.packageName == ctx.packageName && user == me &&
                        info.componentName.className.endsWith("MainActivity")) continue
                    val key = "${info.componentName.flattenToShortString()}#$serial"
                    map[key] = info
                    list += AppEntry(key, info.label?.toString() ?: info.componentName.packageName,
                        info.componentName, user, user != me)
                }
            }
            val collator = Collator.getInstance()
            list.sortWith { a, b -> collator.compare(a.label, b.label) }
            synchronized(infoByKey) { infoByKey.clear(); infoByKey.putAll(map) }
            if (changedPkg != null) IconCache.invalidatePackage(changedPkg)
            _apps.value = list
            _version.value = _version.value + 1
        }
    }

    /** Test hooks (unit-test previews only). */
    @Volatile var testIcons: ((String) -> Drawable?)? = null
    fun setAppsForTest(list: List<AppEntry>) { _apps.value = list; _version.value = _version.value + 1 }

    fun info(key: String): LauncherActivityInfo? = synchronized(infoByKey) { infoByKey[key] }

    fun rawIcon(key: String, density: Int): Drawable? = testIcons?.invoke(key) ?: info(key)?.let { runCatching { it.getIcon(density) }.getOrNull() }

    fun launch(app: AppEntry, bounds: Rect? = null) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, bounds, null) }
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        val i = Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_USER, app.user)
        runCatching { ctx.startActivity(i) }
    }

    fun isSystemApp(app: AppEntry): Boolean = runCatching {
        val ai = info(app.key)?.applicationInfo ?: return false
        (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (ai.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
    }.getOrDefault(false)

    /** App shortcuts (only available while this app is the default launcher). */
    fun shortcuts(app: AppEntry): List<ShortcutInfo> = runCatching {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        (launcherApps.getShortcuts(q, app.user) ?: emptyList())
            .filter { it.activity == null || it.activity == app.component || it.activity?.packageName == app.packageName }
            .sortedBy { it.rank }
            .take(5)
    }.getOrDefault(emptyList())

    fun shortcutsAvailable(): Boolean = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    fun shortcutIcon(s: ShortcutInfo): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(s, ctx.resources.displayMetrics.densityDpi) }.getOrNull()

    fun startShortcut(s: ShortcutInfo) {
        runCatching { launcherApps.startShortcut(s, null, null) }
    }

    // ---------------- Icon packs (ADW / Nova / Go formats share appfilter.xml) ----------------

    fun iconPacks(): List<IconPackInfo> {
        val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME", "com.gau.go.launcherex.theme")
        val seen = LinkedHashMap<String, IconPackInfo>()
        for (a in actions) {
            val res = runCatching {
                if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(Intent(a), PackageManager.ResolveInfoFlags.of(0))
                else @Suppress("DEPRECATION") pm.queryIntentActivities(Intent(a), 0)
            }.getOrDefault(emptyList())
            for (r in res) {
                val p = r.activityInfo.packageName
                if (p !in seen) seen[p] = IconPackInfo(p, r.loadLabel(pm).toString())
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    @Volatile private var packCache: Pair<String, Map<String, String>>? = null

    /** Map of "pkg/cls" -> drawable name for the given pack. */
    private fun packMap(pack: String): Map<String, String> {
        packCache?.let { if (it.first == pack) return it.second }
        val map = HashMap<String, String>()
        runCatching {
            val res = pm.getResourcesForApplication(pack)
            val id = res.getIdentifier("appfilter", "xml", pack)
            val parser: XmlPullParser = if (id != 0) res.getXml(id) else {
                XmlPullParserFactory.newInstance().newPullParser().also { it.setInput(res.assets.open("appfilter.xml"), "utf-8") }
            }
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component") ?: ""
                    val draw = parser.getAttributeValue(null, "drawable") ?: ""
                    if (comp.startsWith("ComponentInfo{") && draw.isNotEmpty()) {
                        val c = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                        val cn = ComponentName.unflattenFromString(c)
                        if (cn != null) map[cn.packageName + "/" + cn.className] = draw
                    }
                }
                ev = parser.next()
            }
        }
        packCache = pack to map
        return map
    }

    fun packIcon(pack: String, app: AppEntry): Drawable? = runCatching {
        val name = packMap(pack)[app.component.packageName + "/" + app.component.className] ?: return null
        val res = pm.getResourcesForApplication(pack)
        val id = res.getIdentifier(name, "drawable", pack)
        if (id == 0) null else res.getDrawableForDensity(id, ctx.resources.displayMetrics.densityDpi, null)
    }.getOrNull()

    fun packCoverage(pack: String): Int {
        val m = packMap(pack)
        return _apps.value.count { m.containsKey(it.component.packageName + "/" + it.component.className) }
    }

    fun clearPackCache() { packCache = null }

    companion object {
        @Volatile private var inst: AppRepository? = null
        fun get(ctx: Context): AppRepository =
            inst ?: synchronized(this) { inst ?: AppRepository(ctx.applicationContext).also { inst = it } }
    }
}
