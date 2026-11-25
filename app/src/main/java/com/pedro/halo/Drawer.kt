@file:OptIn(ExperimentalFoundationApi::class)

package com.pedro.halo

import android.content.Intent
import android.app.SearchManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.floor

@Composable
fun AppDrawer(
    act: MainActivity,
    s: LauncherSettings,
    allApps: List<AppEntry>,
    spec: IconSpec,
    version: Int,
    iconDp: Dp,
    drawer: Animatable<Float, AnimationVector1D>,
    screenH: Float,
    blurred: ImageBitmap?,
    builtInWallpaper: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (MenuTarget) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalHalo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val apps = remember(allApps, s.hidden) { allApps.filter { it.key !in s.hidden } }
    val results = remember(apps, query) {
        if (query.isBlank()) emptyList() else {
            val q = query.trim().lowercase()
            apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
                .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }
        }
    }
    val closed by remember { derivedStateOf { drawer.value <= 0.001f } }
    if (closed && query.isNotEmpty()) LaunchedEffect(Unit) { onQuery("") }
    val textColor = c.text
    val icon = (iconDp * 0.92f).coerceAtMost(60.dp)

    fun settle(v: Float) = scope.launch {
        if (drawer.value < 0.72f || v > 1200f) { focus.clearFocus(); onClose() } else drawer.animateTo(1f, haloSpring(s))
    }

    val nested = remember(s) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (drawer.value < 1f && available.y < 0) {
                    scope.launch { drawer.snapTo((drawer.value - available.y / (screenH * 0.55f)).coerceIn(0f, 1f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    scope.launch { drawer.snapTo((drawer.value - available.y / (screenH * 0.55f)).coerceIn(0f, 1f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drawer.value < 1f) { settle(available.y); return available }
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = drawer.value
                alpha = p
                translationY = if (p <= 0.001f) size.height * 2 else (1f - p) * size.height * 0.18f
            }
            .nestedScroll(nested)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy ->
                        total += dy
                        scope.launch { drawer.snapTo((1f - total / (screenH * 0.55f)).coerceIn(0f, 1f)) }
                    },
                    onDragEnd = { settle(if (total > 0) 1300f else 0f) },
                    onDragCancel = { settle(0f) },
                )
            },
    ) {
        // Frosted background
        if (s.blur && builtInWallpaper && blurred != null) {
            Image(blurred, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(c.panel.copy(alpha = if (c.dark) 0.55f else 0.45f)))
        } else {
            Box(Modifier.fillMaxSize().background(c.panel.copy(alpha = if (s.blur) 0.82f else 0.96f)))
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 14.dp)) {
            // Search pill
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(if (c.dark) Color(0x332F2F36).copy(alpha = 0.7f) else Color(0x99FFFFFF)).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.subText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search", color = c.subText, fontSize = 16.sp)
                    BasicTextField(
                        value = query, onValueChange = onQuery, singleLine = true,
                        textStyle = TextStyle(color = textColor, fontSize = 16.sp),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val first = results.firstOrNull()
                            if (first != null) onLaunch(first) else webSearch(ctx, query)
                            focus.clearFocus()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", tint = c.subText,
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onQuery("") }.padding(10.dp))
                Icon(Icons.Rounded.MoreVert, "Settings", tint = c.subText,
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable {
                        ctx.startActivity(Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.padding(10.dp))
            }
            Spacer(Modifier.height(16.dp))

            val cell: @Composable (AppEntry) -> Unit = { app ->
                IconCell(iconSize = icon, label = if (s.showDrawerLabels) app.label + if (app.isWork) " 💼" else "" else null,
                    labelColor = textColor, onWallpaper = false, haptics = s.haptics,
                    onClick = { onLaunch(app) }, onLongClick = { onMenu(MenuTarget.DrawerApp(app)) }) {
                    AppIconImage(app, spec, version, icon)
                }
            }

            if (query.isNotBlank()) {
                if (results.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No apps found", color = c.subText, fontSize = 15.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Search the web for \"$query\"", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { webSearch(ctx, query) }.padding(12.dp))
                    }
                } else LazyVerticalGrid(GridCells.Fixed(s.drawerCols), Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(results, key = { it.key }) { cell(it) }
                }
            } else if (s.drawerStyle == DrawerStyle.LIST) {
                LazyVerticalGrid(GridCells.Fixed(s.drawerCols), Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(apps, key = { it.key }) { cell(it) }
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val cellH = icon.value + (if (s.showDrawerLabels) 34f else 14f)
                    val rows = floor((maxHeight.value - 30f) / cellH).toInt().coerceIn(3, 7)
                    val perPage = rows * s.drawerCols
                    val pages = remember(apps, perPage) { apps.chunked(perPage).ifEmpty { listOf(emptyList()) } }
                    val pstate = rememberPagerState { pages.size }
                    Column(Modifier.fillMaxSize()) {
                        HorizontalPager(pstate, Modifier.weight(1f), beyondViewportPageCount = 0) { pi ->
                            val list = pages[pi]
                            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                                for (r in 0 until rows) Row(Modifier.fillMaxWidth().height(cellH.dp), verticalAlignment = Alignment.CenterVertically) {
                                    for (col in 0 until s.drawerCols) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        list.getOrNull(r * s.drawerCols + col)?.let { cell(it) }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            for (i in pages.indices) {
                                val sel = pstate.currentPage == i
                                Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (sel) 16.dp else 6.dp).clip(CircleShape)
                                    .background(if (sel) c.accent else c.subText.copy(alpha = 0.4f)))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun webSearch(ctx: android.content.Context, q: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(q)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
fun FolderPopup(
    folder: HomeItem.Folder,
    s: LauncherSettings,
    appMap: Map<String, AppEntry>,
    spec: IconSpec,
    version: Int,
    iconDp: Dp,
    onDismiss: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (MenuTarget) -> Unit,
    onRename: (String) -> Unit,
) {
    val c = LocalHalo.current
    val anim = remember { Animatable(0f) }
    LaunchedEffect(folder.id) { anim.animateTo(1f, haloSpring(s)) }
    var editing by remember { mutableStateOf(false) }
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val apps = folder.keys.mapNotNull { appMap[it] }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f * anim.value))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (editing) { onRename(name); editing = false }
                onDismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.88f)
                .graphicsLayer { val k = 0.85f + 0.15f * anim.value; scaleX = k; scaleY = k; alpha = anim.value }
                .clip(RoundedCornerShape(34.dp)).background(c.card.copy(alpha = 0.94f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
        ) {
            if (editing) {
                BasicTextField(name, { name = it }, singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRename(name); editing = false }),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.chip).padding(12.dp))
            } else {
                Text(folder.name, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { editing = true }.padding(horizontal = 6.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
            val cols = 4
            for (row in apps.chunked(cols)) {
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until cols) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        row.getOrNull(i)?.let { app ->
                            IconCell(iconSize = iconDp.coerceAtMost(56.dp), label = app.label, labelColor = c.text, onWallpaper = false,
                                haptics = s.haptics, onClick = { onLaunch(app) },
                                onLongClick = { onMenu(MenuTarget.FolderApp(folder.id, app)) }) {
                                AppIconImage(app, spec, version, iconDp.coerceAtMost(56.dp))
                            }
                        }
                    }
                }
            }
            if (apps.isEmpty()) Text("This folder is empty", color = c.subText)
            Spacer(Modifier.height(6.dp))
            Text("Tip: long-press any app ▸ Add to folder", color = c.subText, fontSize = 12.sp)
        }
    }
}
