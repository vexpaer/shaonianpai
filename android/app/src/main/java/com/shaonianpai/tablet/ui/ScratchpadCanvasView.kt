package com.shaonianpai.tablet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
    PENCIL("铅笔"),
    ERASER("橡皮擦"),
    TEXT("文字输入")
}

data class ScratchStroke(
    val isText: Boolean = false,
    val isEraser: Boolean = false,
    val color: Int = Color.parseColor("#1A237E"),
    val width: Float = 4f,
    val alpha: Int = 255,
    val points: MutableList<Pair<Float, Float>> = mutableListOf(), // normalized 0..1
    val text: String = ""
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("isText", isText)
        o.put("isEraser", isEraser)
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
    var activeStrokeWidth: Float = com.shaonianpai.tablet.data.UserProfileStore.getFinePenWidth(context)
    var paperBackground: PaperBackground = PaperBackground.DEFAULT
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        // 1. Draw paper background layer
        drawPaperBackground(canvas, w, h)

        // 2. Draw all ink + pixel-level pen-style eraser strokes in an isolated offscreen layer
        //    so PorterDuff.Mode.CLEAR erases ONLY ink strokes without erasing the yellow paper below!
        val layerId = canvas.saveLayer(0f, 0f, w, h, null)
        for (s in strokes) drawStroke(canvas, s, w, h)
        liveStroke?.let { drawStroke(canvas, it, w, h) }
        canvas.restoreToCount(layerId)
    }

    private fun drawPaperBackground(canvas: Canvas, w: Float, h: Float) {
        if (paperBackground == PaperBackground.DEFAULT) {
            val bmp = defaultPaperBitmap
            if (bmp != null) {
                paperRect.set(0f, 0f, w, h)
                canvas.drawBitmap(bmp, null, paperRect, null)
                return
            }
            // Fallback yellow legal-pad ruled lines if resource missing
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
        val step = 32f * d

        when (paperBackground) {
            PaperBackground.DEFAULT, PaperBackground.HOZ_LINE -> {
                var y = step * 1.5f
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, linePaint)
                    y += step
                }
                canvas.drawLine(46f * d, 0f, 46f * d, h, marginPaint)
                if (paperBackground == PaperBackground.DEFAULT) {
                    canvas.drawLine(50f * d, 0f, 50f * d, h, marginPaint)
                }
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
            PaperBackground.EMPTY -> Unit
        }
    }

    private fun drawStroke(canvas: Canvas, s: ScratchStroke, w: Float, h: Float) {
        if (s.points.isEmpty()) return
        val scale = (w / 960f).coerceAtLeast(0.6f)
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
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (s.isEraser) {
                xfermode = clearXfer
                strokeWidth = (s.width * scale).coerceAtLeast(18f)
            } else {
                color = s.color
                alpha = s.alpha
                strokeWidth = (s.width * scale).coerceAtLeast(2f)
            }
        }
        val path = Path()
        val first = s.points.first()
        path.moveTo(first.first * w, first.second * h)
        if (s.points.size == 1) {
            path.lineTo(first.first * w + 0.5f, first.second * h + 0.5f)
        } else {
            for (i in 1 until s.points.size) {
                path.lineTo(s.points[i].first * w, s.points[i].second * h)
            }
        }
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
                return true
            }
            ScratchTool.ERASER -> {
                // Pen-style pixel eraser: drags a transparent CLEAR stroke like a real eraser!
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        liveStroke = ScratchStroke(
                            isText = false,
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
            ScratchTool.PENCIL -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        liveStroke = ScratchStroke(
                            isText = false,
                            isEraser = false,
                            color = activeColor,
                            width = activeStrokeWidth,
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
