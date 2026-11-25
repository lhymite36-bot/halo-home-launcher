package com.pedro.halo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

object Shapes {
    /** Superellipse "squircle" similar in spirit to modern rounded app icons (n ~ 4.2). */
    fun path(shape: IconShape, size: Float): Path {
        val p = Path()
        when (shape) {
            IconShape.CIRCLE -> p.addCircle(size / 2, size / 2, size / 2, Path.Direction.CW)
            IconShape.ROUNDED -> p.addRoundRect(RectF(0f, 0f, size, size), size * 0.22f, size * 0.22f, Path.Direction.CW)
            IconShape.SQUIRCLE -> {
                val n = 4.2
                val r = size / 2.0
                val steps = 96
                for (i in 0..steps) {
                    val t = 2 * Math.PI * i / steps
                    val c = cos(t); val s = sin(t)
                    val x = r + r * sign(c) * abs(c).pow(2.0 / n)
                    val y = r + r * sign(s) * abs(s).pow(2.0 / n)
                    if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
                }
                p.close()
            }
        }
        return p
    }
}

data class IconSpec(val shape: IconShape, val style: IconStyle, val accent: Int, val dark: Boolean, val pack: String)

/** Renders and caches app icons as bitmaps so scrolling stays smooth on low-end phones. */
object IconCache {
    private const val PX = 136 // render size; scaled down by Compose
    private val cache = object : LruCache<String, ImageBitmap>(18 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun invalidatePackage(pkg: String) {
        cache.snapshot().keys.filter { it.contains(pkg) }.forEach { cache.remove(it) }
    }

    fun clear() = cache.evictAll()

    fun get(ctx: Context, app: AppEntry, spec: IconSpec): ImageBitmap {
        val k = "${app.key}|$spec"
        cache.get(k)?.let { return it }
        val repo = AppRepository.get(ctx)
        val packDrawable = if (spec.pack.isNotEmpty()) repo.packIcon(spec.pack, app) else null
        val bmp = if (packDrawable != null && spec.style == IconStyle.ORIGINAL) {
            drawPlain(packDrawable)
        } else {
            val d = packDrawable ?: repo.rawIcon(app.key, ctx.resources.displayMetrics.densityDpi)
            render(d, spec)
        }
        val ib = bmp.asImageBitmap()
        cache.put(k, ib)
        return ib
    }

    fun renderDrawable(d: Drawable?, spec: IconSpec): ImageBitmap = render(d, spec).asImageBitmap()

    private fun drawPlain(d: Drawable): Bitmap {
        val b = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, PX, PX); d.draw(c)
        return b
    }

    private fun render(d: Drawable?, spec: IconSpec): Bitmap {
        val size = PX.toFloat()
        val b = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val clip = Shapes.path(spec.shape, size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val themedBg: Int
        val themedFg: Int
        if (spec.style == IconStyle.THEMED) {
            themedBg = if (spec.dark) blend(spec.accent, Color.BLACK, 0.72f) else blend(spec.accent, Color.WHITE, 0.82f)
            themedFg = if (spec.dark) blend(spec.accent, Color.WHITE, 0.55f) else blend(spec.accent, Color.BLACK, 0.35f)
        } else {
            themedBg = if (spec.dark) Color.rgb(38, 38, 42) else Color.rgb(245, 245, 247)
            themedFg = if (spec.dark) Color.rgb(235, 235, 240) else Color.rgb(40, 40, 46)
        }

        c.save()
        c.clipPath(clip)
        if (d == null) {
            paint.color = themedBg; c.drawPath(clip, paint)
        } else if (spec.style != IconStyle.ORIGINAL) {
            paint.color = themedBg; c.drawPath(clip, paint)
            val mono = if (Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) d.monochrome else null
            if (mono != null) {
                val inset = -size * 0.25f
                mono.setBounds(inset.toInt(), inset.toInt(), (size - inset).toInt(), (size - inset).toInt())
                mono.colorFilter = PorterDuffColorFilter(themedFg, PorterDuff.Mode.SRC_IN)
                mono.draw(c)
            } else {
                // No monochrome layer: desaturate and tint the original icon.
                val layer = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
                val lc = Canvas(layer)
                drawOriginal(lc, d, size, spec, true)
                val cm = ColorMatrix().apply { setSaturation(0f) }
                val tint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                tint.colorFilter = ColorMatrixColorFilter(cm)
                val scale = 0.62f
                val off = size * (1 - scale) / 2
                c.save(); c.translate(off, off); c.scale(scale, scale)
                c.drawBitmap(layer, 0f, 0f, tint)
                c.restore()
                // tint pass
                val over = Paint().apply { color = themedFg; alpha = 110; xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
                c.drawRect(0f, 0f, size, size, over)
                layer.recycle()
            }
        } else {
            drawOriginal(c, d, size, spec, false)
        }
        c.restore()
        return b
    }

    private fun drawOriginal(c: Canvas, d: Drawable, size: Float, spec: IconSpec, forMono: Boolean) {
        if (d is AdaptiveIconDrawable) {
            val inset = -size * 0.25f // layers are 108dp with 72dp visible: expand by 25% per side
            val l = inset.toInt(); val r = (size - inset).toInt()
            if (!forMono) d.background?.let { it.setBounds(l, l, r, r); it.draw(c) }
            d.foreground?.let { it.setBounds(l, l, r, r); it.draw(c) }
        } else {
            // Legacy icon: put it on a soft plate inside the shape, like One UI icon frames.
            if (!forMono) {
                val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (spec.dark) Color.rgb(46, 46, 52) else Color.WHITE }
                c.drawRect(0f, 0f, size, size, plate)
            }
            val pad = size * 0.16f
            d.setBounds(pad.toInt(), pad.toInt(), (size - pad).toInt(), (size - pad).toInt())
            d.draw(c)
        }
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) * (1 - t) + Color.red(b) * t).toInt()
        val g = (Color.green(a) * (1 - t) + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
        return Color.rgb(r, g, bl)
    }
}
