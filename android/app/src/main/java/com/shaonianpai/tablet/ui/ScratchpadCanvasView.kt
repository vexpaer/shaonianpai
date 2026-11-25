package com.shaonianpai.tablet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot

enum class PaperBackground(val label: String) {
    DEFAULT("默认背景"),
    HOZ_LINE("横线"),
    GRID("网格"),
    DOT("点点"),
    VER_LINE("竖线"),
    EMPTY("白纸一张")
}

enum class ScratchTool(val label: String) {
    PENCIL("铅笔"),
    BRUSH("粗笔"),
    ERASER("橡皮擦"),
    TEXT("文字输入")
}

data class ScratchStroke(
    val isText: Boolean = false,
    val color: Int = Color.parseColor("#1A237E"),
    val width: Float = 4f,
    val alpha: Int = 255,
    val points: MutableList<Pair<Float, Float>> = mutableListOf(), // normalized 0..1
    val text: String = ""
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("isText", isText)
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

    var activeTool: ScratchTool = ScratchTool.PENCIL
    var activeColor: Int = Color.parseColor("#1A237E")
    var paperBackground: PaperBackground = PaperBackground.HOZ_LINE
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

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        drawPaperBackground(canvas, w, h)
        for (s in strokes) drawStroke(canvas, s, w, h)
        liveStroke?.let { drawStroke(canvas, it, w, h) }
    }

    private fun drawPaperBackground(canvas: Canvas, w: Float, h: Float) {
        val bgColor = when (paperBackground) {
            PaperBackground.DEFAULT -> Color.parseColor("#FAF8F0")
            PaperBackground.HOZ_LINE -> Color.parseColor("#FFFDF7")
            else -> Color.WHITE
        }
        canvas.drawColor(bgColor)

        val d = resources.displayMetrics.density
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCE6F2")
            strokeWidth = 1f * d
        }
        val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F4B8B8")
            strokeWidth = 1.4f * d
        }
        val step = 32f * d

        when (paperBackground) {
            PaperBackground.HOZ_LINE -> {
                var y = step * 1.5f
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, linePaint)
                    y += step
                }
                canvas.drawLine(46f * d, 0f, 46f * d, h, marginPaint)
            }
            PaperBackground.VER_LINE -> {
                var x = step
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, linePaint)
                    x += step
                }
            }
            PaperBackground.GRID -> {
                var x = step
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, linePaint)
                    x += step
                }
                var y = step
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, linePaint)
                    y += step
                }
            }
            PaperBackground.DOT -> {
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#B0BEC5")
                    style = Paint.Style.FILL
                }
                val dotStep = 26f * d
                var y = dotStep
                while (y < h) {
                    var x = dotStep
                    while (x < w) {
                        canvas.drawCircle(x, y, 1.8f * d, dotPaint)
                        x += dotStep
                    }
                    y += dotStep
                }
            }
            PaperBackground.DEFAULT, PaperBackground.EMPTY -> Unit
        }
    }

    private fun drawStroke(canvas: Canvas, s: ScratchStroke, w: Float, h: Float) {
        if (s.points.isEmpty()) return
        val scale = (w / 960f).coerceAtLeast(0.55f)
        if (s.isText) {
            val (nx, ny) = s.points.first()
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = s.color
                textSize = 22f * scale
                typeface = Typeface.DEFAULT
            }
            var ty = ny * h
            for (line in s.text.split("\n")) {
                canvas.drawText(line, nx * w, ty, tp)
                ty += tp.fontSpacing
            }
            return
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = s.color
            alpha = s.alpha
            strokeWidth = (s.width * scale).coerceAtLeast(2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (s.points.size == 1) {
            val (x, y) = s.points[0]
            canvas.drawPoint(x * w, y * h, p)
            return
        }
        val path = Path()
        path.moveTo(s.points[0].first * w, s.points[0].second * h)
        for (i in 1 until s.points.size) {
            val prev = s.points[i - 1]
            val curr = s.points[i]
            val mx = (prev.first + curr.first) * 0.5f * w
            val my = (prev.second + curr.second) * 0.5f * h
            path.quadTo(prev.first * w, prev.second * h, mx, my)
        }
        val last = s.points.last()
        path.lineTo(last.first * w, last.second * h)
        canvas.drawPath(path, p)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val nx = (event.x / w).coerceIn(0f, 1f)
        val ny = (event.y / h).coerceIn(0f, 1f)

        when (activeTool) {
            ScratchTool.TEXT -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    onRequestTextAt?.invoke(nx, ny)
                }
            }
            ScratchTool.ERASER -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val hit = strokes.lastOrNull { st ->
                        st.points.any { (px, py) -> hypot((nx - px).toDouble(), (ny - py).toDouble()) < 0.032 }
                    }
                    if (hit != null) {
                        pushUndo()
                        strokes.remove(hit)
                        invalidate()
                        onStrokesChanged?.invoke()
                    }
                }
            }
            ScratchTool.PENCIL, ScratchTool.BRUSH -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val fine = com.shaonianpai.tablet.data.UserProfileStore.getFinePenWidth(context)
                        val thick = com.shaonianpai.tablet.data.UserProfileStore.getThickPenWidth(context)
                        liveStroke = ScratchStroke(
                            isText = false,
                            color = activeColor,
                            width = if (activeTool == ScratchTool.BRUSH) thick else fine,
                            alpha = if (activeTool == ScratchTool.BRUSH) 190 else 255,
                            points = mutableListOf(nx to ny)
                        )
                        invalidate()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        liveStroke?.points?.let { pts ->
                            val last = pts.lastOrNull()
                            if (last == null || hypot((nx - last.first).toDouble(), (ny - last.second).toDouble()) > 0.003) {
                                pts.add(nx to ny)
                                invalidate()
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val s = liveStroke
                        liveStroke = null
                        if (s != null) {
                            pushUndo()
                            strokes.add(s)
                            onStrokesChanged?.invoke()
                        }
                        invalidate()
                    }
                }
            }
        }
        return true
    }
}
