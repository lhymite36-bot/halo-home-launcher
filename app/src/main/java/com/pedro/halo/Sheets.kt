@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.pedro.halo

import android.app.Activity
import android.app.role.RoleManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MenuAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val run: () -> Unit)

/** Bottom-anchored rounded card with scrim, spring scale-in. */
@Composable
fun FloatingCard(s: LauncherSettings, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalHalo.current
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, haloSpring(s)) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f * anim.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth()
                .graphicsLayer {
                    alpha = anim.value; translationY = (1 - anim.value) * 120f
                    val k = 0.92f + 0.08f * anim.value; scaleX = k; scaleY = k
                }
                .clip(RoundedCornerShape(30.dp)).background(c.card)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .heightIn(max = 620.dp)
                .padding(18.dp),
            content = content,
        )
    }
}

@Composable
fun ActionGrid(actions: List<MenuAction>) {
    val c = LocalHalo.current
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        for (a in actions) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(c.chip).clickable { a.run() }.padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(a.icon, null, tint = if (a.danger) Color(0xFFE5484D) else c.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(6.dp))
                Text(a.label, color = if (a.danger) Color(0xFFE5484D) else c.text, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

@Composable
fun ItemMenu(
    act: MainActivity,
    m: MenuTarget,
    s: LauncherSettings,
    layout: HomeLayout,
    appMap: Map<String, AppEntry>,
    spec: IconSpec,
    version: Int,
    onDismiss: () -> Unit,
    onMenu: (MenuTarget) -> Unit,
) {
    val ctl = act.controller
    val c = LocalHalo.current
    val ctx = LocalContext.current
    fun done(f: () -> Unit) = { f(); onDismiss() }

    var title = ""
    var app: AppEntry? = null
    val actions = ArrayList<MenuAction>()
    when (m) {
        is MenuTarget.PickFolder -> {
            FloatingCard(s, onDismiss) {
                Text("Add to folder", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                val fs = ctl.folders()
                for (f in fs) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                    ctl.addKeyToFolder(m.key, f.id, m.tileId); onDismiss()
                }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, null, tint = c.accent); Spacer(Modifier.width(12.dp))
                    Text(f.name, color = c.text, fontSize = 16.sp); Spacer(Modifier.weight(1f))
                    Text("${f.keys.size} apps", color = c.subText, fontSize = 13.sp)
                }
                if (fs.isEmpty()) Text("No folders yet. Long-press an app on the Home screen ▸ New folder.", color = c.subText)
            }
            return
        }
        is MenuTarget.Tile -> {
            val item = m.item
            when (item) {
                is HomeItem.App -> {
                    app = appMap[item.key]; title = app?.label ?: "Missing app"
                    actions += MenuAction("Remove", Icons.Rounded.RemoveCircleOutline, run = done { ctl.removeItem(item.id) })
                    actions += MenuAction("New folder", Icons.Rounded.CreateNewFolder, run = done { ctl.newFolderFrom(item.id) })
                    actions += MenuAction("Add to folder", Icons.Rounded.Folder) { onMenu(MenuTarget.PickFolder(item.key, item.id)) }
                }
                is HomeItem.Folder -> {
                    title = item.name
                    actions += MenuAction("Ungroup", Icons.Rounded.FolderOff, run = done { ctl.ungroupFolder(item.id) })
                    actions += MenuAction("Delete folder", Icons.Rounded.Delete, danger = true, run = done { ctl.removeItem(item.id) })
                }
                is HomeItem.Widget -> title = "Widget"
            }
            actions += MenuAction("Move left", Icons.Rounded.ChevronLeft, run = done { ctl.moveItem(item.id, -1) })
            actions += MenuAction("Move right", Icons.Rounded.ChevronRight, run = done { ctl.moveItem(item.id, 1) })
            if (m.inDock) actions += MenuAction("Move to Home", Icons.Rounded.Home, run = done { ctl.moveDockToHome(item.id) })
            else {
                actions += MenuAction("To next page", Icons.AutoMirrored.Rounded.LastPage, run = done { ctl.moveToPage(item.id, 1) })
                actions += MenuAction("To previous page", Icons.Rounded.FirstPage, run = done { ctl.moveToPage(item.id, -1) })
                actions += MenuAction("Move to dock", Icons.Rounded.Dock, run = done {
                    if (!ctl.moveToDock(item.id)) Toast.makeText(ctx, "Dock is full", Toast.LENGTH_SHORT).show()
                })
            }
        }
        is MenuTarget.DrawerApp -> {
            app = m.app; title = m.app.label
            val on = ctl.isOnHome(m.app.key)
            actions += MenuAction(if (on) "Add again to Home" else "Add to Home", Icons.AutoMirrored.Rounded.AddToHomeScreen, run = done {
                ctl.addToHome(m.app.key); Toast.makeText(ctx, "Added to Home screen", Toast.LENGTH_SHORT).show()
            })
            actions += MenuAction("Add to folder", Icons.Rounded.Folder) { onMenu(MenuTarget.PickFolder(m.app.key, null)) }
            actions += MenuAction("Hide app", Icons.Rounded.VisibilityOff, run = done {
                ctl.hide(m.app.key); Toast.makeText(ctx, "Hidden. Unhide in Settings ▸ Hide apps", Toast.LENGTH_SHORT).show()
            })
        }
        is MenuTarget.FolderApp -> {
            app = m.app; title = m.app.label
            actions += MenuAction("Move out to Home", Icons.AutoMirrored.Rounded.Logout, run = done { ctl.removeFromFolder(m.folderId, m.app.key, true) })
            actions += MenuAction("Remove from folder", Icons.Rounded.RemoveCircleOutline, run = done { ctl.removeFromFolder(m.folderId, m.app.key, false) })
        }
    }
    val a = app
    if (a != null) {
        actions += MenuAction("App info", Icons.Rounded.Info, run = done { ctl.repo.openAppInfo(a) })
        if (!ctl.repo.isSystemApp(a)) actions += MenuAction("Uninstall", Icons.Rounded.Delete, danger = true, run = done { ctl.repo.uninstall(a) })
    }
    val shortcuts by produceState<List<Pair<ShortcutInfo, ImageBitmap?>>>(emptyList(), a?.key) {
        value = if (a == null) emptyList() else withContext(Dispatchers.Default) {
            ctl.repo.shortcuts(a).map { sc -> sc to runCatching { ctl.repo.shortcutIcon(sc)?.toBitmap(64, 64)?.asImageBitmap() }.getOrNull() }
        }
    }

    FloatingCard(s, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (a != null) AppIconImage(a, spec, version, 44.dp)
            else Icon(Icons.Rounded.Folder, null, tint = c.accent, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        if (shortcuts.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.chip)) {
                for ((sc, ic) in shortcuts) Row(
                    Modifier.fillMaxWidth().clickable { ctl.repo.startShortcut(sc); onDismiss() }.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ic != null) Image(ic, null, Modifier.size(26.dp)) else Icon(Icons.Rounded.Bolt, null, tint = c.accent)
                    Spacer(Modifier.width(12.dp))
                    Text((sc.shortLabel ?: sc.longLabel ?: "").toString(), color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
        } else if (a != null && !ctl.repo.shortcutsAvailable()) {
            Text("App shortcuts appear here once Halo Home is your default Home app.", color = c.subText, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState())) { ActionGrid(actions) }
    }
}

@Composable
fun HomeSheets(
    act: MainActivity,
    sheet: Sheet,
    s: LauncherSettings,
    layout: HomeLayout,
    currentPage: Int,
    onSheet: (Sheet) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctl = act.controller
    val c = LocalHalo.current
    val ctx = LocalContext.current
    when (sheet) {
        Sheet.OPTIONS -> FloatingCard(s, onDismiss) {
            Text("Home screen", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            ActionGrid(listOf(
                MenuAction("Wallpapers", Icons.Rounded.Wallpaper) { onSheet(Sheet.WALLPAPER) },
                MenuAction("Widgets", Icons.Rounded.Widgets) { onSheet(Sheet.WIDGETS) },
                MenuAction("Settings", Icons.Rounded.Settings) {
                    onDismiss(); ctx.startActivity(Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                MenuAction("Add page", Icons.Rounded.AddBox) { ctl.addPage(); onDismiss() },
                MenuAction("Remove empty pages", Icons.Rounded.DeleteSweep) { ctl.removeEmptyPages(); onDismiss() },
                MenuAction(if (s.themeMode == ThemeMode.DARK) "Light mode" else "Dark mode", Icons.Rounded.DarkMode) {
                    ctl.store.update { it.copy(themeMode = if (it.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK) }
                },
            ))
        }
        Sheet.WALLPAPER -> FloatingCard(s, onDismiss) { WallpaperPicker(act, s) }
        Sheet.WIDGETS -> FloatingCard(s, onDismiss) { WidgetPicker(act, s, layout, currentPage, onDismiss) }
        Sheet.NONE -> {}
    }
}

@Composable
fun WallpaperThumb(def: WallpaperDef, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, def.id) {
        value = withContext(Dispatchers.Default) { Wallpapers.render(def, 150, 320).asImageBitmap() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
        Box(Modifier.size(84.dp, 170.dp).clip(RoundedCornerShape(18.dp))
            .then(if (selected) Modifier.border(3.dp, accent, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onClick)) {
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
        }
        Spacer(Modifier.height(6.dp))
        Text(def.name, color = LocalHalo.current.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun WallpaperPicker(act: Activity, s: LauncherSettings) {
    val c = LocalHalo.current
    val store = SettingsStore.get(act)
    Text("Wallpapers", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Text("Original Halo designs, drawn on your phone", color = c.subText, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Wallpapers.all, key = { it.id }) { def ->
            WallpaperThumb(def, s.wallpaper == def.id, c.accent) { store.update { it.copy(wallpaper = def.id) } }
        }
    }
    Spacer(Modifier.height(14.dp))
    ActionGrid(listOf(
        MenuAction("Use phone wallpaper", Icons.Rounded.Smartphone) { store.update { it.copy(wallpaper = "system") } },
        MenuAction("Pick from gallery", Icons.Rounded.Image) {
            store.update { it.copy(wallpaper = "system") }; SystemActions.openWallpaperPicker(act)
        },
        MenuAction("Also set on lock screen", Icons.Rounded.Lock) {
            val def = Wallpapers.byId(s.wallpaper)
            if (def == null) Toast.makeText(act, "Pick a Halo wallpaper first", Toast.LENGTH_SHORT).show()
            else {
                val ok = Wallpapers.applyToSystem(act, def, lock = true)
                Toast.makeText(act, if (ok) "Set on Home and Lock screen" else "Couldn't set the wallpaper", Toast.LENGTH_SHORT).show()
            }
        },
    ))
}

@Composable
fun WidgetPicker(act: MainActivity, s: LauncherSettings, layout: HomeLayout, page: Int, onDismiss: () -> Unit) {
    val c = LocalHalo.current
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val providers by produceState<List<Pair<String, List<AppWidgetProviderInfo>>>>(emptyList()) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val um = ctx.getSystemService(android.os.UserManager::class.java)
                um.userProfiles.flatMap { act.widgetManager.getInstalledProvidersForProfile(it) }
                    .groupBy { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it.provider.packageName, 0)).toString() }.getOrDefault(it.provider.packageName) }
                    .toList().sortedBy { it.first.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    val pageWidgets = layout.pages.getOrElse(page) { emptyList() }.filterIsInstance<HomeItem.Widget>()
    Text("Widgets", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Text("Tap one to add it to this page", color = c.subText, fontSize = 13.sp)
    Spacer(Modifier.height(10.dp))
    if (pageWidgets.isNotEmpty()) {
        Text("On this page", color = c.subText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        for (w in pageWidgets) {
            val label = runCatching { act.widgetManager.getAppWidgetInfo(w.widgetId)?.loadLabel(pm) }.getOrNull() ?: "Widget"
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(c.chip).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${w.rows} rows", color = c.subText, fontSize = 12.sp)
                Icon(Icons.Rounded.Remove, "Shorter", tint = c.accent, modifier = Modifier.size(36.dp).clip(CircleShape).clickable { act.controller.resizeWidget(w.id, -1) }.padding(8.dp))
                Icon(Icons.Rounded.Add, "Taller", tint = c.accent, modifier = Modifier.size(36.dp).clip(CircleShape).clickable { act.controller.resizeWidget(w.id, 1) }.padding(8.dp))
                Icon(Icons.Rounded.Delete, "Remove", tint = Color(0xFFE5484D), modifier = Modifier.size(36.dp).clip(CircleShape).clickable { act.removeWidget(w) }.padding(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        for ((appLabel, list) in providers) {
            item(key = "h_$appLabel") {
                Text(appLabel, color = c.subText, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            }
            items(list, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                val icon by produceState<ImageBitmap?>(null, info.provider) {
                    value = withContext(Dispatchers.Default) {
                        runCatching { info.loadIcon(ctx, ctx.resources.displayMetrics.densityDpi)?.toBitmap(64, 64)?.asImageBitmap() }.getOrNull()
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(c.chip)
                    .clickable { act.requestWidget(info, page); onDismiss() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    icon?.let { Image(it, null, Modifier.size(30.dp)) } ?: Icon(Icons.Rounded.Widgets, null, tint = c.accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.loadLabel(pm) ?: "Widget", color = c.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val cols = if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) info.targetCellWidth else ((info.minWidth / ctx.resources.displayMetrics.density) / 70).toInt().coerceAtLeast(1)
                        Text("$cols × ${act.rowsFor(info)}", color = c.subText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun Onboarding(act: MainActivity, s: LauncherSettings) {
    val c = LocalHalo.current
    val store = act.controller.store
    var isDefault by remember { mutableStateOf(SystemActions.isDefaultLauncher(act)) }
    var gestures by remember { mutableStateOf(SystemActions.isAccessibilityOn(act)) }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = SystemActions.isDefaultLauncher(act)
        if (!isDefault) SystemActions.openHomeSettings(act)
    }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(1500); isDefault = SystemActions.isDefaultLauncher(act); gestures = SystemActions.isAccessibilityOn(act) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(c.card).padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(72.dp).clip(composeIconShape(IconShape.SQUIRCLE))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF6A5CFF), Color(0xFF3D7BFF), Color(0xFF28C8E8)))),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(34.dp).border(4.dp, Color.White, CircleShape))
            }
            Spacer(Modifier.height(16.dp))
            Text("Welcome to Halo Home", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("A clean, One UI 8-inspired home screen for your phone.", color = c.subText, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Step(1, "Make it your Home app", if (isDefault) "Done ✓" else "Set as default", isDefault) {
                if (Build.VERSION.SDK_INT >= 29) {
                    val rm = act.getSystemService(RoleManager::class.java)
                    if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                        runCatching { role.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)) }.onFailure { SystemActions.openHomeSettings(act) }
                        return@Step
                    }
                }
                SystemActions.openHomeSettings(act)
            }
            Text("Or: Settings ▸ Apps ▸ Default apps ▸ Home app ▸ Halo Home", color = c.subText, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.clickable { SystemActions.openHomeSettings(act) }.padding(6.dp))
            Spacer(Modifier.height(8.dp))
            Step(2, "Double-tap to lock (optional)", if (gestures) "On ✓" else "Turn on", gestures) {
                store.update { it.copy(doubleTap = DoubleTapAction.LOCK) }
                SystemActions.openAccessibilitySettings(act)
            }
            Text("Accessibility ▸ Installed apps ▸ Halo Home gestures", color = c.subText, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(c.accent)
                .clickable { store.update { it.copy(onboarded = true) } }, contentAlignment = Alignment.Center) {
                Text("Get started", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text("Long-press empty space for wallpapers, widgets and settings. Swipe up for apps.", color = c.subText, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Step(n: Int, title: String, button: String, done: Boolean, onClick: () -> Unit) {
    val c = LocalHalo.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.chip).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text("$n", color = c.accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (done) c.accent.copy(alpha = 0.15f) else c.accent)
            .clickable(enabled = !done, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(button, color = if (done) c.accent else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
