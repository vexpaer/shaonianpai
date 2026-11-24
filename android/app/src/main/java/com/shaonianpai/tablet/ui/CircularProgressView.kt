package com.shaonianpai.tablet.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Authentic CircularProgressBar matching layout_customselflearnitemview2_list.xml:
 * - Track color: #603F51B5, width 10dp
 * - Foreground color: #FF3F51B5, width 7dp
 */
class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 100f)
            invalidate()
        }

    private val density = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#603F51B5")
        style = Paint.Style.STROKE
        strokeWidth = 10f * density
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3F51B5")
        style = Paint.Style.STROKE
        strokeWidth = 7f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 6f * density
        oval.set(inset, inset, width - inset, height - inset)
        canvas.drawOval(oval, trackPaint)
        val sweep = 360f * (progress / 100f)
        if (sweep > 0f) {
            canvas.drawArc(oval, -90f, sweep, false, progressPaint)
        }
    }
}
