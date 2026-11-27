package com.shaonianpai.tablet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.shaonianpai.tablet.R
import org.json.JSONArray
import org.json.JSONObject

enum class PaperBackground(val label: String) {
    DEFAULT("默认黄底横线"),
    HOZ_LINE("白底横线"),
    GRID("网格"),
    DOT("点点"),
    VER_LINE("竖线"),
    EMPTY("白纸一张")
}

enum class ScratchTool(val label: String) {
    BRUSH("毛笔"),
    PENCIL("铅笔"),
    TEXT("文字输入"),
    ERASER("橡皮擦"),
    SHAPE("图形/直尺")
}

enum class ShapeKind(val label: String) {
    LINE("直尺直线"),
    RECT("矩形"),
    TRIANGLE("直角三角形"),
    CIRCLE("圆 / 椭圆")
}

data class ScratchStroke(
    val isText: Boolean = false,
    val isEraser: Boolean = false,
    val shapeKind: String = "", // "", "LINE", "RECT", "TRIANGLE", "CIRCLE"
    val color: Int = Color.parseColor("#1A237E"),
    val width: Float = 4f,
    val alpha: Int = 255,
    val points: MutableList<Pair<Float, Float>> = mutableListOf(), // normalized 0..1 inside paperRect
    val text: String = ""
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("isText", isText)
        o.put("isEraser", isEraser)
        o.put("shapeKind", shapeKind)
        o.put("color", color)
        o.put("width", width.toDouble())
        o.put("alpha", alpha)
        o.put("text", text)
        val arr = JSONArray()
        for ((x, y) in points) {
            arr.put(JSONObject().put("x", x.toDouble()).put("y", y.toDouble()))
        }
        o.put("points", arr)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): ScratchStroke {
            val pts = mutableListOf<Pair<Float, Float>>()
            val arr = o.optJSONArray("points") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                pts.add(p.getDouble("x").toFloat() to p.getDouble("y").toFloat())
            }
            return ScratchStroke(
                isText = o.optBoolean("isText", false),
                isEraser = o.optBoolean("isEraser", false),
                shapeKind = o.optString("shapeKind", ""),
                color = o.optInt("color", Color.BLACK),
                width = o.optDouble("width", 4.0).toFloat(),
                alpha = o.optInt("alpha", 255),
                points = pts,
                text = o.optString("text", "")
            )
        }
    }
}

class ScratchpadCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var activeTool: ScratchTool = ScratchTool.BRUSH
    var activeShapeKind: ShapeKind = ShapeKind.LINE
    var activeColor: Int = Color.parseColor("#1A237E")
    var activeStrokeWidth: Float = com.shaonianpai.tablet.data.UserProfileStore.getFinePenWidth(context)
    var paperBackground: PaperBackground = PaperBackground.DEFAULT
        set(value) {
            field = value
            invalidate()
        }

    /** 0 = horizontal default legal pad; 1 = rotated 90° vertical ruled strip centered on #808080 */
    var isRotatedPortraitPaper: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isCenteredFitMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var onStrokesChanged: (() -> Unit)? = null
    var onRequestTextAt: ((Float, Float) -> Unit)? = null

    private var strokes: MutableList<ScratchStroke> = mutableListOf()
    private val undoStack = ArrayDeque<List<ScratchStroke>>()
    private val redoStack = ArrayDeque<List<ScratchStroke>>()
    private var liveStroke: ScratchStroke? = null

    private val defaultPaperBitmap: Bitmap? by lazy {
        runCatching { BitmapFactory.decodeResource(resources, R.drawable.background_drawpad) }.getOrNull()
    }
    private val paperRect = RectF()
    private val clearXfer = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val rotMatrix = Matrix()

    fun setPageStrokes(list: MutableList<ScratchStroke>) {
        strokes = list
        undoStack.clear()
        redoStack.clear()
        liveStroke = null
        invalidate()
    }

    fun getPageStrokes(): List<ScratchStroke> = strokes

    fun addTextStroke(nx: Float, ny: Float, text: String) {
        if (text.isBlank()) return
        pushUndo()
        strokes.add(
            ScratchStroke(
                isText = true,
                color = activeColor,
                width = 4f,
                alpha = 255,
                points = mutableListOf(nx to ny),
                text = text
            )
        )
        invalidate()
        onStrokesChanged?.invoke()
    }

    fun clearPage() {
        if (strokes.isEmpty()) return
        pushUndo()
        strokes.clear()
        invalidate()
        onStrokesChanged?.invoke()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(strokes.map { it.copy(points = it.points.toMutableList()) })
        val prev = undoStack.removeLast()
        strokes.clear()
        strokes.addAll(prev)
        invalidate()
        onStrokesChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(strokes.map { it.copy(points = it.points.toMutableList()) })
        val next = redoStack.removeLast()
        strokes.clear()
        strokes.addAll(next)
        invalidate()
        onStrokesChanged?.invoke()
    }

    private fun pushUndo() {
        undoStack.addLast(strokes.map { it.copy(points = it.points.toMutableList()) })
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
    }

    fun exportToBitmap(): Bitmap {
        val w = width.coerceAtLeast(800)
        val h = height.coerceAtLeast(600)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        draw(c)
        return bmp
    }

    private fun computePaperRect(w: Float, h: Float) {
        if (isRotatedPortraitPaper) {
            // Centered narrow vertical sheet over #808080 gray backdrop matching 11_草稿本_旋转后竖线纸.png
            val stripW = (h * 0.62f).coerceAtMost(w * 0.86f)
            val left = (w - stripW) / 2f
            paperRect.set(left, 0f, left + stripW, h)
        } else if (isCenteredFitMode) {
            val padX = w * 0.04f
            val padY = h * 0.03f
            paperRect.set(padX, padY, w - padX, h - padY)
        } else {
            // Subtle gray edge border on left matching 10_草稿本_默认横线纸.png
            val d = resources.displayMetrics.density
            paperRect.set(8f * d, 0f, w, h)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        canvas.drawColor(Color.parseColor("#808080"))
        computePaperRect(w, h)

        canvas.save()
        canvas.clipRect(paperRect)
        drawPaperBackground(canvas, paperRect)

        // Isolated layer inside paperRect so pixel eraser erases ink without erasing paper!
        val layerId = canvas.saveLayer(paperRect, null)
        for (s in strokes) drawStroke(canvas, s, paperRect)
        liveStroke?.let { drawStroke(canvas, it, paperRect) }
        canvas.restoreToCount(layerId)
        canvas.restore()
    }

    private fun drawPaperBackground(canvas: Canvas, rect: RectF) {
        val rw = rect.width()
        val rh = rect.height()
        if (paperBackground == PaperBackground.DEFAULT) {
            val bmp = defaultPaperBitmap
            if (bmp != null) {
                if (isRotatedPortraitPaper) {
                    canvas.save()
                    rotMatrix.reset()
                    rotMatrix.postRotate(90f)
                    rotMatrix.postScale(rw / bmp.height.toFloat(), rh / bmp.width.toFloat())
                    rotMatrix.postTranslate(rect.right, rect.top)
                    canvas.drawBitmap(bmp, rotMatrix, null)
                    canvas.restore()
                } else {
                    canvas.drawBitmap(bmp, null, rect, null)
                }
                return
            }
            canvas.drawColor(Color.parseColor("#F5EE9E"))
        } else {
            canvas.drawColor(Color.WHITE)
        }

        val d = resources.displayMetrics.density
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (paperBackground == PaperBackground.DEFAULT) Color.parseColor("#C5BE82") else Color.parseColor("#DCE6F2")
            strokeWidth = 1f * d
        }
        val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D98880")
            strokeWidth = 1.2f * d
        }
        val step = 30f * d

        if (isRotatedPortraitPaper && paperBackground == PaperBackground.DEFAULT) {
            var x = rect.left + step * 1.4f
            while (x < rect.right) {
                canvas.drawLine(x, rect.top, x, rect.bottom, linePaint)
                x += step
            }
            canvas.drawLine(rect.left, rect.top + 46f * d, rect.right, rect.top + 46f * d, marginPaint)
            canvas.drawLine(rect.left, rect.top + 50f * d, rect.right, rect.top + 50f * d, marginPaint)
            return
        }

        when (paperBackground) {
            PaperBackground.DEFAULT, PaperBackground.HOZ_LINE -> {
                var y = rect.top + step * 1.5f
                while (y < rect.bottom) {
                    canvas.drawLine(rect.left, y, rect.right, y, linePaint)
                    y += step
                }
                canvas.drawLine(rect.left + 44f * d, rect.top, rect.left + 44f * d, rect.bottom, marginPaint)
                if (paperBackground == PaperBackground.DEFAULT) {
                    canvas.drawLine(rect.left + 48f * d, rect.top, rect.left + 48f * d, rect.bottom, marginPaint)
                }
            }
            PaperBackground.VER_LINE -> {
                var x = rect.left + step
                while (x < rect.right) {
                    canvas.drawLine(x, rect.top, x, rect.bottom, linePaint)
                    x += step
                }
            }
            PaperBackground.GRID -> {
                var x = rect.left + step
                while (x < rect.right) {
                    canvas.drawLine(x, rect.top, x, rect.bottom, linePaint)
                    x += step
                }
                var y = rect.top + step
                while (y < rect.bottom) {
                    canvas.drawLine(rect.left, y, rect.right, y, linePaint)
                    y += step
                }
            }
            PaperBackground.DOT -> {
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#B0BEC5")
                    style = Paint.Style.FILL
                }
                val dotStep = 26f * d
                var y = rect.top + dotStep
                while (y < rect.bottom) {
                    var x = rect.left + dotStep
                    while (x < rect.right) {
                        canvas.drawCircle(x, y, 1.8f * d, dotPaint)
                        x += dotStep
                    }
                    y += dotStep
                }
            }
            PaperBackground.EMPTY -> Unit
        }
    }

    private fun drawStroke(canvas: Canvas, s: ScratchStroke, rect: RectF) {
        if (s.points.isEmpty()) return
        val rw = rect.width().coerceAtLeast(1f)
        val rh = rect.height().coerceAtLeast(1f)
        val scale = (rw / 960f).coerceAtLeast(0.65f)

        fun px(nx: Float) = rect.left + nx * rw
        fun py(ny: Float) = rect.top + ny * rh

        if (s.isText) {
            val (nx, ny) = s.points.first()
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = s.color
                textSize = 22f * scale
                typeface = Typeface.DEFAULT
            }
            var ty = py(ny)
            for (line in s.text.split("\n")) {
                canvas.drawText(line, px(nx), ty, tp)
                ty += tp.fontSpacing
            }
            return
        }

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (s.isEraser) {
                xfermode = clearXfer
                strokeWidth = (s.width * scale).coerceAtLeast(20f)
            } else {
                color = s.color
                alpha = s.alpha
                strokeWidth = (s.width * scale).coerceAtLeast(2f)
            }
        }

        if (s.shapeKind.isNotEmpty() && s.points.size >= 2) {
            val a = s.points.first()
            val b = s.points.last()
            val x1 = px(a.first); val y1 = py(a.second)
            val x2 = px(b.first); val y2 = py(b.second)
            when (s.shapeKind) {
                "LINE" -> canvas.drawLine(x1, y1, x2, y2, p)
                "RECT" -> canvas.drawRect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2), p)
                "CIRCLE" -> canvas.drawOval(RectF(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2)), p)
                "TRIANGLE" -> {
                    val path = Path().apply {
                        moveTo(x1, y1)
                        lineTo(x1, y2)
                        lineTo(x2, y2)
                        close()
                    }
                    canvas.drawPath(path, p)
                }
            }
            return
        }

        val path = Path()
        val first = s.points.first()
        path.moveTo(px(first.first), py(first.second))
        if (s.points.size == 1) {
            path.lineTo(px(first.first) + 0.5f, py(first.second) + 0.5f)
        } else {
            for (i in 1 until s.points.size) {
                path.lineTo(px(s.points[i].first), py(s.points[i].second))
            }
        }
        canvas.drawPath(path, p)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        computePaperRect(w, h)
        val rw = paperRect.width().coerceAtLeast(1f)
        val rh = paperRect.height().coerceAtLeast(1f)
        val nx = ((event.x - paperRect.left) / rw).coerceIn(0f, 1f)
        val ny = ((event.y - paperRect.top) / rh).coerceIn(0f, 1f)

        when (activeTool) {
            ScratchTool.TEXT -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    onRequestTextAt?.invoke(nx, ny)
                }
                return true
            }
            ScratchTool.ERASER -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        liveStroke = ScratchStroke(
                            isEraser = true,
                            width = 28f,
                            points = mutableListOf(nx to ny)
                        )
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        liveStroke?.points?.add(nx to ny)
                        invalidate()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        liveStroke?.let { finished ->
                            pushUndo()
                            strokes.add(finished)
                            onStrokesChanged?.invoke()
                        }
                        liveStroke = null
                        invalidate()
                    }
                }
                return true
            }
            ScratchTool.SHAPE -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        liveStroke = ScratchStroke(
                            shapeKind = activeShapeKind.name,
                            color = activeColor,
                            width = activeStrokeWidth,
                            alpha = 255,
                            points = mutableListOf(nx to ny, nx to ny)
                        )
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        liveStroke?.let {
                            if (it.points.size >= 2) it.points[1] = nx to ny
                            invalidate()
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        liveStroke?.let { finished ->
                            pushUndo()
                            strokes.add(finished)
                            onStrokesChanged?.invoke()
                        }
                        liveStroke = null
                        invalidate()
                    }
                }
                return true
            }
            ScratchTool.BRUSH, ScratchTool.PENCIL -> {
                val strokeW = if (activeTool == ScratchTool.BRUSH) {
                    com.shaonianpai.tablet.data.UserProfileStore.getThickPenWidth(context).coerceAtLeast(activeStrokeWidth)
                } else {
                    activeStrokeWidth
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        liveStroke = ScratchStroke(
                            color = activeColor,
                            width = strokeW,
                            alpha = 255,
                            points = mutableListOf(nx to ny)
                        )
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        liveStroke?.points?.add(nx to ny)
                        invalidate()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        liveStroke?.let { finished ->
                            pushUndo()
                            strokes.add(finished)
                            onStrokesChanged?.invoke()
                        }
                        liveStroke = null
                        invalidate()
                    }
                }
                return true
            }
        }
    }
}
