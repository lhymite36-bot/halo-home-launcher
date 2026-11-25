package com.pedro.halo

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class IconShape(val label: String) { SQUIRCLE("Squircle"), CIRCLE("Circle"), ROUNDED("Rounded square") }
enum class IconStyle(val label: String) { ORIGINAL("Original"), THEMED("Themed (accent)"), MONO("Monochrome") }
enum class ThemeMode(val label: String) { AUTO("Auto (follow system)"), LIGHT("Light"), DARK("Dark") }
enum class DrawerStyle(val label: String) { PAGED("Horizontal pages (One UI)"), LIST("Vertical list") }
enum class ClockStyle(val label: String) { BIG("Big"), STACKED("Stacked"), COMPACT("Compact"), HIDDEN("Hidden") }
enum class SwipeDownAction(val label: String) { NOTIFICATIONS("Open notification panel"), SEARCH("Open app search"), NONE("Do nothing") }
enum class DoubleTapAction(val label: String) { LOCK("Lock screen"), DRAWER("Open Apps screen"), NONE("Do nothing") }

/** All user options. Immutable; edit through [SettingsStore.update]. */
data class LauncherSettings(
    val gridCols: Int = 4,
    val gridRows: Int = 6,
    val iconScale: Float = 1.0f,
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconStyle: IconStyle = IconStyle.ORIGINAL,
    val showLabels: Boolean = true,
    val showDrawerLabels: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val accent: Int = 0xFF3D7BFF.toInt(),
    val drawerStyle: DrawerStyle = DrawerStyle.PAGED,
    val drawerCols: Int = 5,
    val dockCount: Int = 4,
    val dockBackground: Boolean = true,
    val blur: Boolean = true,
    val blurStrength: Int = 28,
    val wallpaper: String = "aurora", // built-in id, or "system"
    val clockStyle: ClockStyle = ClockStyle.BIG,
    val showBattery: Boolean = true,
    val pageIndicator: Boolean = true,
    val swipeUpDrawer: Boolean = true,
    val swipeDown: SwipeDownAction = SwipeDownAction.NOTIFICATIONS,
    val doubleTap: DoubleTapAction = DoubleTapAction.NONE,
    val haptics: Boolean = true,
    val iconPack: String = "", // package name of an ADW/Nova style icon pack, "" = none
    val hidden: Set<String> = emptySet(),
    val animSpeed: Float = 1.0f,
    val onboarded: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("gridCols", gridCols); put("gridRows", gridRows); put("iconScale", iconScale.toDouble())
        put("iconShape", iconShape.name); put("iconStyle", iconStyle.name); put("showLabels", showLabels)
        put("showDrawerLabels", showDrawerLabels); put("themeMode", themeMode.name); put("accent", accent)
        put("drawerStyle", drawerStyle.name); put("drawerCols", drawerCols); put("dockCount", dockCount)
        put("dockBackground", dockBackground); put("blur", blur); put("blurStrength", blurStrength)
        put("wallpaper", wallpaper); put("clockStyle", clockStyle.name); put("showBattery", showBattery)
        put("pageIndicator", pageIndicator); put("swipeUpDrawer", swipeUpDrawer); put("swipeDown", swipeDown.name)
        put("doubleTap", doubleTap.name); put("haptics", haptics); put("iconPack", iconPack)
        put("hidden", JSONArray(hidden.toList())); put("animSpeed", animSpeed.toDouble()); put("onboarded", onboarded)
    }

    companion object {
        private inline fun <reified T : Enum<T>> en(v: String?, d: T): T =
            runCatching { enumValueOf<T>(v ?: "") }.getOrDefault(d)

        fun fromJson(o: JSONObject): LauncherSettings {
            val d = LauncherSettings()
            val hid = mutableSetOf<String>()
            o.optJSONArray("hidden")?.let { a -> for (i in 0 until a.length()) hid += a.getString(i) }
            return LauncherSettings(
                gridCols = o.optInt("gridCols", d.gridCols).coerceIn(3, 6),
                gridRows = o.optInt("gridRows", d.gridRows).coerceIn(4, 7),
                iconScale = o.optDouble("iconScale", d.iconScale.toDouble()).toFloat().coerceIn(0.7f, 1.3f),
                iconShape = en(o.optString("iconShape"), d.iconShape),
                iconStyle = en(o.optString("iconStyle"), d.iconStyle),
                showLabels = o.optBoolean("showLabels", d.showLabels),
                showDrawerLabels = o.optBoolean("showDrawerLabels", d.showDrawerLabels),
                themeMode = en(o.optString("themeMode"), d.themeMode),
                accent = o.optInt("accent", d.accent),
                drawerStyle = en(o.optString("drawerStyle"), d.drawerStyle),
                drawerCols = o.optInt("drawerCols", d.drawerCols).coerceIn(4, 6),
                dockCount = o.optInt("dockCount", d.dockCount).coerceIn(3, 6),
                dockBackground = o.optBoolean("dockBackground", d.dockBackground),
                blur = o.optBoolean("blur", d.blur),
                blurStrength = o.optInt("blurStrength", d.blurStrength).coerceIn(4, 60),
                wallpaper = o.optString("wallpaper", d.wallpaper),
                clockStyle = en(o.optString("clockStyle"), d.clockStyle),
                showBattery = o.optBoolean("showBattery", d.showBattery),
                pageIndicator = o.optBoolean("pageIndicator", d.pageIndicator),
                swipeUpDrawer = o.optBoolean("swipeUpDrawer", d.swipeUpDrawer),
                swipeDown = en(o.optString("swipeDown"), d.swipeDown),
                doubleTap = en(o.optString("doubleTap"), d.doubleTap),
                haptics = o.optBoolean("haptics", d.haptics),
                iconPack = o.optString("iconPack", ""),
                hidden = hid,
                animSpeed = o.optDouble("animSpeed", 1.0).toFloat().coerceIn(0.5f, 2f),
                onboarded = o.optBoolean("onboarded", false),
            )
        }
    }
}

/** Home screen layout: pages of items plus the dock. Items are referenced by app key. */
sealed class HomeItem {
    abstract val id: Long
    data class App(override val id: Long, val key: String) : HomeItem()
    data class Folder(override val id: Long, val name: String, val keys: List<String>) : HomeItem()
    data class Widget(override val id: Long, val widgetId: Int, val rows: Int) : HomeItem()

    fun toJson(): JSONObject = when (this) {
        is App -> JSONObject().put("t", "app").put("id", id).put("key", key)
        is Folder -> JSONObject().put("t", "folder").put("id", id).put("name", name).put("keys", JSONArray(keys))
        is Widget -> JSONObject().put("t", "widget").put("id", id).put("wid", widgetId).put("rows", rows)
    }

    companion object {
        fun fromJson(o: JSONObject): HomeItem? = when (o.optString("t")) {
            "app" -> App(o.getLong("id"), o.getString("key"))
            "folder" -> {
                val a = o.optJSONArray("keys") ?: JSONArray()
                Folder(o.getLong("id"), o.optString("name", "Folder"), List(a.length()) { a.getString(it) })
            }
            "widget" -> Widget(o.getLong("id"), o.getInt("wid"), o.optInt("rows", 2))
            else -> null
        }
    }
}

data class HomeLayout(
    val pages: List<List<HomeItem>> = listOf(emptyList()),
    val dock: List<HomeItem> = emptyList(),
    val seeded: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pages", JSONArray(pages.map { p -> JSONArray(p.map { it.toJson() }) }))
        put("dock", JSONArray(dock.map { it.toJson() }))
        put("seeded", seeded)
    }

    fun allItems(): List<HomeItem> = pages.flatten() + dock

    companion object {
        fun fromJson(o: JSONObject): HomeLayout {
            val pa = o.optJSONArray("pages") ?: JSONArray()
            val pages = List(pa.length()) { i ->
                val p = pa.getJSONArray(i)
                List(p.length()) { j -> HomeItem.fromJson(p.getJSONObject(j)) }.filterNotNull()
            }.ifEmpty { listOf(emptyList()) }
            val da = o.optJSONArray("dock") ?: JSONArray()
            val dock = List(da.length()) { HomeItem.fromJson(da.getJSONObject(it)) }.filterNotNull()
            return HomeLayout(pages, dock, o.optBoolean("seeded", false))
        }
    }
}

/** Single place that persists settings + layout and exposes them as flows. */
class SettingsStore private constructor(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("halo", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<LauncherSettings> = _settings
    private val _layout = MutableStateFlow(loadLayout())
    val layout: StateFlow<HomeLayout> = _layout

    private fun load(): LauncherSettings =
        prefs.getString("settings", null)?.let { runCatching { LauncherSettings.fromJson(JSONObject(it)) }.getOrNull() }
            ?: LauncherSettings()

    private fun loadLayout(): HomeLayout =
        prefs.getString("layout", null)?.let { runCatching { HomeLayout.fromJson(JSONObject(it)) }.getOrNull() }
            ?: HomeLayout()

    fun update(f: (LauncherSettings) -> LauncherSettings) {
        val n = f(_settings.value)
        _settings.value = n
        prefs.edit().putString("settings", n.toJson().toString()).apply()
    }

    fun updateLayout(f: (HomeLayout) -> HomeLayout) {
        val n = f(_layout.value)
        _layout.value = n
        prefs.edit().putString("layout", n.toJson().toString()).apply()
    }

    fun nextId(): Long {
        val n = prefs.getLong("nextId", 1L)
        prefs.edit().putLong("nextId", n + 1).apply()
        return n
    }

    /** Backup file: settings + home layout (widgets are re-added by the user after restore). */
    fun exportBackup(): String = JSONObject()
        .put("format", "halo-home-backup")
        .put("version", 1)
        .put("settings", _settings.value.toJson())
        .put("layout", _layout.value.toJson())
        .toString(2)

    fun importBackup(text: String): Boolean = runCatching {
        val o = JSONObject(text)
        require(o.optString("format") == "halo-home-backup")
        val s = LauncherSettings.fromJson(o.getJSONObject("settings")).copy(onboarded = true)
        val l = HomeLayout.fromJson(o.getJSONObject("layout"))
        val noWidgets = l.copy(pages = l.pages.map { p -> p.filter { it !is HomeItem.Widget } }, seeded = true)
        update { s }
        updateLayout { noWidgets }
        // keep id counter above restored ids
        val maxId = noWidgets.allItems().maxOfOrNull { it.id } ?: 0L
        prefs.edit().putLong("nextId", maxOf(prefs.getLong("nextId", 1L), maxId + 1)).apply()
        true
    }.getOrDefault(false)

    fun resetAll() {
        update { LauncherSettings(onboarded = true) }
        updateLayout { HomeLayout() }
    }

    companion object {
        @Volatile private var inst: SettingsStore? = null
        fun get(ctx: Context): SettingsStore =
            inst ?: synchronized(this) { inst ?: SettingsStore(ctx.applicationContext).also { inst = it } }
    }
}
