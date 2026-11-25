@file:OptIn(ExperimentalLayoutApi::class)

package com.pedro.halo

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT), SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            val store = SettingsStore.get(this)
            val s by store.settings.collectAsState()
            HaloTheme(s) { SettingsRoot(s, store) }
        }
    }
}

enum class Page(val title: String) { MAIN("Halo Home"), HIDE("Hide apps"), ICONPACK("Icon packs"), WALLPAPER("Wallpapers") }

@Composable
fun SettingsRoot(s: LauncherSettings, store: SettingsStore) {
    var page by remember { mutableStateOf(Page.MAIN) }
    val ctx = LocalContext.current
    BackHandler(enabled = page != Page.MAIN) { page = Page.MAIN }
    val c = LocalHalo.current
    val activity = ctx as ComponentActivity
    LaunchedEffect(c.dark) {
        val ic = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        ic.isAppearanceLightStatusBars = !c.dark; ic.isAppearanceLightNavigationBars = !c.dark
    }
    Box(Modifier.fillMaxSize().background(c.background)) {
        when (page) {
            Page.MAIN -> MainSettings(s, store) { page = it }
            Page.HIDE -> HideApps(s, store) { page = Page.MAIN }
            Page.ICONPACK -> IconPacks(s, store) { page = Page.MAIN }
            Page.WALLPAPER -> SubPage("Wallpapers", { page = Page.MAIN }) {
                item { Card { Column(Modifier.padding(16.dp)) { WallpaperPicker(activity, s) } } }
            }
        }
    }
}

/** One UI-style screen: tall airy title area, then grouped rounded cards. */
@Composable
fun OneUiScaffold(title: String, onBack: (() -> Unit)?, content: LazyListScope.() -> Unit) {
    val c = LocalHalo.current
    val state = rememberLazyListState()
    val h = (LocalConfiguration.current.screenHeightDp * 0.30f).dp
    val collapsed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 200 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 40.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(h).statusBarsPadding(), contentAlignment = Alignment.Center) {
                    Text(title, color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Normal,
                        modifier = Modifier.graphicsLayer {
                            val off = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset else 400
                            alpha = (1f - off / 300f).coerceIn(0f, 1f)
                        })
                }
            }
            content()
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
        // Small top bar
        Row(Modifier.fillMaxWidth().background(if (collapsed) c.background else Color.Transparent).statusBarsPadding().height(56.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.text,
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable { onBack() }.padding(12.dp))
            else Spacer(Modifier.width(14.dp))
            if (collapsed) Text(title, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SubPage(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) = OneUiScaffold(title, onBack, content)

@Composable
fun Card(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalHalo.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(26.dp)).background(c.card), content = content)
}

@Composable
fun SectionLabel(t: String) {
    Text(t, color = LocalHalo.current.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp))
}

@Composable
fun SwitchRow(title: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalHalo.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp)
            if (sub != null) Text(sub, color = c.subText, fontSize = 13.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White))
    }
}

@Composable
fun NavRow(title: String, sub: String? = null, onClick: () -> Unit) {
    val c = LocalHalo.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp)
            if (sub != null) Text(sub, color = c.accent.copy(alpha = 0.9f), fontSize = 13.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = c.subText)
    }
}

@Composable
fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    val c = LocalHalo.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, color = c.text, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in options) {
                val sel = o == selected
                Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (sel) c.accent else c.chip).clickable { onPick(o) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text(label(o), color = if (sel) Color.White else c.text, fontSize = 14.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    val c = LocalHalo.current
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row { Text(title, color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f)); Text(display, color = c.subText, fontSize = 14.sp) }
        Slider(v, { v = it }, valueRange = range, onValueChangeFinished = { onChange(v) },
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.chip))
    }
}

val ACCENTS = listOf(0xFF3D7BFF, 0xFF6A5CFF, 0xFF9B5CFF, 0xFFE0569B, 0xFFFF5C5C, 0xFFFF8A3D, 0xFFF2B21B, 0xFF2BB673, 0xFF14B8A6, 0xFF28C8E8, 0xFF8E8E93)

@Composable
fun AccentPicker(s: LauncherSettings, onPick: (Int) -> Unit) {
    val c = LocalHalo.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Accent colour", color = c.text, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (a in ACCENTS) {
                val sel = s.accent == a.toInt()
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(a))
                    .then(if (sel) Modifier.border(3.dp, c.text.copy(alpha = 0.8f), CircleShape) else Modifier)
                    .clickable { onPick(a.toInt()) })
            }
        }
        Spacer(Modifier.height(12.dp))
        // Custom hue slider
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(s.accent, it) }
        var hue by remember(s.accent) { mutableFloatStateOf(hsv[0]) }
        Text("Custom hue", color = c.subText, fontSize = 13.sp)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
            .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.75f, 0.95f) })))
        Slider(hue, { hue = it }, valueRange = 0f..359f,
            onValueChangeFinished = { onPick(Color.hsv(hue, 0.75f, 0.95f).toArgb()) },
            colors = SliderDefaults.colors(thumbColor = Color.hsv(hue, 0.75f, 0.95f), activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
    }
}

@Composable
fun IconShapePreview(shape: IconShape, selected: Boolean, onClick: () -> Unit) {
    val c = LocalHalo.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(6.dp)) {
        Box(Modifier.size(52.dp).clip(composeIconShape(shape))
            .background(if (selected) Brush.linearGradient(listOf(c.accent, c.accent.copy(alpha = 0.6f))) else Brush.linearGradient(listOf(c.chip, c.chip))))
        Spacer(Modifier.height(6.dp))
        Text(shape.label, color = if (selected) c.accent else c.subText, fontSize = 12.sp)
    }
}

@Composable
fun MainSettings(s: LauncherSettings, store: SettingsStore, go: (Page) -> Unit) {
    val ctx = LocalContext.current
    val c = LocalHalo.current
    val ctl = remember { LauncherController(ctx) }
    var isDefault by remember { mutableStateOf(SystemActions.isDefaultLauncher(ctx)) }
    var a11y by remember { mutableStateOf(SystemActions.isAccessibilityOn(ctx)) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1500); isDefault = SystemActions.isDefaultLauncher(ctx); a11y = SystemActions.isAccessibilityOn(ctx) } }
    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(store.exportBackup().toByteArray()) } }.isSuccess
            Toast.makeText(ctx, if (ok) "Backup saved" else "Couldn't save backup", Toast.LENGTH_SHORT).show()
        }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val txt = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            val ok = txt != null && store.importBackup(txt)
            IconCache.clear()
            Toast.makeText(ctx, if (ok) "Layout and settings restored" else "That file isn't a Halo Home backup", Toast.LENGTH_LONG).show()
        }
    }
    fun upd(f: (LauncherSettings) -> LauncherSettings) = store.update(f)
    fun updIcons(f: (LauncherSettings) -> LauncherSettings) { store.update(f); IconCache.clear() }

    OneUiScaffold("Halo Home settings", onBack = { (ctx as ComponentActivity).finish() }) {
        if (!isDefault) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(26.dp))
                .background(c.accent.copy(alpha = 0.14f)).padding(18.dp)) {
                Text("Halo Home isn't your Home app yet", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Settings ▸ Apps ▸ Default apps ▸ Home app", color = c.subText, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(c.accent).clickable { SystemActions.openHomeSettings(ctx) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)) { Text("Set as default", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
        item { SectionLabel("Home screen") }
        item {
            Card {
                val grids = listOf(4 to 5, 4 to 6, 5 to 5, 5 to 6)
                ChoiceRow("Home screen grid", grids, s.gridCols to s.gridRows, { "${it.first}×${it.second}" }) { g ->
                    upd { it.copy(gridCols = g.first, gridRows = g.second) }; ctl.reflow()
                }
                SliderRow("Icon size", s.iconScale, 0.75f..1.25f, "${(s.iconScale * 100).toInt()}%") { v -> upd { it.copy(iconScale = v) } }
                SwitchRow("Show app labels", checked = s.showLabels) { v -> upd { it.copy(showLabels = v) } }
                ChoiceRow("Dock icons", listOf(3, 4, 5, 6), s.dockCount, { "$it" }) { n -> upd { it.copy(dockCount = n) } }
                SwitchRow("Dock background", "Frosted pill behind the dock", s.dockBackground) { v -> upd { it.copy(dockBackground = v) } }
                SwitchRow("Page indicator", checked = s.pageIndicator) { v -> upd { it.copy(pageIndicator = v) } }
                ChoiceRow("Clock widget", ClockStyle.entries, s.clockStyle, { it.label }) { v -> upd { it.copy(clockStyle = v) }; ctl.reflow() }
                SwitchRow("Battery next to date", checked = s.showBattery) { v -> upd { it.copy(showBattery = v) } }
            }
        }
        item { SectionLabel("Apps screen") }
        item {
            Card {
                ChoiceRow("Layout", DrawerStyle.entries, s.drawerStyle, { it.label }) { v -> upd { it.copy(drawerStyle = v) } }
                ChoiceRow("Columns", listOf(4, 5, 6), s.drawerCols, { "$it" }) { v -> upd { it.copy(drawerCols = v) } }
                SwitchRow("Show app labels", checked = s.showDrawerLabels) { v -> upd { it.copy(showDrawerLabels = v) } }
                NavRow("Hide apps", if (s.hidden.isEmpty()) "None hidden" else "${s.hidden.size} hidden") { go(Page.HIDE) }
            }
        }
        item { SectionLabel("Icons") }
        item {
            Card {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("Icon shape", color = c.text, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (sh in IconShape.entries) IconShapePreview(sh, s.iconShape == sh) { updIcons { it.copy(iconShape = sh) } }
                    }
                }
                ChoiceRow("Icon style", IconStyle.entries, s.iconStyle, { it.label }) { v -> updIcons { it.copy(iconStyle = v) } }
                NavRow("Icon pack", if (s.iconPack.isEmpty()) "Default icons" else s.iconPack) { go(Page.ICONPACK) }
            }
        }
        item { SectionLabel("Look & feel") }
        item {
            Card {
                ChoiceRow("Theme", ThemeMode.entries, s.themeMode, { it.label }) { v -> updIcons { it.copy(themeMode = v) } }
                AccentPicker(s) { v -> updIcons { it.copy(accent = v) } }
                NavRow("Wallpaper", Wallpapers.byId(s.wallpaper)?.name ?: "Phone wallpaper") { go(Page.WALLPAPER) }
                SwitchRow("Background blur", "Frosted glass behind Apps screen", s.blur) { v -> upd { it.copy(blur = v) } }
                if (s.blur) SliderRow("Blur strength", s.blurStrength.toFloat(), 8f..60f, "${s.blurStrength}") { v -> upd { it.copy(blurStrength = v.toInt()) } }
                SliderRow("Animation speed", s.animSpeed, 0.5f..2f, String.format("%.1f×", s.animSpeed)) { v -> upd { it.copy(animSpeed = v) } }
                SwitchRow("Haptic feedback", checked = s.haptics) { v -> upd { it.copy(haptics = v) } }
            }
        }
        item { SectionLabel("Gestures") }
        item {
            Card {
                SwitchRow("Swipe up for Apps screen", checked = s.swipeUpDrawer) { v -> upd { it.copy(swipeUpDrawer = v) } }
                ChoiceRow("Swipe down", SwipeDownAction.entries, s.swipeDown, { it.label }) { v -> upd { it.copy(swipeDown = v) } }
                ChoiceRow("Double-tap empty space", DoubleTapAction.entries, s.doubleTap, { it.label }) { v -> upd { it.copy(doubleTap = v) } }
                NavRow("Gesture service", if (a11y) "On — double-tap to lock works" else "Off — needed for double-tap to lock") {
                    SystemActions.openAccessibilitySettings(ctx)
                }
            }
        }
        item { SectionLabel("Backup & restore") }
        item {
            Card {
                NavRow("Back up layout & settings", "Saves a .json file you choose") { exportL.launch("halo-home-backup.json") }
                NavRow("Restore from backup") { importL.launch(arrayOf("application/json", "text/plain", "*/*")) }
                NavRow("Reset Home screen", "Puts default apps back; settings stay") {
                    store.updateLayout { HomeLayout() }; AppRepository.get(ctx).refresh(null)
                    Toast.makeText(ctx, "Home screen reset", Toast.LENGTH_SHORT).show()
                }
            }
        }
        item { SectionLabel("About") }
        item {
            Card {
                Column(Modifier.padding(20.dp)) {
                    Text("Halo Home 1.0.0", color = c.text, fontSize = 16.sp)
                    Text("A free launcher with an original design inspired by One UI 8. Not affiliated with or endorsed by Samsung. All icons, wallpapers and artwork are original.",
                        color = c.subText, fontSize = 13.sp)
                }
                NavRow("Show welcome screen again") { upd { it.copy(onboarded = false) }; (ctx as ComponentActivity).finish() }
            }
        }
    }
}

@Composable
fun HideApps(s: LauncherSettings, store: SettingsStore, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalHalo.current
    val apps by AppRepository.get(ctx).apps.collectAsState()
    val spec = IconSpec(s.iconShape, s.iconStyle, s.accent, c.dark, s.iconPack)
    SubPage("Hide apps", onBack) {
        item { Text("Hidden apps don't show on the Apps screen or in search. They stay installed.", color = c.subText, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
        items(apps, key = { it.key }) { app ->
            val hidden = app.key in s.hidden
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(18.dp)).background(c.card)
                .clickable { store.update { it.copy(hidden = if (hidden) it.hidden - app.key else it.hidden + app.key) } }
                .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(app, spec, 0, 38.dp)
                Spacer(Modifier.width(14.dp))
                Text(app.label, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(hidden, { v -> store.update { it.copy(hidden = if (v) it.hidden + app.key else it.hidden - app.key) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White))
            }
        }
    }
}

@Composable
fun IconPacks(s: LauncherSettings, store: SettingsStore, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalHalo.current
    val repo = AppRepository.get(ctx)
    val packs = remember { repo.iconPacks() }
    fun pick(p: String) { repo.clearPackCache(); IconCache.clear(); store.update { it.copy(iconPack = p) } }
    SubPage("Icon packs", onBack) {
        item {
            Text("Works with icon packs made for Nova, ADW and similar launchers. Install one from the Play Store, then choose it here.",
                color = c.subText, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
        }
        item {
            Card {
                Row(Modifier.fillMaxWidth().clickable { pick("") }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Default icons", color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (s.iconPack.isEmpty()) Text("✓", color = c.accent, fontSize = 18.sp)
                }
            }
        }
        items(packs, key = { it.packageName }) { p ->
            Card {
                Row(Modifier.fillMaxWidth().clickable { pick(p.packageName) }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.label, color = c.text, fontSize = 16.sp)
                        Text(p.packageName, color = c.subText, fontSize = 12.sp)
                    }
                    if (s.iconPack == p.packageName) Text("✓", color = c.accent, fontSize = 18.sp)
                }
            }
        }
        if (packs.isEmpty()) item {
            Card {
                Column(Modifier.padding(20.dp)) {
                    Text("No icon packs installed", color = c.text, fontSize = 16.sp)
                    Text("Try searching \"icon pack\" in the Play Store.", color = c.subText, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Open Play Store", color = c.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://search?q=icon%20pack&c=apps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    })
                }
            }
        }
    }
}
