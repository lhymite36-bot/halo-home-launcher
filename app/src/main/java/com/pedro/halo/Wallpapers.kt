package com.pedro.halo

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build

/** Original, procedurally generated wallpapers (soft glassy gradients with flowing light blobs). */
data class Blob(val x: Float, val y: Float, val r: Float, val color: Long)
data class WallpaperDef(val id: String, val name: String, val top: Long, val bottom: Long, val blobs: List<Blob>, val dark: Boolean = true)

object Wallpapers {
    val all = listOf(
        WallpaperDef("aurora", "Aurora Glass", 0xFF0B1030, 0xFF1B2A6B, listOf(
            Blob(0.15f, 0.25f, 0.65f, 0xCC3D7BFF), Blob(0.85f, 0.45f, 0.6f, 0xAA8A5CFF),
            Blob(0.4f, 0.85f, 0.7f, 0x9928C8E8), Blob(0.9f, 0.05f, 0.4f, 0x66FF7AD9))),
        WallpaperDef("lavender", "Lavender Bloom", 0xFF2A1640, 0xFF6B4BA8, listOf(
            Blob(0.2f, 0.2f, 0.6f, 0xBBC58BFF), Blob(0.8f, 0.6f, 0.65f, 0xAAFF8FC8),
            Blob(0.3f, 0.9f, 0.55f, 0x887A6BFF))),
        WallpaperDef("mint", "Mint Flow", 0xFF06302B, 0xFF0F6B5C, listOf(
            Blob(0.8f, 0.2f, 0.6f, 0xAA4DF2C3), Blob(0.2f, 0.55f, 0.7f, 0x9926B0FF),
            Blob(0.7f, 0.95f, 0.6f, 0x88B6FF6B))),
        WallpaperDef("sunset", "Sunset Silk", 0xFF3A0F2E, 0xFFFF8A5C, listOf(
            Blob(0.2f, 0.3f, 0.6f, 0xAAFF5C8A), Blob(0.85f, 0.55f, 0.6f, 0xAAFFB35C),
            Blob(0.4f, 0.95f, 0.6f, 0x88FFE08A))),
        WallpaperDef("graphite", "Graphite", 0xFF0A0A0C, 0xFF24252B, listOf(
            Blob(0.75f, 0.25f, 0.55f, 0x553D7BFF), Blob(0.2f, 0.75f, 0.6f, 0x44A0A4B8))),
        WallpaperDef("coral", "Coral Dune", 0xFF3B1612, 0xFFE0705A, listOf(
            Blob(0.1f, 0.6f, 0.7f, 0x99FF9F7A), Blob(0.9f, 0.3f, 0.5f, 0x88FFD3A8),
            Blob(0.5f, 0.05f, 0.45f, 0x66B05CFF))),
        WallpaperDef("midnight", "Midnight Orbs", 0xFF000000, 0xFF0B1024, listOf(
            Blob(0.3f, 0.3f, 0.35f, 0xAA2F6BFF), Blob(0.75f, 0.55f, 0.3f, 0xAAB04DFF),
            Blob(0.45f, 0.85f, 0.32f, 0x9900D1C1))),
        WallpaperDef("ice", "Ice Light", 0xFFDDE7F7, 0xFF9DB7E8, listOf(
            Blob(0.2f, 0.25f, 0.6f, 0xAAFFFFFF), Blob(0.85f, 0.6f, 0.6f, 0x887FA8FF),
            Blob(0.35f, 0.95f, 0.6f, 0x66C4B5FF)), dark = false),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    fun render(def: WallpaperDef, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        p.shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(), def.top.toInt(), def.bottom.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val dim = maxOf(w, h).toFloat()
        for (bl in def.blobs) {
            val cx = bl.x * w; val cy = bl.y * h; val r = bl.r * dim * 0.6f
            val col = bl.color.toInt()
            p.shader = RadialGradient(cx, cy, r, intArrayOf(col, col and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
        }
        // Subtle glass ribbon highlight.
        p.shader = LinearGradient(0f, h * 0.35f, w.toFloat(), h * 0.65f,
            intArrayOf(0x00FFFFFF, 0x22FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        p.maskFilter = BlurMaskFilter(dim * 0.02f, BlurMaskFilter.Blur.NORMAL)
        val path = android.graphics.Path().apply {
            moveTo(-w * 0.1f, h * 0.62f)
            cubicTo(w * 0.3f, h * 0.42f, w * 0.6f, h * 0.72f, w * 1.1f, h * 0.38f)
            lineTo(w * 1.1f, h * 0.46f)
            cubicTo(w * 0.6f, h * 0.8f, w * 0.3f, h * 0.5f, -w * 0.1f, h * 0.7f)
            close()
        }
        c.drawPath(path, p)
        return b
    }

    /** Applies a built-in wallpaper to the system home (and optionally lock) screen. */
    fun applyToSystem(ctx: Context, def: WallpaperDef, lock: Boolean): Boolean = runCatching {
        val dm = ctx.resources.displayMetrics
        val w = minOf(dm.widthPixels, 1080); val h = (w.toFloat() * dm.heightPixels / dm.widthPixels).toInt()
        val bmp = render(def, w, h)
        val wm = WallpaperManager.getInstance(ctx)
        val flags = if (lock) WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
        if (Build.VERSION.SDK_INT >= 24) wm.setBitmap(bmp, null, true, flags) else wm.setBitmap(bmp)
        bmp.recycle()
        true
    }.getOrDefault(false)
}
