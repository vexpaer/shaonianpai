package com.shaonianpai.tablet.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class AnnotTool(val label: String, val group: String) {
    BROWSE("浏览", "base"),
    // Text markup
    HIGHLIGHT("高亮", "markup"),
    UNDERLINE("下划线", "markup"),
    SQUIGGLY("波浪线", "markup"),
    STRIKEOUT("删除线", "markup"),
    // Drawing
    PENCIL("铅笔", "drawing"),
    LINE("直线", "drawing"),
    ARROW("箭头", "drawing"),
    RECT("矩形", "drawing"),
    OVAL("椭圆", "drawing"),
    ERASER("橡皮擦", "drawing"),
    // Notes & others
    NOTE("便签批注", "others"),
    TYPEWRITER("打字写字", "others"),
    TEXTBOX("文本框", "others"),
    CALLOUT("标注框", "others"),
    STAMP("盖章", "others")
}

data class PointNorm(val x: Float, val y: Float)

data class RectNorm(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun normalized(): RectNorm = RectNorm(
        min(left, right).coerceIn(0f, 1f),
        min(top, bottom).coerceIn(0f, 1f),
        max(left, right).coerceIn(0f, 1f),
        max(top, bottom).coerceIn(0f, 1f)
    )

    fun contains(x: Float, y: Float, pad: Float = 0.012f): Boolean {
        val r = normalized()
        return x >= r.left - pad && x <= r.right + pad && y >= r.top - pad && y <= r.bottom + pad
    }
}

data class ReplyItem(
    val author: String = "学生",
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PdfAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val type: AnnotTool,
    var color: Int,
    var alpha: Int = 100, // 25, 50, 75, 100
    var strokeWidth: Float = 4f, // 1..12
    val points: MutableList<PointNorm> = mutableListOf(),
    val rects: MutableList<RectNorm> = mutableListOf(),
    var text: String = "",
    val replies: MutableList<ReplyItem> = mutableListOf(),
    val author: String = "张明同学",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun deepCopy(): PdfAnnotation = PdfAnnotation(
        id = id,
        pageIndex = pageIndex,
        type = type,
        color = color,
        alpha = alpha,
        strokeWidth = strokeWidth,
        points = points.map { it.copy() }.toMutableList(),
        rects = rects.map { it.copy() }.toMutableList(),
        text = text,
        replies = replies.map { it.copy() }.toMutableList(),
        author = author,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun summaryText(): String {
        if (text.isNotBlank()) return text.trim()
        return when (type) {
            AnnotTool.HIGHLIGHT -> "高亮强调"
            AnnotTool.UNDERLINE -> "下划线标记"
            AnnotTool.SQUIGGLY -> "波浪线标记"
            AnnotTool.STRIKEOUT -> "删除线标记"
            AnnotTool.PENCIL -> "手绘铅笔笔迹"
            AnnotTool.LINE -> "直线标注"
            AnnotTool.ARROW -> "箭头标注"
            AnnotTool.RECT -> "矩形框选"
            AnnotTool.OVAL -> "椭圆圈注"
            AnnotTool.NOTE -> "便签批注"
            AnnotTool.TYPEWRITER -> "文字注释"
            AnnotTool.TEXTBOX -> "文本框注释"
            AnnotTool.CALLOUT -> "指向标注框"
            AnnotTool.STAMP -> "印章标记"
            else -> type.label
        }
    }
}

data class PdfBookmark(
    val pageIndex: Int,
    var title: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class PdfDocState(
    val assetId: String,
    val annotations: MutableList<PdfAnnotation> = mutableListOf(),
    val bookmarks: MutableList<PdfBookmark> = mutableListOf()
)

object FoxitPalette {
    val COLORS = intArrayOf(
        Color.parseColor("#FFEB3B"), // 经典荧光黄
        Color.parseColor("#FF5252"), // 批改红
        Color.parseColor("#179CD8"), // 福昕青蓝
        Color.parseColor("#4CAF50"), // 荧光绿
        Color.parseColor("#FF9800"), // 醒目橙
        Color.parseColor("#9C27B0"), // 重点紫
        Color.parseColor("#E91E63"), // 桃红
        Color.parseColor("#00BCD4"), // 青色
        Color.parseColor("#3F51B5"), // 靛蓝
        Color.parseColor("#795548"), // 棕色
        Color.parseColor("#212121"), // 墨黑
        Color.parseColor("#607D8B")  // 蓝灰
    )

    val STAMPS = listOf(
        "重点" to Color.parseColor("#D32F2F"),
        "必背" to Color.parseColor("#E65100"),
        "错题" to Color.parseColor("#C62828"),
        "疑问" to Color.parseColor("#7B1FA2"),
        "已掌握" to Color.parseColor("#2E7D32"),
        "优秀" to Color.parseColor("#1565C0")
    )

    fun defaultColorFor(tool: AnnotTool): Int = when (tool) {
        AnnotTool.HIGHLIGHT -> Color.parseColor("#FFEB3B")
        AnnotTool.UNDERLINE -> Color.parseColor("#4CAF50")
        AnnotTool.SQUIGGLY -> Color.parseColor("#179CD8")
        AnnotTool.STRIKEOUT -> Color.parseColor("#FF5252")
        AnnotTool.PENCIL, AnnotTool.LINE, AnnotTool.ARROW,
        AnnotTool.RECT, AnnotTool.OVAL -> Color.parseColor("#FF5252")
        AnnotTool.NOTE -> Color.parseColor("#FFB300")
        AnnotTool.TYPEWRITER, AnnotTool.TEXTBOX, AnnotTool.CALLOUT -> Color.parseColor("#1565C0")
        AnnotTool.STAMP -> Color.parseColor("#D32F2F")
        else -> Color.parseColor("#FF5252")
    }

    fun defaultAlphaFor(tool: AnnotTool): Int = when (tool) {
        AnnotTool.HIGHLIGHT -> 50
        else -> 100
    }
}

object PdfAnnotationStore {

    private fun fileFor(context: Context, assetId: String): File {
        val dir = File(context.filesDir, "pdf_annots")
        if (!dir.exists()) dir.mkdirs()
        val safe = assetId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(dir, "$safe.json")
    }

    fun load(context: Context, assetId: String): PdfDocState {
        val file = fileFor(context, assetId)
        if (!file.exists()) return PdfDocState(assetId)
        return try {
            val root = JSONObject(file.readText())
            val annots = mutableListOf<PdfAnnotation>()
            val arr = root.optJSONArray("annotations") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tool = runCatching { AnnotTool.valueOf(o.getString("type")) }.getOrNull() ?: continue
                val pts = mutableListOf<PointNorm>()
                val ptsArr = o.optJSONArray("points") ?: JSONArray()
                for (j in 0 until ptsArr.length()) {
                    val p = ptsArr.getJSONObject(j)
                    pts.add(PointNorm(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
                }
                val rects = mutableListOf<RectNorm>()
                val rArr = o.optJSONArray("rects") ?: JSONArray()
                for (j in 0 until rArr.length()) {
                    val r = rArr.getJSONObject(j)
                    rects.add(
                        RectNorm(
                            r.getDouble("l").toFloat(),
                            r.getDouble("t").toFloat(),
                            r.getDouble("r").toFloat(),
                            r.getDouble("b").toFloat()
                        )
                    )
                }
                val replies = mutableListOf<ReplyItem>()
                val repArr = o.optJSONArray("replies") ?: JSONArray()
                for (j in 0 until repArr.length()) {
                    val rp = repArr.getJSONObject(j)
                    replies.add(
                        ReplyItem(
                            author = rp.optString("author", "学生"),
                            text = rp.optString("text", ""),
                            timestamp = rp.optLong("ts", 0L)
                        )
                    )
                }
                annots.add(
                    PdfAnnotation(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        pageIndex = o.optInt("page", 0),
                        type = tool,
                        color = o.optInt("color", FoxitPalette.defaultColorFor(tool)),
                        alpha = o.optInt("alpha", 100),
                        strokeWidth = o.optDouble("width", 4.0).toFloat(),
                        points = pts,
                        rects = rects,
                        text = o.optString("text", ""),
                        replies = replies,
                        author = o.optString("author", "张明同学"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            val bookmarks = mutableListOf<PdfBookmark>()
            val bmArr = root.optJSONArray("bookmarks") ?: JSONArray()
            for (i in 0 until bmArr.length()) {
                val b = bmArr.getJSONObject(i)
                bookmarks.add(
                    PdfBookmark(
                        pageIndex = b.getInt("page"),
                        title = b.optString("title", "第 ${b.getInt("page") + 1} 页"),
                        createdAt = b.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            PdfDocState(assetId, annots, bookmarks)
        } catch (_: Exception) {
            PdfDocState(assetId)
        }
    }

    fun save(context: Context, state: PdfDocState) {
        val root = JSONObject()
        root.put("assetId", state.assetId)
        val arr = JSONArray()
        for (a in state.annotations) {
            val o = JSONObject()
            o.put("id", a.id)
            o.put("page", a.pageIndex)
            o.put("type", a.type.name)
            o.put("color", a.color)
            o.put("alpha", a.alpha)
            o.put("width", a.strokeWidth.toDouble())
            o.put("text", a.text)
            o.put("author", a.author)
            o.put("createdAt", a.createdAt)
            o.put("updatedAt", a.updatedAt)
            val pts = JSONArray()
            for (p in a.points) {
                pts.put(JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble()))
            }
            o.put("points", pts)
            val rects = JSONArray()
            for (r in a.rects) {
                rects.put(
                    JSONObject()
                        .put("l", r.left.toDouble())
                        .put("t", r.top.toDouble())
                        .put("r", r.right.toDouble())
                        .put("b", r.bottom.toDouble())
                )
            }
            o.put("rects", rects)
            val replies = JSONArray()
            for (rp in a.replies) {
                replies.put(
                    JSONObject()
                        .put("author", rp.author)
                        .put("text", rp.text)
                        .put("ts", rp.timestamp)
                )
            }
            o.put("replies", replies)
            arr.put(o)
        }
        root.put("annotations", arr)

        val bmArr = JSONArray()
        for (b in state.bookmarks) {
            bmArr.put(
                JSONObject()
                    .put("page", b.pageIndex)
                    .put("title", b.title)
                    .put("createdAt", b.createdAt)
            )
        }
        root.put("bookmarks", bmArr)

        fileFor(context, state.assetId).writeText(root.toString(2))
    }

    /**
     * Exports a copy of the PDF with both standard PDF annotations (Highlight/Underline/Squiggly/Strikeout/StickyNote)
     * and crisp rendered vector/CJK overlays so any desktop or mobile PDF viewer shows every annotation faithfully.
     */
    fun exportAnnotatedPdf(
        context: Context,
        sourceFile: File,
        password: String?,
        state: PdfDocState,
        outFile: File
    ): File {
        PdfBoxTextExtractor.ensureInit(context)
        val doc = if (password.isNullOrEmpty()) PDDocument.load(sourceFile) else PDDocument.load(sourceFile, password)
        doc.use { pdDoc ->
            if (pdDoc.isEncrypted) {
                pdDoc.isAllSecurityToBeRemoved = true
            }
            val byPage = state.annotations.groupBy { it.pageIndex }
            for ((pageIdx, list) in byPage) {
                if (pageIdx < 0 || pageIdx >= pdDoc.numberOfPages) continue
                val page = pdDoc.getPage(pageIdx)
                val box = page.cropBox ?: page.mediaBox
                val pw = box.width
                val ph = box.height

                // 1) Write standard PDF annotations for text markup and sticky notes
                for (a in list) {
                    val r = Color.red(a.color) / 255f
                    val g = Color.green(a.color) / 255f
                    val b = Color.blue(a.color) / 255f
                    val pdColor = PDColor(floatArrayOf(r, g, b), PDDeviceRGB.INSTANCE)

                    when (a.type) {
                        AnnotTool.HIGHLIGHT, AnnotTool.UNDERLINE, AnnotTool.SQUIGGLY, AnnotTool.STRIKEOUT -> {
                            val sub = when (a.type) {
                                AnnotTool.HIGHLIGHT -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
                                AnnotTool.UNDERLINE -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
                                AnnotTool.SQUIGGLY -> PDAnnotationTextMarkup.SUB_TYPE_SQUIGGLY
                                else -> PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
                            }
                            if (a.rects.isNotEmpty()) {
                                val markup = PDAnnotationTextMarkup(sub)
                                markup.color = pdColor
                                markup.constantOpacity = (a.alpha.coerceIn(15, 100) / 100f)
                                markup.contents = a.text
                                markup.titlePopup = a.author
                                val quads = FloatArray(a.rects.size * 8)
                                var minX = Float.MAX_VALUE
                                var minY = Float.MAX_VALUE
                                var maxX = -Float.MAX_VALUE
                                var maxY = -Float.MAX_VALUE
                                a.rects.forEachIndexed { idx, rn ->
                                    val nr = rn.normalized()
                                    val x1 = box.lowerLeftX + nr.left * pw
                                    val x2 = box.lowerLeftX + nr.right * pw
                                    val yTop = box.lowerLeftY + (1f - nr.top) * ph
                                    val yBot = box.lowerLeftY + (1f - nr.bottom) * ph
                                    val base = idx * 8
                                    quads[base] = x1; quads[base + 1] = yTop
                                    quads[base + 2] = x2; quads[base + 3] = yTop
                                    quads[base + 4] = x1; quads[base + 5] = yBot
                                    quads[base + 6] = x2; quads[base + 7] = yBot
                                    minX = min(minX, x1); maxX = max(maxX, x2)
                                    minY = min(minY, yBot); maxY = max(maxY, yTop)
                                }
                                markup.quadPoints = quads
                                markup.rectangle = PDRectangle(minX, minY, max(1f, maxX - minX), max(1f, maxY - minY))
                                page.annotations.add(markup)
                            }
                        }
                        AnnotTool.NOTE -> {
                            val pt = a.points.firstOrNull() ?: continue
                            val note = PDAnnotationText()
                            note.color = pdColor
                            note.contents = buildString {
                                append(a.text)
                                for (rp in a.replies) {
                                    append("\n[").append(rp.author).append("] ").append(rp.text)
                                }
                            }
                            note.titlePopup = a.author
                            note.name = PDAnnotationText.NAME_NOTE
                            val nx = box.lowerLeftX + pt.x * pw
                            val ny = box.lowerLeftY + (1f - pt.y) * ph
                            note.rectangle = PDRectangle(nx - 12f, ny - 12f, 24f, 24f)
                            page.annotations.add(note)
                        }
                        else -> Unit
                    }
                }

                // 2) Draw vector strokes/shapes directly on PDF content stream
                val vectorItems = list.filter {
                    it.type in setOf(AnnotTool.PENCIL, AnnotTool.LINE, AnnotTool.ARROW, AnnotTool.RECT, AnnotTool.OVAL)
                }
                if (vectorItems.isNotEmpty()) {
                    PDPageContentStream(pdDoc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        for (a in vectorItems) {
                            cs.saveGraphicsState()
                            val gs = PDExtendedGraphicsState()
                            gs.strokingAlphaConstant = a.alpha.coerceIn(10, 100) / 100f
                            cs.setGraphicsStateParameters(gs)
                            cs.setStrokingColor(
                                Color.red(a.color) / 255f,
                                Color.green(a.color) / 255f,
                                Color.blue(a.color) / 255f
                            )
                            cs.setLineWidth((a.strokeWidth * 0.7f).coerceIn(0.8f, 12f))
                            cs.setLineCapStyle(1)
                            cs.setLineJoinStyle(1)
                            when (a.type) {
                                AnnotTool.PENCIL -> {
                                    if (a.points.size >= 2) {
                                        val p0 = a.points.first()
                                        cs.moveTo(box.lowerLeftX + p0.x * pw, box.lowerLeftY + (1f - p0.y) * ph)
                                        for (i in 1 until a.points.size) {
                                            val p = a.points[i]
                                            cs.lineTo(box.lowerLeftX + p.x * pw, box.lowerLeftY + (1f - p.y) * ph)
                                        }
                                        cs.stroke()
                                    }
                                }
                                AnnotTool.LINE, AnnotTool.ARROW -> {
                                    if (a.points.size >= 2) {
                                        val s = a.points.first()
                                        val e = a.points.last()
                                        val sx = box.lowerLeftX + s.x * pw
                                        val sy = box.lowerLeftY + (1f - s.y) * ph
                                        val ex = box.lowerLeftX + e.x * pw
                                        val ey = box.lowerLeftY + (1f - e.y) * ph
                                        cs.moveTo(sx, sy)
                                        cs.lineTo(ex, ey)
                                        if (a.type == AnnotTool.ARROW) {
                                            val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                                            val head = 12.0
                                            val a1 = angle + Math.toRadians(150.0)
                                            val a2 = angle - Math.toRadians(150.0)
                                            cs.moveTo(ex, ey)
                                            cs.lineTo((ex + head * cos(a1)).toFloat(), (ey + head * sin(a1)).toFloat())
                                            cs.moveTo(ex, ey)
                                            cs.lineTo((ex + head * cos(a2)).toFloat(), (ey + head * sin(a2)).toFloat())
                                        }
                                        cs.stroke()
                                    }
                                }
                                AnnotTool.RECT -> {
                                    val rn = a.rects.firstOrNull()?.normalized()
                                    if (rn != null) {
                                        val x = box.lowerLeftX + rn.left * pw
                                        val y = box.lowerLeftY + (1f - rn.bottom) * ph
                                        cs.addRect(x, y, (rn.right - rn.left) * pw, (rn.bottom - rn.top) * ph)
                                        cs.stroke()
                                    }
                                }
                                AnnotTool.OVAL -> {
                                    val rn = a.rects.firstOrNull()?.normalized()
                                    if (rn != null) {
                                        val cx = box.lowerLeftX + (rn.left + rn.right) * 0.5f * pw
                                        val cy = box.lowerLeftY + (1f - (rn.top + rn.bottom) * 0.5f) * ph
                                        val rx = (rn.right - rn.left) * 0.5f * pw
                                        val ry = (rn.bottom - rn.top) * 0.5f * ph
                                        val k = 0.5522848f
                                        cs.moveTo(cx + rx, cy)
                                        cs.curveTo(cx + rx, cy + ry * k, cx + rx * k, cy + ry, cx, cy + ry)
                                        cs.curveTo(cx - rx * k, cy + ry, cx - rx, cy + ry * k, cx - rx, cy)
                                        cs.curveTo(cx - rx, cy - ry * k, cx - rx * k, cy - ry, cx, cy - ry)
                                        cs.curveTo(cx + rx * k, cy - ry, cx + rx, cy - ry * k, cx + rx, cy)
                                        cs.stroke()
                                    }
                                }
                                else -> Unit
                            }
                            cs.restoreGraphicsState()
                        }
                    }
                }

                // 3) Render CJK text/textbox/callout/stamp onto a transparent overlay bitmap so Chinese glyphs always appear accurately
                val overlayItems = list.filter {
                    it.type in setOf(AnnotTool.TYPEWRITER, AnnotTool.TEXTBOX, AnnotTool.CALLOUT, AnnotTool.STAMP)
                }
                if (overlayItems.isNotEmpty()) {
                    val bmpW = 1200
                    val bmpH = (bmpW * (ph / pw)).toInt().coerceIn(600, 2400)
                    val overlay = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(overlay)
                    PdfAnnotRenderer.drawAnnotations(canvas, overlayItems, bmpW.toFloat(), bmpH.toFloat(), null)
                    val pdImage = LosslessFactory.createFromImage(pdDoc, overlay)
                    PDPageContentStream(pdDoc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.drawImage(pdImage, box.lowerLeftX, box.lowerLeftY, pw, ph)
                    }
                    overlay.recycle()
                }
            }
            pdDoc.save(outFile)
        }
        return outFile
    }
}

/**
 * Shared vector renderer used by both the live interactive canvas and PDF overlay export.
 */
object PdfAnnotRenderer {

    fun drawAnnotations(
        canvas: Canvas,
        annotations: List<PdfAnnotation>,
        pageW: Float,
        pageH: Float,
        notePinBitmap: Bitmap?
    ) {
        for (a in annotations) {
            drawSingle(canvas, a, pageW, pageH, notePinBitmap)
        }
    }

    fun drawSingle(
        canvas: Canvas,
        a: PdfAnnotation,
        pageW: Float,
        pageH: Float,
        notePinBitmap: Bitmap?
    ) {
        val alphaInt = ((a.alpha.coerceIn(10, 100) / 100f) * 255).toInt()
        val scale = pageW / 960f
        val strokePx = (a.strokeWidth * 1.6f * scale).coerceAtLeast(1.8f)

        when (a.type) {
            AnnotTool.HIGHLIGHT -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = a.color
                    alpha = ((a.alpha.coerceIn(15, 85) / 100f) * 255).toInt()
                }
                for (rn in a.rects) {
                    val nr = rn.normalized()
                    canvas.drawRoundRect(
                        RectF(nr.left * pageW, nr.top * pageH, nr.right * pageW, nr.bottom * pageH),
                        3f * scale, 3f * scale, p
                    )
                }
            }
            AnnotTool.UNDERLINE -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = a.color
                    alpha = alphaInt
                    strokeWidth = (strokePx * 0.75f).coerceAtLeast(2.2f * scale)
                    strokeCap = Paint.Cap.ROUND
                }
                for (rn in a.rects) {
                    val nr = rn.normalized()
                    val y = nr.bottom * pageH
                    canvas.drawLine(nr.left * pageW, y, nr.right * pageW, y, p)
                }
            }
            AnnotTool.STRIKEOUT -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = a.color
                    alpha = alphaInt
                    strokeWidth = (strokePx * 0.75f).coerceAtLeast(2.2f * scale)
                    strokeCap = Paint.Cap.ROUND
                }
                for (rn in a.rects) {
                    val nr = rn.normalized()
                    val y = (nr.top + nr.bottom) * 0.5f * pageH
                    canvas.drawLine(nr.left * pageW, y, nr.right * pageW, y, p)
                }
            }
            AnnotTool.SQUIGGLY -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = a.color
                    alpha = alphaInt
                    strokeWidth = (strokePx * 0.65f).coerceAtLeast(2f * scale)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val step = (6f * scale).coerceAtLeast(4f)
                val amp = (2.6f * scale).coerceAtLeast(1.8f)
                for (rn in a.rects) {
                    val nr = rn.normalized()
                    val x1 = nr.left * pageW
                    val x2 = nr.right * pageW
                    val y = nr.bottom * pageH
                    val path = Path()
                    path.moveTo(x1, y)
                    var x = x1
                    var up = true
                    while (x < x2) {
                        val nextX = min(x + step, x2)
                        val midX = (x + nextX) * 0.5f
                        path.quadTo(midX, if (up) y - amp else y + amp, nextX, y)
                        up = !up
                        x = nextX
                    }
                    canvas.drawPath(path, p)
                }
            }
            AnnotTool.PENCIL -> {
                if (a.points.size < 2) return
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = a.color
                    alpha = alphaInt
                    strokeWidth = strokePx
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val path = Path()
                path.moveTo(a.points[0].x * pageW, a.points[0].y * pageH)
                for (i in 1 until a.points.size) {
                    val prev = a.points[i - 1]
                    val curr = a.points[i]
                    val midX = (prev.x + curr.x) * 0.5f * pageW
                    val midY = (prev.y + curr.y) * 0.5f * pageH
                    path.quadTo(prev.x * pageW, prev.y * pageH, midX, midY)
                }
                val last = a.points.last()
                path.lineTo(last.x * pageW, last.y * pageH)
                canvas.drawPath(path, p)
            }
            AnnotTool.LINE, AnnotTool.ARROW -> {
                if (a.points.size < 2) return
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = a.color
                    alpha = alphaInt
                    strokeWidth = strokePx
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val s = a.points.first()
                val e = a.points.last()
                val sx = s.x * pageW; val sy = s.y * pageH
                val ex = e.x * pageW; val ey = e.y * pageH
                canvas.drawLine(sx, sy, ex, ey, p)
                if (a.type == AnnotTool.ARROW) {
                    val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                    val head = (16f * scale + strokePx * 1.4f).toDouble()
                    val a1 = angle + Math.toRadians(150.0)
                    val a2 = angle - Math.toRadians(150.0)
                    val path = Path()
                    path.moveTo(ex, ey)
                    path.lineTo((ex + head * cos(a1)).toFloat(), (ey + head * sin(a1)).toFloat())
                    path.moveTo(ex, ey)
                    path.lineTo((ex + head * cos(a2)).toFloat(), (ey + head * sin(a2)).toFloat())
                    canvas.drawPath(path, p)
                }
            }
            AnnotTool.RECT -> {
                val rn = a.rects.firstOrNull()?.normalized() ?: return
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = a.color
                    alpha = alphaInt
                    strokeWidth = strokePx
                }
                canvas.drawRect(rn.left * pageW, rn.top * pageH, rn.right * pageW, rn.bottom * pageH, p)
            }
            AnnotTool.OVAL -> {
                val rn = a.rects.firstOrNull()?.normalized() ?: return
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = a.color
                    alpha = alphaInt
                    strokeWidth = strokePx
                }
                canvas.drawOval(RectF(rn.left * pageW, rn.top * pageH, rn.right * pageW, rn.bottom * pageH), p)
            }
            AnnotTool.NOTE -> {
                val pt = a.points.firstOrNull() ?: return
                val cx = pt.x * pageW
                val cy = pt.y * pageH
                val sz = 28f * scale
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.parseColor("#FFF59D")
                    setShadowLayer(4f * scale, 0f, 2f * scale, Color.parseColor("#55000000"))
                }
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = a.color
                    strokeWidth = 2.5f * scale
                }
                val rect = RectF(cx - sz * 0.55f, cy - sz * 0.55f, cx + sz * 0.55f, cy + sz * 0.55f)
                canvas.drawRoundRect(rect, 6f * scale, 6f * scale, bg)
                canvas.drawRoundRect(rect, 6f * scale, 6f * scale, border)
                if (notePinBitmap != null) {
                    val inset = 4f * scale
                    canvas.drawBitmap(
                        notePinBitmap, null,
                        RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                        null
                    )
                } else {
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#5D4037")
                        textSize = 15f * scale
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    canvas.drawText("注", cx, cy + 5f * scale, tp)
                }
            }
            AnnotTool.TYPEWRITER, AnnotTool.TEXTBOX, AnnotTool.CALLOUT -> {
                val pt = a.points.lastOrNull() ?: a.rects.firstOrNull()?.let { PointNorm(it.left, it.top) } ?: return
                val text = a.text.ifBlank { "批注文字" }
                val lines = text.split("\n")
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = a.color
                    alpha = alphaInt
                    textSize = (18f + a.strokeWidth * 1.4f) * scale
                    typeface = Typeface.DEFAULT
                }
                val lineH = tp.fontSpacing
                var maxW = 0f
                for (l in lines) maxW = max(maxW, tp.measureText(l))
                val pad = 8f * scale
                val bx = pt.x * pageW
                val by = pt.y * pageH
                val boxRect = RectF(bx, by, bx + maxW + pad * 2, by + lineH * lines.size + pad * 1.4f)

                if (a.type == AnnotTool.CALLOUT && a.points.size >= 2) {
                    val anchor = a.points.first()
                    val ax = anchor.x * pageW
                    val ay = anchor.y * pageH
                    val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        color = a.color
                        alpha = alphaInt
                        strokeWidth = 2.4f * scale
                    }
                    val connectX = if (ax < boxRect.left) boxRect.left else if (ax > boxRect.right) boxRect.right else boxRect.centerX()
                    val connectY = boxRect.centerY()
                    canvas.drawLine(connectX, connectY, ax, ay, lp)
                    val angle = atan2((ay - connectY).toDouble(), (ax - connectX).toDouble())
                    val head = (12f * scale).toDouble()
                    canvas.drawLine(ax, ay, (ax + head * cos(angle + Math.toRadians(150.0))).toFloat(), (ay + head * sin(angle + Math.toRadians(150.0))).toFloat(), lp)
                    canvas.drawLine(ax, ay, (ax + head * cos(angle - Math.toRadians(150.0))).toFloat(), (ay + head * sin(angle - Math.toRadians(150.0))).toFloat(), lp)
                }

                if (a.type == AnnotTool.TEXTBOX || a.type == AnnotTool.CALLOUT) {
                    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = Color.parseColor("#EEFFFDE7")
                    }
                    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        color = a.color
                        alpha = alphaInt
                        strokeWidth = 2.2f * scale
                    }
                    canvas.drawRoundRect(boxRect, 5f * scale, 5f * scale, bg)
                    canvas.drawRoundRect(boxRect, 5f * scale, 5f * scale, border)
                }

                var ty = boxRect.top + pad - tp.ascent() * 0.85f
                for (l in lines) {
                    canvas.drawText(l, boxRect.left + pad, ty, tp)
                    ty += lineH
                }
            }
            AnnotTool.STAMP -> {
                val pt = a.points.firstOrNull() ?: return
                val label = a.text.ifBlank { "重点" }
                val cx = pt.x * pageW
                val cy = pt.y * pageH
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = a.color
                    alpha = alphaInt
                    textSize = 22f * scale
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }
                val tw = tp.measureText(label)
                val hw = max(44f * scale, tw * 0.65f + 16f * scale)
                val hh = 22f * scale
                val outer = RectF(cx - hw, cy - hh, cx + hw, cy + hh)
                val inner = RectF(cx - hw + 4f * scale, cy - hh + 4f * scale, cx + hw - 4f * scale, cy + hh - 4f * scale)
                canvas.save()
                canvas.rotate(-12f, cx, cy)
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.WHITE
                    alpha = 200
                }
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = a.color
                    alpha = alphaInt
                    strokeWidth = 3f * scale
                }
                val border2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = a.color
                    alpha = alphaInt
                    strokeWidth = 1.4f * scale
                }
                canvas.drawRoundRect(outer, 8f * scale, 8f * scale, bg)
                canvas.drawRoundRect(outer, 8f * scale, 8f * scale, border)
                canvas.drawRoundRect(inner, 5f * scale, 5f * scale, border2)
                canvas.drawText(label, cx, cy + 7.5f * scale, tp)
                canvas.restore()
            }
            else -> Unit
        }
    }
}
