@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.pedro.halo

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a long-press menu is about. */
sealed class MenuTarget {
    data class Tile(val item: HomeItem, val inDock: Boolean) : MenuTarget()
    data class DrawerApp(val app: AppEntry) : MenuTarget()
    data class FolderApp(val folderId: Long, val app: AppEntry) : MenuTarget()
    data class PickFolder(val key: String, val tileId: Long?) : MenuTarget()
}

enum class Sheet { NONE, OPTIONS, WALLPAPER, WIDGETS }

fun <T> haloSpring(s: LauncherSettings) = spring<T>(dampingRatio = 0.82f, stiffness = 420f * s.animSpeed * s.animSpeed)

/** Wallpaper bitmaps (sharp + pre-blurred) for built-in wallpapers. Pre-blurring once keeps the drawer smooth. */
@Composable
fun rememberWallpaper(s: LauncherSettings): Pair<ImageBitmap?, ImageBitmap?> {
    val cfg = LocalConfiguration.current
    val dens = LocalDensity.current.density
    val def = Wallpapers.byId(s.wallpaper)
    val state by produceState<Pair<ImageBitmap?, ImageBitmap?>>(null to null, s.wallpaper, s.blurStrength) {
        if (def == null) { value = null to null; return@produceState }
        value = withContext(Dispatchers.Default) {
            val w = (cfg.screenWidthDp * dens / 2).toInt().coerceAtLeast(200)
            val h = (cfg.screenHeightDp * dens / 2 + 120).toInt().coerceAtLeast(400)
            val sharp = Wallpapers.render(def, w, h)
            val f = (s.blurStrength / 2).coerceIn(3, 30)
            val small = Bitmap.createScaledBitmap(sharp, (w / f).coerceAtLeast(4), (h / f).coerceAtLeast(8), true)
            val blurred = boxBlur(small, 2)
            sharp.asImageBitmap() to blurred.asImageBitmap()
        }
    }
    return state
}

/** Tiny box blur used on an already down-scaled bitmap. */
fun boxBlur(src: Bitmap, passes: Int): Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    repeat(passes) {
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0; var g = 0; var b = 0; var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = (x + dx).coerceIn(0, w - 1); val yy = (y + dy).coerceIn(0, h - 1)
                val c = px[yy * w + xx]; r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255; n++
            }
            tmp[y * w + x] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
        System.arraycopy(tmp, 0, px, 0, px.size)
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

@Composable
fun LauncherRoot(act: MainActivity) {
    val ctl = act.controller
    val s by ctl.store.settings.collectAsState()
    val layout by ctl.store.layout.collectAsState()
    val apps by ctl.repo.apps.collectAsState()
    val version by ctl.repo.version.collectAsState()
    LaunchedEffect(apps) { ctl.seedIfNeeded(apps) }
    val appMap = remember(apps) { apps.associateBy { it.key } }
    HaloTheme(s) {
        LauncherScreen(act, s, layout, apps, appMap, version)
    }
}

@Composable
fun LauncherScreen(
    act: MainActivity,
    s: LauncherSettings,
    layout: HomeLayout,
    apps: List<AppEntry>,
    appMap: Map<String, AppEntry>,
    version: Int,
) {
    val ctl = act.controller
    val ctx = LocalContext.current
    val c = LocalHalo.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val screenH = with(density) { cfg.screenHeightDp.dp.toPx() }

    val drawer = remember { Animatable(0f) }
    var openFolder by remember { mutableStateOf<Long?>(null) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var query by remember { mutableStateOf("") }
    val pager = rememberPagerState { layout.pages.size.coerceAtLeast(1) }
    val (wpSharp, wpBlur) = rememberWallpaper(s)
    val builtIn = Wallpapers.byId(s.wallpaper)

    // Decide label colour from the wallpaper brightness.
    val darkText by produceState(false, s.wallpaper, version) {
        value = if (builtIn != null) !builtIn.dark else withContext(Dispatchers.Default) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 31) {
                    val wc: WallpaperColors? = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    wc != null && (wc.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                } else false
            }.getOrDefault(false)
        }
    }
    val labelColor = if (darkText) Color(0xFF15151A) else Color.White
    val spec = IconSpec(s.iconShape, s.iconStyle, s.accent, isDark(s), s.iconPack)
    val iconDp = ((cfg.screenWidthDp / s.gridCols) * 0.62f * s.iconScale).coerceIn(36f, 72f).dp

    fun closeDrawer() = scope.launch { drawer.animateTo(0f, haloSpring(s)); query = "" }
    fun openDrawer() = scope.launch { drawer.animateTo(1f, haloSpring(s)) }

    // Home button: close overlays, then go to first page.
    val presses = act.homePresses.intValue
    LaunchedEffect(presses) {
        if (presses == 0) return@LaunchedEffect
        val hadOverlay = drawer.value > 0f || openFolder != null || menu != null || sheet != Sheet.NONE
        menu = null; openFolder = null; sheet = Sheet.NONE
        if (drawer.value > 0f) closeDrawer()
        if (!hadOverlay && pager.currentPage != 0) pager.animateScrollToPage(0)
    }
    BackHandler(enabled = true) {
        when {
            menu != null -> menu = null
            sheet != Sheet.NONE -> sheet = if (sheet == Sheet.OPTIONS) Sheet.NONE else Sheet.OPTIONS
            openFolder != null -> openFolder = null
            drawer.value > 0f -> closeDrawer()
        }
    }
    // Status bar icon colour follows what's behind it.
    val drawerShown by remember { derivedStateOf { drawer.value > 0.5f } }
    LaunchedEffect(drawerShown, darkText, c.dark) {
        act.setLightStatusBar(if (drawerShown) !c.dark else darkText)
    }
    // Real window blur for the phone's own wallpaper (only if the device supports cross-window blur).
    val drawerOpenish by remember { derivedStateOf { drawer.value > 0.02f } }
    val blurWindow = builtIn == null && s.blur && drawerOpenish
    LaunchedEffect(blurWindow) {
        if (Build.VERSION.SDK_INT >= 31) runCatching {
            if (act.windowManager.isCrossWindowBlurEnabled) act.window.setBackgroundBlurRadius(if (blurWindow) s.blurStrength * 2 else 0)
        }
    }

    fun launch(app: AppEntry) { ctl.repo.launch(app); if (drawer.value > 0f) scope.launch { delay(350); drawer.snapTo(0f); query = "" } }

    fun swipeDown() {
        when (s.swipeDown) {
            SwipeDownAction.NOTIFICATIONS -> if (!SystemActions.expandNotifications(ctx))
                Toast.makeText(ctx, "Couldn't open the notification panel on this phone", Toast.LENGTH_SHORT).show()
            SwipeDownAction.SEARCH -> openDrawer()
            SwipeDownAction.NONE -> {}
        }
    }
    fun doubleTap() {
        when (s.doubleTap) {
            DoubleTapAction.LOCK -> if (!SystemActions.lockScreen()) {
                Toast.makeText(ctx, "Turn on \"Halo Home gestures\" in Accessibility to lock with a double-tap", Toast.LENGTH_LONG).show()
                SystemActions.openAccessibilitySettings(ctx)
            }
            DoubleTapAction.DRAWER -> openDrawer()
            DoubleTapAction.NONE -> {}
        }
    }

    Box(Modifier.fillMaxSize()) {
        // ---------- Wallpaper ----------
        if (wpSharp != null) Image(wpSharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (builtIn != null) Box(Modifier.fillMaxSize().background(Color(builtIn.top)))

        // ---------- Home ----------
        val vt = remember { VelocityTracker() }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = drawer.value
                    alpha = 1f - p
                    scaleX = 1f - 0.06f * p; scaleY = 1f - 0.06f * p
                }
                .pointerInput(s.swipeUpDrawer, s.swipeDown, s.animSpeed) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f; vt.resetTracking() },
                        onVerticalDrag = { ch, dy ->
                            total += dy
                            vt.addPosition(ch.uptimeMillis, ch.position)
                            if (s.swipeUpDrawer && total < 0) scope.launch { drawer.snapTo((-total / (screenH * 0.55f)).coerceIn(0f, 1f)) }
                        },
                        onDragEnd = {
                            val v = vt.calculateVelocity().y
                            if (s.swipeUpDrawer && total < 0) {
                                if (drawer.value > 0.22f || v < -900f) openDrawer() else closeDrawer()
                            } else if (total > 90.dp.toPx() || v > 1400f) swipeDown()
                        },
                        onDragCancel = { if (drawer.value in 0.01f..0.99f) closeDrawer() },
                    )
                }
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0) { page ->
                    HomePage(
                        act, page, layout.pages.getOrElse(page) { emptyList() }, s, appMap, spec, version, iconDp,
                        labelColor, darkText,
                        onApp = ::launch,
                        onFolder = { openFolder = it },
                        onMenu = { menu = it },
                        onEmptyLongPress = { sheet = Sheet.OPTIONS },
                        onDoubleTap = ::doubleTap,
                    )
                }
                if (s.pageIndicator && layout.pages.size > 1) PageDots(pager, layout.pages.size, labelColor)
                else Spacer(Modifier.height(8.dp))
                Dock(layout.dock, s, appMap, spec, version, iconDp, labelColor, darkText,
                    onApp = ::launch, onFolder = { openFolder = it }, onMenu = { menu = it })
                Spacer(Modifier.height(10.dp))
            }
        }

        // ---------- Apps screen (drawer) ----------
        AppDrawer(
            act, s, apps, spec, version, iconDp, drawer, screenH, wpBlur, builtIn != null, query,
            onQuery = { query = it },
            onLaunch = ::launch,
            onMenu = { menu = it },
            onClose = { closeDrawer() },
        )

        // ---------- Folder popup ----------
        val folder = openFolder?.let { id -> layout.allItems().firstOrNull { it.id == id } as? HomeItem.Folder }
        if (openFolder != null && folder == null) LaunchedEffect(Unit) { openFolder = null }
        if (folder != null) FolderPopup(folder, s, appMap, spec, version, iconDp,
            onDismiss = { openFolder = null },
            onLaunch = { launch(it); openFolder = null },
            onMenu = { menu = it },
            onRename = { ctl.renameFolder(folder.id, it) })

        // ---------- Home options & sheets ----------
        if (sheet != Sheet.NONE) HomeSheets(act, sheet, s, layout, pager.currentPage,
            onSheet = { sheet = it }, onDismiss = { sheet = Sheet.NONE })

        // ---------- Long-press menu ----------
        menu?.let { m -> ItemMenu(act, m, s, layout, appMap, spec, version, onDismiss = { menu = null }, onMenu = { menu = it }) }

        // ---------- First run ----------
        if (!s.onboarded) Onboarding(act, s)
    }
}

@Composable
fun HomePage(
    act: MainActivity,
    page: Int,
    items: List<HomeItem>,
    s: LauncherSettings,
    appMap: Map<String, AppEntry>,
    spec: IconSpec,
    version: Int,
    iconDp: Dp,
    labelColor: Color,
    darkText: Boolean,
    onApp: (AppEntry) -> Unit,
    onFolder: (Long) -> Unit,
    onMenu: (MenuTarget) -> Unit,
    onEmptyLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val rows = layoutRows(items, s.gridCols)
    val header = if (page == 0) headerRows(s) else 0
    var used = header
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().pointerInput(s.doubleTap) {
            detectTapGestures(
                onLongPress = { onEmptyLongPress() },
                onDoubleTap = if (s.doubleTap != DoubleTapAction.NONE) ({ onDoubleTap() }) else null,
            )
        })
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            if (header > 0) Box(Modifier.weight(header.toFloat()).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                ClockHeader(s, labelColor)
            }
            for (r in rows) {
                val h = if (r is RowSlot.WidgetRow) r.item.rows else 1
                if (used + h > s.gridRows) break
                used += h
                Box(Modifier.weight(h.toFloat()).fillMaxWidth()) {
                    when (r) {
                        is RowSlot.WidgetRow -> WidgetBox(act, r.item)
                        is RowSlot.AppRow -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            for (i in 0 until s.gridCols) {
                                val it = r.items.getOrNull(i)
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (it != null) Tile(it, false, s, appMap, spec, version, iconDp, labelColor, darkText, s.showLabels, onApp, onFolder, onMenu)
                                }
                            }
                        }
                    }
                }
            }
            val rest = s.gridRows - used
            if (rest > 0) Spacer(Modifier.weight(rest.toFloat()))
        }
    }
}

@Composable
fun Tile(
    item: HomeItem, inDock: Boolean, s: LauncherSettings, appMap: Map<String, AppEntry>, spec: IconSpec, version: Int,
    iconDp: Dp, labelColor: Color, darkText: Boolean, labels: Boolean,
    onApp: (AppEntry) -> Unit, onFolder: (Long) -> Unit, onMenu: (MenuTarget) -> Unit,
) {
    when (item) {
        is HomeItem.App -> {
            val app = appMap[item.key]
            if (app == null && appMap.isEmpty()) {
                Spacer(Modifier.size(iconDp))
            } else if (app == null) {
                // App was uninstalled: show a faded placeholder that can be removed.
                IconCell(iconSize = iconDp, label = if (labels) "Missing" else null, labelColor = labelColor.copy(alpha = 0.6f),
                    onWallpaper = true, haptics = s.haptics, onClick = { onMenu(MenuTarget.Tile(item, inDock)) },
                    onLongClick = { onMenu(MenuTarget.Tile(item, inDock)) }) {
                    Box(Modifier.size(iconDp).clip(composeIconShape(s.iconShape)).background(Color(0x33FFFFFF)))
                }
            } else IconCell(iconSize = iconDp, label = if (labels) app.label else null, labelColor = labelColor,
                onWallpaper = true, haptics = s.haptics, onClick = { onApp(app) },
                onLongClick = { onMenu(MenuTarget.Tile(item, inDock)) }) {
                AppIconImage(app, spec, version, iconDp)
            }
        }
        is HomeItem.Folder -> IconCell(iconSize = iconDp, label = if (labels) item.name else null, labelColor = labelColor,
            onWallpaper = true, haptics = s.haptics, onClick = { onFolder(item.id) },
            onLongClick = { onMenu(MenuTarget.Tile(item, inDock)) }) {
            FolderIconImage(item.keys.mapNotNull { appMap[it] }, spec, version, iconDp, darkText.not() && isDarkPlate(s))
        }
        is HomeItem.Widget -> {}
    }
}

fun isDarkPlate(s: LauncherSettings) = s.themeMode != ThemeMode.LIGHT

@Composable
fun WidgetBox(act: MainActivity, item: HomeItem.Widget) {
    val info = remember(item.widgetId) { runCatching { act.widgetManager.getAppWidgetInfo(item.widgetId) }.getOrNull() }
    val density = LocalDensity.current
    if (info == null) {
        Box(Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(26.dp)).background(Color(0x55000000))
            .clickable { act.removeWidget(item) }, contentAlignment = Alignment.Center) {
            Text("Widget unavailable — tap to remove", color = Color.White, fontSize = 13.sp)
        }
        return
    }
    key(item.widgetId) {
        AndroidView(
            factory = { context -> act.widgetHost.createView(context, item.widgetId, info) },
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp)
                .onSizeChanged { sz ->
                    val w = (sz.width / density.density).toInt(); val h = (sz.height / density.density).toInt()
                    runCatching {
                        val opts = act.widgetManager.getAppWidgetOptions(item.widgetId)
                        opts.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
                        opts.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                        opts.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
                        opts.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
                        act.widgetManager.updateAppWidgetOptions(item.widgetId, opts)
                    }
                },
        )
    }
}

@Composable
fun PageDots(pager: PagerState, count: Int, color: Color) {
    Row(Modifier.fillMaxWidth().height(20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val sel = pager.currentPage == i
            Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (sel) 16.dp else 6.dp).clip(CircleShape)
                .background(if (sel) color else color.copy(alpha = 0.4f)))
        }
    }
}

@Composable
fun Dock(
    dock: List<HomeItem>, s: LauncherSettings, appMap: Map<String, AppEntry>, spec: IconSpec, version: Int, iconDp: Dp,
    labelColor: Color, darkText: Boolean, onApp: (AppEntry) -> Unit, onFolder: (Long) -> Unit, onMenu: (MenuTarget) -> Unit,
) {
    val bg = if (s.dockBackground) (if (darkText) Color(0x55FFFFFF) else Color(0x33FFFFFF)) else Color.Transparent
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(30.dp)).background(bg)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until s.dockCount) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                dock.getOrNull(i)?.let { Tile(it, true, s, appMap, spec, version, iconDp, labelColor, darkText, false, onApp, onFolder, onMenu) }
                    ?: Spacer(Modifier.size(iconDp))
            }
        }
    }
}

@Composable
fun ClockHeader(s: LauncherSettings, color: Color) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            runCatching {
                val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (i != null) {
                    val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    battery = if (l >= 0) l * 100 / sc else -1
                    val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
                }
            }
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50)
        }
    }
    val is24 = DateFormat.is24HourFormat(ctx)
    val hh = SimpleDateFormat(if (is24) "HH" else "h", Locale.getDefault()).format(now)
    val mm = SimpleDateFormat("mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEE, d MMMM", Locale.getDefault()).format(now)
    val extra = if (s.showBattery && battery >= 0) "   ${if (charging) "⚡" else "🔋"} $battery%" else ""
    val shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), blurRadius = 8f)
    val openClock = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        runCatching { ctx.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val openCal = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        runCatching {
            ctx.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    val sh = if (color.luminanceSimple() > 0.5f) shadow else null
    Column(Modifier.padding(start = 14.dp)) {
        when (s.clockStyle) {
            ClockStyle.BIG -> {
                Text("$hh:$mm", openClock, color = color, fontSize = 68.sp, fontWeight = FontWeight.Light,
                    style = androidx.compose.ui.text.TextStyle(shadow = sh))
                Text(date + extra, openCal, color = color.copy(alpha = 0.92f), fontSize = 16.sp, style = androidx.compose.ui.text.TextStyle(shadow = sh))
            }
            ClockStyle.STACKED -> {
                Text(hh.padStart(2, '0'), openClock, color = color, fontSize = 58.sp, fontWeight = FontWeight.SemiBold, lineHeight = 58.sp,
                    style = androidx.compose.ui.text.TextStyle(shadow = sh))
                Text(mm, openClock, color = color.copy(alpha = 0.85f), fontSize = 58.sp, fontWeight = FontWeight.Light, lineHeight = 58.sp,
                    style = androidx.compose.ui.text.TextStyle(shadow = sh))
                Text(date + extra, openCal, color = color.copy(alpha = 0.92f), fontSize = 14.sp, style = androidx.compose.ui.text.TextStyle(shadow = sh))
            }
            ClockStyle.COMPACT -> Row(verticalAlignment = Alignment.Bottom) {
                Text("$hh:$mm", openClock, color = color, fontSize = 34.sp, fontWeight = FontWeight.Normal, style = androidx.compose.ui.text.TextStyle(shadow = sh))
                Spacer(Modifier.width(10.dp))
                Text(date + extra, openCal.padding(bottom = 6.dp), color = color.copy(alpha = 0.9f), fontSize = 14.sp, style = androidx.compose.ui.text.TextStyle(shadow = sh))
            }
            ClockStyle.HIDDEN -> {}
        }
    }
}
