package com.spectrum.v2

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class YinYangView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var lightColor = Color.WHITE
    private var darkColor  = Color.BLACK
    private var ringColor  = 0xFF0099CC.toInt() // Biru BBM default

    private val paintLight = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintDark  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintRing  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintBorder= Paint(Paint.ANTI_ALIAS_FLAG)

    fun setColors(light: Int, dark: Int, accent: Int) {
        lightColor = light; darkColor = dark; ringColor = accent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val r = minOf(w, h) / 2f - 2f
        val half = r / 2f
        val small = r / 5f

        paintLight.color  = lightColor
        paintDark.color   = darkColor
        paintRing.style   = Paint.Style.STROKE
        paintRing.strokeWidth = r * 0.07f
        paintRing.color   = ringColor
        paintBorder.color = ringColor
        paintBorder.style = Paint.Style.STROKE
        paintBorder.strokeWidth = r * 0.05f

        // Setengah lingkaran kiri (terang)
        paintLight.style = Paint.Style.FILL
        canvas.drawArc(cx-r, cy-r, cx+r, cy+r, 90f, 180f, true, paintLight)

        // Setengah lingkaran kanan (gelap)
        paintDark.style = Paint.Style.FILL
        canvas.drawArc(cx-r, cy-r, cx+r, cy+r, 270f, 180f, true, paintDark)

        // Setengah atas kecil terang
        canvas.drawCircle(cx, cy - half, half, paintLight)
        // Setengah bawah kecil gelap
        canvas.drawCircle(cx, cy + half, half, paintDark)

        // Titik kecil kontras
        paintDark.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy - half, small, paintDark)
        paintLight.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy + half, small, paintLight)

        // Ring luar biru BBM
        canvas.drawCircle(cx, cy, r, paintRing)

        // Border tipis luar
        canvas.drawCircle(cx, cy, r + paintBorder.strokeWidth/2, paintBorder)
    }
}
