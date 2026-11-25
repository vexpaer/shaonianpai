package com.shaonianpai.tablet.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.shaonianpai.tablet.R
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class PdfAnnotCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var currentPageIndex: Int = 0
    var activeTool: AnnotTool = AnnotTool.BROWSE
    var activeColor: Int = FoxitPalette.defaultColorFor(AnnotTool.HIGHLIGHT)
    var activeAlpha: Int = 100
    var activeStrokeWidth: Float = 4f
    var nightMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var pageTextIndex: PageTextIndex? = null
    var searchHighlightRects: List<RectNorm> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var onAnnotationCreated: ((PdfAnnotation) -> Unit)? = null
    var onAnnotationTapped: ((PdfAnnotation) -> Unit)? = null
    var onAnnotationErased: ((PdfAnnotation) -> Unit)? = null
    var onPlaceTextAnnotation: ((AnnotTool, PointNorm, PointNorm) -> Unit)? = null
    var onToggleChrome: (() -> Unit)? = null
    var onSwipePage: ((Int) -> Unit)? = null // -1 prev, +1 next

    private var pageBitmap: Bitmap? = null
    private var annotations: List<PdfAnnotation> = emptyList()
    private var draftAnnotation: PdfAnnotation? = null
    private var draftDragStart: PointNorm? = null

    private var zoomScale: Float = 1f
    private var panX: Float = 0f
    private var panY: Float = 0f
    private var isMultiTouch: Boolean = false

    private val pageRect = RectF()
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val nightPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        // Gentle warm eye-care inversion matching Foxit night mode
        val cm = ColorMatrix(
            floatArrayOf(
                -0.86f, 0f, 0f, 0f, 232f,
                0f, -0.86f, 0f, 0f, 232f,
                0f, 0f, -0.82f, 0f, 240f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        colorFilter = ColorMatrixColorFilter(cm)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(12f, 0f, 3f, Color.parseColor("#38000000"))
    }
    private val searchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#77FF6D00")
    }
    private val searchBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FF6D00")
        strokeWidth = 2.5f
    }

    private val notePinBitmap: Bitmap? by lazy {
        runCatching { BitmapFactory.decodeResource(resources, R.drawable.fx_note_pin) }.getOrNull()
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val prev = zoomScale
                zoomScale = (zoomScale * detector.scaleFactor).coerceIn(1f, 4f)
                if (zoomScale <= 1.01f) {
                    panX = 0f
                    panY = 0f
                } else {
                    val ratio = zoomScale / prev
                    panX = (panX - (detector.focusX - width * 0.5f)) * ratio + (detector.focusX - width * 0.5f)
                    panY = (panY - (detector.focusY - height * 0.5f)) * ratio + (detector.focusY - height * 0.5f)
                    clampPan()
                }
                invalidate()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (activeTool != AnnotTool.BROWSE) return false
                val pt = screenToPageNorm(e.x, e.y)
                if (pt != null) {
                    val hit = findAnnotationAt(pt.x, pt.y)
                    if (hit != null) {
                        onAnnotationTapped?.invoke(hit)
                        return true
                    }
                }
                onToggleChrome?.invoke()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (activeTool != AnnotTool.BROWSE) return false
                if (zoomScale > 1.25f) {
                    resetZoom()
                } else {
                    zoomScale = 2.15f
                    panX = (width * 0.5f - e.x) * 1.15f
                    panY = (height * 0.5f - e.y) * 1.15f
                    clampPan()
                    invalidate()
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (activeTool != AnnotTool.BROWSE && !isMultiTouch) return false
                if (zoomScale > 1.02f) {
                    panX -= distanceX
                    panY -= distanceY
                    clampPan()
                    invalidate()
                    return true
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (activeTool != AnnotTool.BROWSE || zoomScale > 1.05f || e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > abs(dy) * 1.5f && abs(dx) > 90f && abs(velocityX) > 350f) {
                    onSwipePage?.invoke(if (dx < 0) 1 else -1)
                    return true
                }
                return false
            }
        }
    )

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setPageContent(pageIndex: Int, bitmap: Bitmap?, pageAnnots: List<PdfAnnotation>) {
        currentPageIndex = pageIndex
        pageBitmap = bitmap
        annotations = pageAnnots.toList()
        draftAnnotation = null
        draftDragStart = null
        invalidate()
    }

    fun updateAnnotations(pageAnnots: List<PdfAnnotation>) {
        annotations = pageAnnots.toList()
        invalidate()
    }

    fun resetZoom() {
        zoomScale = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }

    fun getZoomPercent(): Int = (zoomScale * 100).toInt()

    private fun computePageRect() {
        val bmp = pageBitmap ?: return
        if (width <= 0 || height <= 0) return
        val pad = 16f
        val availW = (width - pad * 2).coerceAtLeast(100f)
        val availH = (height - pad * 2).coerceAtLeast(100f)
        val bmpAspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
        val viewAspect = availW / availH
        val baseW: Float
        val baseH: Float
        if (bmpAspect > viewAspect) {
            baseW = availW
            baseH = availW / bmpAspect
        } else {
            baseH = availH
            baseW = availH * bmpAspect
        }
        val scaledW = baseW * zoomScale
        val scaledH = baseH * zoomScale
        val cx = width * 0.5f + panX
        val cy = height * 0.5f + panY
        pageRect.set(cx - scaledW * 0.5f, cy - scaledH * 0.5f, cx + scaledW * 0.5f, cy + scaledH * 0.5f)
    }

    private fun clampPan() {
        val maxPanX = max(0f, (width * (zoomScale - 1f)) * 0.6f)
        val maxPanY = max(0f, (height * (zoomScale - 1f)) * 0.6f)
        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
    }

    private fun screenToPageNorm(sx: Float, sy: Float): PointNorm? {
        computePageRect()
        if (pageRect.width() <= 1f || pageRect.height() <= 1f) return null
        if (!pageRect.contains(sx, sy)) {
            // Allow clamping slightly outside page edge when dragging
            val nx = ((sx - pageRect.left) / pageRect.width()).coerceIn(0f, 1f)
            val ny = ((sy - pageRect.top) / pageRect.height()).coerceIn(0f, 1f)
            return if (sx >= pageRect.left - 36f && sx <= pageRect.right + 36f &&
                sy >= pageRect.top - 36f && sy <= pageRect.bottom + 36f
            ) PointNorm(nx, ny) else null
        }
        return PointNorm(
            ((sx - pageRect.left) / pageRect.width()).coerceIn(0f, 1f),
            ((sy - pageRect.top) / pageRect.height()).coerceIn(0f, 1f)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(if (nightMode) Color.parseColor("#212139") else Color.parseColor("#E1E1E1"))
        val bmp = pageBitmap ?: return
        computePageRect()

        // Page card shadow
        if (!nightMode) {
            canvas.drawRect(pageRect, shadowPaint)
        }
        canvas.drawBitmap(bmp, null, pageRect, if (nightMode) nightPaint else pagePaint)

        // Draw search match highlights
        if (searchHighlightRects.isNotEmpty()) {
            for (r in searchHighlightRects) {
                val nr = r.normalized()
                val rf = RectF(
                    pageRect.left + nr.left * pageRect.width(),
                    pageRect.top + nr.top * pageRect.height(),
                    pageRect.left + nr.right * pageRect.width(),
                    pageRect.top + nr.bottom * pageRect.height()
                )
                canvas.drawRoundRect(rf, 4f, 4f, searchPaint)
                canvas.drawRoundRect(rf, 4f, 4f, searchBorderPaint)
            }
        }

        // Draw committed + live draft annotations in page coordinate space
        canvas.save()
        canvas.clipRect(pageRect)
        canvas.translate(pageRect.left, pageRect.top)
        PdfAnnotRenderer.drawAnnotations(canvas, annotations, pageRect.width(), pageRect.height(), notePinBitmap)
        draftAnnotation?.let {
            PdfAnnotRenderer.drawSingle(canvas, it, pageRect.width(), pageRect.height(), notePinBitmap)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1) {
            isMultiTouch = true
            draftAnnotation = null
            draftDragStart = null
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (isMultiTouch) {
                if (event.pointerCount <= 1) isMultiTouch = false
                invalidate()
                return true
            }
        }

        if (activeTool == AnnotTool.BROWSE || isMultiTouch) {
            gestureDetector.onTouchEvent(event)
            return true
        }

        val pt = screenToPageNorm(event.x, event.y) ?: return true

        when (activeTool) {
            AnnotTool.ERASER -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val hit = findAnnotationAt(pt.x, pt.y)
                    if (hit != null) {
                        onAnnotationErased?.invoke(hit)
                    }
                }
            }
            AnnotTool.NOTE, AnnotTool.TYPEWRITER, AnnotTool.TEXTBOX, AnnotTool.STAMP -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    onPlaceTextAnnotation?.invoke(activeTool, pt, pt)
                }
            }
            AnnotTool.CALLOUT -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        draftDragStart = pt
                        draftAnnotation = PdfAnnotation(
                            pageIndex = currentPageIndex,
                            type = AnnotTool.CALLOUT,
                            color = activeColor,
                            alpha = activeAlpha,
                            strokeWidth = activeStrokeWidth,
                            points = mutableListOf(pt, PointNorm((pt.x + 0.08f).coerceAtMost(0.85f), (pt.y - 0.05f).coerceAtLeast(0.05f))),
                            text = "标注说明..."
                        )
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val s = draftDragStart ?: return true
                        draftAnnotation?.let {
                            it.points.clear()
                            it.points.add(s)
                            it.points.add(pt)
                        }
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        val s = draftDragStart ?: pt
                        val dist = hypot((pt.x - s.x).toDouble(), (pt.y - s.y).toDouble())
                        val boxPt = if (dist < 0.03) {
                            PointNorm((s.x + 0.08f).coerceAtMost(0.75f), (s.y - 0.06f).coerceAtLeast(0.05f))
                        } else pt
                        draftAnnotation = null
                        draftDragStart = null
                        invalidate()
                        onPlaceTextAnnotation?.invoke(AnnotTool.CALLOUT, s, boxPt)
                    }
                }
            }
            AnnotTool.PENCIL -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        draftAnnotation = PdfAnnotation(
                            pageIndex = currentPageIndex,
                            type = AnnotTool.PENCIL,
                            color = activeColor,
                            alpha = activeAlpha,
                            strokeWidth = activeStrokeWidth,
                            points = mutableListOf(pt)
                        )
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        draftAnnotation?.points?.let { pts ->
                            val last = pts.lastOrNull()
                            if (last == null || hypot((pt.x - last.x).toDouble(), (pt.y - last.y).toDouble()) > 0.003) {
                                pts.add(pt)
                                invalidate()
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val a = draftAnnotation
                        draftAnnotation = null
                        if (a != null) {
                            if (a.points.size == 1) {
                                a.points.add(PointNorm((pt.x + 0.002f).coerceAtMost(1f), pt.y))
                            }
                            onAnnotationCreated?.invoke(a)
                        }
                        invalidate()
                    }
                }
            }
            AnnotTool.LINE, AnnotTool.ARROW -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        draftDragStart = pt
                        draftAnnotation = PdfAnnotation(
                            pageIndex = currentPageIndex,
                            type = activeTool,
                            color = activeColor,
                            alpha = activeAlpha,
                            strokeWidth = activeStrokeWidth,
                            points = mutableListOf(pt, pt)
                        )
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val s = draftDragStart ?: return true
                        draftAnnotation?.points?.apply {
                            clear(); add(s); add(pt)
                        }
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        val a = draftAnnotation
                        draftAnnotation = null
                        draftDragStart = null
                        if (a != null && a.points.size >= 2) {
                            val s = a.points.first(); val e = a.points.last()
                            if (hypot((e.x - s.x).toDouble(), (e.y - s.y).toDouble()) > 0.012) {
                                onAnnotationCreated?.invoke(a)
                            }
                        }
                        invalidate()
                    }
                }
            }
            AnnotTool.RECT, AnnotTool.OVAL -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        draftDragStart = pt
                        draftAnnotation = PdfAnnotation(
                            pageIndex = currentPageIndex,
                            type = activeTool,
                            color = activeColor,
                            alpha = activeAlpha,
                            strokeWidth = activeStrokeWidth,
                            rects = mutableListOf(RectNorm(pt.x, pt.y, pt.x, pt.y))
                        )
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val s = draftDragStart ?: return true
                        draftAnnotation?.rects?.apply {
                            clear(); add(RectNorm(s.x, s.y, pt.x, pt.y).normalized())
                        }
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        val a = draftAnnotation
                        draftAnnotation = null
                        draftDragStart = null
                        val r = a?.rects?.firstOrNull()?.normalized()
                        if (a != null && r != null && (r.right - r.left > 0.012f || r.bottom - r.top > 0.012f)) {
                            onAnnotationCreated?.invoke(a)
                        }
                        invalidate()
                    }
                }
            }
            AnnotTool.HIGHLIGHT, AnnotTool.UNDERLINE, AnnotTool.SQUIGGLY, AnnotTool.STRIKEOUT -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        draftDragStart = pt
                        draftAnnotation = PdfAnnotation(
                            pageIndex = currentPageIndex,
                            type = activeTool,
                            color = activeColor,
                            alpha = if (activeTool == AnnotTool.HIGHLIGHT) min(activeAlpha, 65) else activeAlpha,
                            strokeWidth = activeStrokeWidth,
                            rects = mutableListOf(RectNorm(pt.x, (pt.y - 0.012f).coerceAtLeast(0f), pt.x, (pt.y + 0.012f).coerceAtMost(1f)))
                        )
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val s = draftDragStart ?: return true
                        val dragBox = RectNorm(
                            min(s.x, pt.x),
                            min(s.y, pt.y) - 0.008f,
                            max(s.x, pt.x),
                            max(s.y, pt.y) + 0.008f
                        ).normalized()
                        val snapped = pageTextIndex?.selectByDrag(dragBox)
                        draftAnnotation?.let { a ->
                            a.rects.clear()
                            if (snapped != null && snapped.first.isNotEmpty()) {
                                a.rects.addAll(snapped.first)
                                a.text = snapped.second
                            } else {
                                // Smooth fallback strip for scanned PDFs or blank areas
                                val bandH = max(0.022f, abs(pt.y - s.y))
                                val midY = (s.y + pt.y) * 0.5f
                                a.rects.add(
                                    RectNorm(
                                        min(s.x, pt.x),
                                        (midY - bandH * 0.5f).coerceIn(0f, 1f),
                                        max(s.x, pt.x),
                                        (midY + bandH * 0.5f).coerceIn(0f, 1f)
                                    )
                                )
                                a.text = ""
                            }
                        }
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        val a = draftAnnotation
                        draftAnnotation = null
                        draftDragStart = null
                        if (a != null && a.rects.isNotEmpty()) {
                            val totalW = a.rects.sumOf { (it.right - it.left).toDouble() }
                            if (totalW > 0.012) {
                                onAnnotationCreated?.invoke(a)
                            }
                        }
                        invalidate()
                    }
                }
            }
            AnnotTool.BROWSE -> Unit
        }
        return true
    }

    private fun findAnnotationAt(nx: Float, ny: Float): PdfAnnotation? {
        for (a in annotations.asReversed()) {
            when (a.type) {
                AnnotTool.NOTE, AnnotTool.STAMP -> {
                    val p = a.points.firstOrNull() ?: continue
                    if (hypot((nx - p.x).toDouble(), (ny - p.y).toDouble()) < 0.045) return a
                }
                AnnotTool.TYPEWRITER, AnnotTool.TEXTBOX, AnnotTool.CALLOUT -> {
                    val p = a.points.lastOrNull() ?: continue
                    if (nx >= p.x - 0.02f && nx <= p.x + 0.35f && ny >= p.y - 0.02f && ny <= p.y + 0.14f) return a
                }
                AnnotTool.HIGHLIGHT, AnnotTool.UNDERLINE, AnnotTool.SQUIGGLY, AnnotTool.STRIKEOUT,
                AnnotTool.RECT, AnnotTool.OVAL -> {
                    if (a.rects.any { it.contains(nx, ny, 0.018f) }) return a
                }
                AnnotTool.PENCIL -> {
                    if (a.points.any { hypot((nx - it.x).toDouble(), (ny - it.y).toDouble()) < 0.028 }) return a
                }
                AnnotTool.LINE, AnnotTool.ARROW -> {
                    if (a.points.size >= 2) {
                        val s = a.points.first(); val e = a.points.last()
                        if (pointToSegDist(nx, ny, s.x, s.y, e.x, e.y) < 0.025f) return a
                    }
                }
                else -> Unit
            }
        }
        return null
    }

    private fun pointToSegDist(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-6f) return hypot((px - x1).toDouble(), (py - y1).toDouble()).toFloat()
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot((px - projX).toDouble(), (py - projY).toDouble()).toFloat()
    }
}
