package com.pedro.halo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Telephony

sealed class RowSlot {
    data class WidgetRow(val item: HomeItem.Widget) : RowSlot()
    data class AppRow(val items: List<HomeItem>) : RowSlot()
}

fun headerRows(s: LauncherSettings): Int = when (s.clockStyle) {
    ClockStyle.BIG, ClockStyle.STACKED -> 2
    ClockStyle.COMPACT -> 1
    ClockStyle.HIDDEN -> 0
}

fun availableRows(s: LauncherSettings, page: Int): Int = s.gridRows - if (page == 0) headerRows(s) else 0

/** Flows items into rows: widgets take full-width rows, apps/folders fill [cols] per row. */
fun layoutRows(items: List<HomeItem>, cols: Int): List<RowSlot> {
    val out = ArrayList<RowSlot>()
    var cur = ArrayList<HomeItem>()
    for (it in items) {
        if (it is HomeItem.Widget) {
            if (cur.isNotEmpty()) { out += RowSlot.AppRow(cur); cur = ArrayList() }
            out += RowSlot.WidgetRow(it)
        } else {
            cur += it
            if (cur.size == cols) { out += RowSlot.AppRow(cur); cur = ArrayList() }
        }
    }
    if (cur.isNotEmpty()) out += RowSlot.AppRow(cur)
    return out
}

fun rowsUsed(items: List<HomeItem>, cols: Int): Int =
    layoutRows(items, cols).sumOf { if (it is RowSlot.WidgetRow) it.item.rows else 1 }

class LauncherController(private val ctx: Context) {
    val store = SettingsStore.get(ctx)
    val repo = AppRepository.get(ctx)

    private val s get() = store.settings.value

    private fun fits(page: Int, items: List<HomeItem>) = rowsUsed(items, s.gridCols) <= availableRows(s, page)

    private fun place(l: HomeLayout, item: HomeItem): HomeLayout {
        val pages = l.pages.toMutableList()
        for (i in pages.indices) {
            val cand = pages[i] + item
            if (fits(i, cand)) { pages[i] = cand; return l.copy(pages = pages) }
        }
        pages += listOf(item)
        return l.copy(pages = pages)
    }

    fun isOnHome(key: String): Boolean = store.layout.value.allItems().any {
        (it is HomeItem.App && it.key == key) || (it is HomeItem.Folder && key in it.keys)
    }

    fun addToHome(key: String) = store.updateLayout { place(it, HomeItem.App(store.nextId(), key)) }

    fun addToHomePage(key: String, page: Int) = store.updateLayout { l ->
        val pages = l.pages.toMutableList()
        val item = HomeItem.App(store.nextId(), key)
        if (page in pages.indices && fits(page, pages[page] + item)) { pages[page] = pages[page] + item; l.copy(pages = pages) }
        else place(l, item)
    }

    private fun mapAll(l: HomeLayout, f: (List<HomeItem>) -> List<HomeItem>) =
        l.copy(pages = l.pages.map(f), dock = f(l.dock))

    fun removeItem(id: Long) = store.updateLayout { l ->
        mapAll(l) { list -> list.filter { it.id != id } }
    }

    fun find(id: Long): HomeItem? = store.layout.value.allItems().firstOrNull { it.id == id }

    /** Move an item one slot earlier/later on its page or in the dock. */
    fun moveItem(id: Long, delta: Int) = store.updateLayout { l ->
        mapAll(l) { list ->
            val i = list.indexOfFirst { it.id == id }
            val j = i + delta
            if (i < 0 || j !in list.indices) list else list.toMutableList().also { m -> val t = m[i]; m[i] = m[j]; m[j] = t }
        }
    }

    fun moveToPage(id: Long, dir: Int) = store.updateLayout { l ->
        val item = l.allItems().firstOrNull { it.id == id } ?: return@updateLayout l
        val from = l.pages.indexOfFirst { p -> p.any { it.id == id } }
        if (from < 0) return@updateLayout l
        val to = from + dir
        if (to < 0) return@updateLayout l
        val pages = l.pages.map { p -> p.filter { it.id != id } }.toMutableList()
        while (pages.size <= to) pages += listOf(emptyList<HomeItem>())
        if (fits(to, pages[to] + item)) pages[to] = pages[to] + item else return@updateLayout l
        l.copy(pages = pages)
    }

    fun moveToDock(id: Long): Boolean {
        val l = store.layout.value
        if (l.dock.size >= s.dockCount) return false
        val item = find(id) ?: return false
        if (item is HomeItem.Widget) return false
        store.updateLayout { it.copy(pages = it.pages.map { p -> p.filter { x -> x.id != id } }, dock = it.dock + item) }
        return true
    }

    fun moveDockToHome(id: Long) = store.updateLayout { l ->
        val item = l.dock.firstOrNull { it.id == id } ?: return@updateLayout l
        place(l.copy(dock = l.dock.filter { it.id != id }), item)
    }

    fun folders(): List<HomeItem.Folder> = store.layout.value.allItems().filterIsInstance<HomeItem.Folder>()

    /** Turn an app tile into a new folder containing it. */
    fun newFolderFrom(id: Long, name: String = "Folder") = store.updateLayout { l ->
        mapAll(l) { list -> list.map { if (it.id == id && it is HomeItem.App) HomeItem.Folder(it.id, name, listOf(it.key)) else it } }
    }

    fun addKeyToFolder(key: String, folderId: Long, removeTileId: Long?) = store.updateLayout { l ->
        mapAll(l) { list ->
            list.filter { removeTileId == null || it.id != removeTileId }
                .map { if (it is HomeItem.Folder && it.id == folderId && key !in it.keys) it.copy(keys = it.keys + key) else it }
        }
    }

    fun removeFromFolder(folderId: Long, key: String, keepOnHome: Boolean) {
        store.updateLayout { l ->
            mapAll(l) { list ->
                list.mapNotNull {
                    if (it is HomeItem.Folder && it.id == folderId) {
                        val rest = it.keys - key
                        when {
                            rest.isEmpty() -> null
                            rest.size == 1 -> HomeItem.App(it.id, rest[0])
                            else -> it.copy(keys = rest)
                        }
                    } else it
                }
            }
        }
        if (keepOnHome) addToHome(key)
    }

    fun renameFolder(folderId: Long, name: String) = store.updateLayout { l ->
        mapAll(l) { list -> list.map { if (it is HomeItem.Folder && it.id == folderId) it.copy(name = name.ifBlank { "Folder" }) else it } }
    }

    fun ungroupFolder(folderId: Long) {
        val f = find(folderId) as? HomeItem.Folder ?: return
        removeItem(folderId)
        f.keys.forEach { addToHome(it) }
    }

    fun hide(key: String) {
        store.update { it.copy(hidden = it.hidden + key) }
    }

    fun addWidget(widgetId: Int, rows: Int, page: Int) = store.updateLayout { l ->
        val item = HomeItem.Widget(store.nextId(), widgetId, rows.coerceIn(1, 5))
        val pages = l.pages.toMutableList()
        val p = page.coerceIn(0, pages.size - 1)
        // Widgets go to the top of the page; push overflow apps onto a new page.
        val cand = listOf<HomeItem>(item) + pages[p]
        if (fits(p, cand)) { pages[p] = cand; return@updateLayout l.copy(pages = pages) }
        val keep = ArrayList<HomeItem>(); keep += item
        val overflow = ArrayList<HomeItem>()
        for (x in pages[p]) if (fits(p, keep + x)) keep += x else overflow += x
        pages[p] = keep
        var out = l.copy(pages = pages)
        overflow.forEach { out = place(out, it) }
        out
    }

    fun resizeWidget(id: Long, delta: Int) = store.updateLayout { l ->
        l.copy(pages = l.pages.map { p -> p.map { if (it is HomeItem.Widget && it.id == id) it.copy(rows = (it.rows + delta).coerceIn(1, 5)) else it } })
    }

    fun addPage() = store.updateLayout { it.copy(pages = it.pages + listOf(emptyList())) }

    fun removeEmptyPages() = store.updateLayout { l ->
        val kept = l.pages.filterIndexed { i, p -> i == 0 || p.isNotEmpty() }
        l.copy(pages = kept)
    }

    /** Re-flow every page after the grid size changes so nothing is clipped. */
    fun reflow() = store.updateLayout { l ->
        var out = l.copy(pages = listOf(emptyList()))
        val all = l.pages.flatten()
        val pages = ArrayList<List<HomeItem>>()
        var cur = ArrayList<HomeItem>()
        for (it in all) {
            val idx = pages.size
            if (fits(idx, cur + it)) cur += it else { pages += cur.toList(); cur = arrayListOf(it) }
        }
        pages += cur.toList()
        out = out.copy(pages = pages.ifEmpty { listOf(emptyList()) }, dock = l.dock.take(s.dockCount + 2))
        out
    }

    /** First run: put sensible apps on the dock and first page (only apps that exist). */
    fun seedIfNeeded(apps: List<AppEntry>) {
        if (store.layout.value.seeded || apps.isEmpty()) return
        val pm = ctx.packageManager
        fun pkgFor(i: Intent): String? = runCatching { pm.resolveActivity(i, 0)?.activityInfo?.packageName }.getOrNull()
            ?.takeIf { it != "android" }
        fun keyFor(pkg: String?): String? = pkg?.let { p -> apps.firstOrNull { it.packageName == p && !it.isWork }?.key }

        val dockPk = listOfNotNull(
            pkgFor(Intent(Intent.ACTION_DIAL)),
            runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull(),
            pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            pkgFor(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        ).distinct()
        val dock = dockPk.mapNotNull { keyFor(it) }.distinct().take(s.dockCount)

        val wanted = listOf(
            "com.sec.android.gallery3d", "com.google.android.apps.photos", "com.android.vending",
            "com.whatsapp", "com.google.android.youtube", "com.google.android.gm", "com.google.android.apps.maps",
            "com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.settings",
            "com.sec.android.app.myfiles", "com.instagram.android", "com.spotify.music", "com.sec.android.app.popupcalculator",
        )
        val page = wanted.mapNotNull { keyFor(it) }.filter { it !in dock }.distinct().take(s.gridCols * 2)

        val googlePk = listOf("com.google.android.googlequicksearchbox", "com.google.android.apps.docs",
            "com.google.android.apps.meetings", "com.google.android.calendar", "com.google.android.keep",
            "com.google.android.apps.tachyon", "com.google.android.apps.youtube.music", "com.google.android.apps.messaging")
        val google = googlePk.mapNotNull { keyFor(it) }.filter { it !in dock && it !in page }

        val items = ArrayList<HomeItem>()
        page.forEach { items += HomeItem.App(store.nextId(), it) }
        if (google.size >= 2) items += HomeItem.Folder(store.nextId(), "Google", google)
        store.updateLayout {
            HomeLayout(pages = listOf(items), dock = dock.map { HomeItem.App(store.nextId(), it) }, seeded = true)
        }
    }

    fun spec(dark: Boolean): IconSpec = IconSpec(s.iconShape, s.iconStyle, s.accent, dark, s.iconPack)
}
