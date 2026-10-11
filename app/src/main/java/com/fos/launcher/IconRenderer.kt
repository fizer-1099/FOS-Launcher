package com.fos.launcher

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

class AppInfo(val info: LauncherActivityInfo) {
    val component: ComponentName = info.componentName
    val pkg: String = component.packageName
    val key: String = component.flattenToString()
    val defaultLabel: String = info.label.toString()
    val installTime: Long = info.firstInstallTime
}

data class IconStyle(
    val shape: Int,
    val radiusPct: Int,
    val legacyPlate: Boolean,
    val plateMode: Int,
    val mono: Boolean,
    val accent: Int,
    val dark: Boolean
)

/** Draws every app icon into the user's chosen shape ("rounded everything"). */
object IconRenderer {
    const val SIZE = 192

    fun render(d: Drawable, st: IconStyle): Bitmap {
        val s = SIZE
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val layer = c.saveLayer(0f, 0f, s.toFloat(), s.toFloat(), null)
        val q = (s * 0.25f).toInt()
        val base = if (st.dark) 0xFF2B2B30.toInt() else 0xFFF4F4F8.toInt()

        if (d is AdaptiveIconDrawable) {
            val mono: Drawable? = if (st.mono && Build.VERSION.SDK_INT >= 33) d.monochrome else null
            if (mono != null) {
                c.drawColor(base)
                val m = (mono.constantState?.newDrawable() ?: mono).mutate()
                m.setTint(st.accent)
                m.setBounds(-q, -q, s + q, s + q)
                m.draw(c)
            } else {
                d.background?.let {
                    it.setBounds(-q, -q, s + q, s + q)
                    it.draw(c)
                }
                d.foreground?.let {
                    it.setBounds(-q, -q, s + q, s + q)
                    it.draw(c)
                }
            }
        } else if (st.legacyPlate) {
            c.drawColor(plateColor(st))
            val pad = (s * 0.16f).toInt()
            d.setBounds(pad, pad, s - pad, s - pad)
            d.draw(c)
        } else {
            d.setBounds(0, 0, s, s)
            d.draw(c)
        }

        val mask = Paint(Paint.ANTI_ALIAS_FLAG)
        mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        c.drawPath(shapePath(st, s.toFloat()), mask)
        c.restoreToCount(layer)
        return bmp
    }

    private fun plateColor(st: IconStyle): Int = when (st.plateMode) {
        1 -> 0xFF1C1C1E.toInt()
        2 -> st.accent
        else -> if (st.dark) 0xFF2B2B30.toInt() else 0xFFF4F4F8.toInt()
    }

    private fun shapePath(st: IconStyle, s: Float): Path {
        val p = Path()
        when (st.shape) {
            1 -> { // squircle (super-ellipse)
                val r = s / 2.0
                val steps = 120
                for (i in 0..steps) {
                    val t = 2.0 * PI * i / steps
                    val ct = cos(t)
                    val sn = sin(t)
                    val x = (r + r * sign(ct) * abs(ct).pow(0.5)).toFloat()
                    val y = (r + r * sign(sn) * abs(sn).pow(0.5)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            2 -> { // teardrop
                val h = s / 2f
                val k = s * 0.10f
                p.addRoundRect(0f, 0f, s, s, floatArrayOf(h, h, h, h, k, k, h, h), Path.Direction.CW)
            }
            3 -> { // rounded hexagon
                val pts = (0 until 6).map { i ->
                    val a = Math.toRadians(-90.0 + 60.0 * i)
                    PointF((s / 2 + s / 2 * cos(a)).toFloat(), (s / 2 + s / 2 * sin(a)).toFloat())
                }
                val f = 0.2f
                for (i in 0 until 6) {
                    val pc = pts[i]
                    val pp = pts[(i + 5) % 6]
                    val pn = pts[(i + 1) % 6]
                    val ax = pc.x + (pp.x - pc.x) * f
                    val ay = pc.y + (pp.y - pc.y) * f
                    val bx = pc.x + (pn.x - pc.x) * f
                    val by = pc.y + (pn.y - pc.y) * f
                    if (i == 0) p.moveTo(ax, ay) else p.lineTo(ax, ay)
                    p.quadTo(pc.x, pc.y, bx, by)
                }
                p.close()
            }
            else -> {
                val r = s * st.radiusPct.coerceIn(0, 50) / 100f
                p.addRoundRect(RectF(0f, 0f, s, s), r, r, Path.Direction.CW)
            }
        }
        return p
    }
}
