package com.shaonianpai.tablet.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.UserProfileStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Faithfully recreates original FingerDrawActivity2 (screenshots 10–18) with user preferences:
 * - Default yellow ruled legal-pad canvas; NO "第1题" badge, NO bottom page counter.
 * - Right-side solid blue vertical strip with the exact 8 icon-only white line-art buttons:
 *   1. 毛笔 (Brush)
 *   2. 铅笔 (Pencil)
 *   3. 文字输入 ("文" -> toggles bottom cyan-bordered multiline input bar matching 13_草稿本_文字输入框.png)
 *   4. 橡皮擦 (Silent pixel-level eraser; long-press to clear)
 *   5. 调色板 (White dialog with HSV rainbow color wheel + brightness bar matching 15_草稿本_调色盘.png)
 *   6. 图形工具 (Triangle ruler -> straight line / rect / triangle / circle; long-press chooses shape)
 *   7. 旋转画布 (90° rotation -> centered vertical yellow strip on #808080 backdrop matching 11_草稿本_旋转后竖线纸.png)
 *   8. 自适应 / 更多 (Fit frame -> opens quick sheet for 纸张样式 / 撤销 / 重做 / 收藏)
 */
class FingerDrawActivity : AppCompatActivity() {

    private lateinit var canvasView: ScratchpadCanvasView
    private lateinit var llRightTools: LinearLayout
    private lateinit var textInputBar: EditText

    private val strokes = mutableListOf<ScratchStroke>()
    private val toolButtons = mutableMapOf<ScratchTool, View>()
    private var btnRotateView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadScratchpad()
        if (intent.getBooleanExtra("demo_seed_scratch", false) && strokes.isEmpty()) {
            seedDemoMathScratch()
        }
        buildLayout()
        canvasView.setPageStrokes(strokes)
        if (intent.getBooleanExtra("demo_show_color_wheel", false)) {
            llRightTools.post { showColorWheelDialog() }
        }
        if (intent.getBooleanExtra("demo_rotated", false)) {
            canvasView.isRotatedPortraitPaper = true
            selectTool(ScratchTool.TEXT)
        }
    }

    override fun onResume() {
        super.onResume()
        FloatingQuickBar.attachIfNeeded(this)
    }

    private fun buildLayout() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = RelativeLayout(this).apply {
            setBackgroundColor(Color.parseColor("#808080"))
        }

        // Right-side vertical blue toolbar matching 10_草稿本_默认横线纸.png (8 icon-only buttons)
        val scrollRight = ScrollView(this).apply {
            id = View.generateViewId()
            setBackgroundColor(UserProfileStore.getThemePrimaryColor(this@FingerDrawActivity))
            isVerticalScrollBarEnabled = false
        }
        llRightTools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(8), dp(4), dp(10))
        }
        scrollRight.addView(llRightTools)

        val rightParams = RelativeLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            addRule(RelativeLayout.ALIGN_PARENT_END)
        }
        root.addView(scrollRight, rightParams)

        canvasView = ScratchpadCanvasView(this).apply {
            paperBackground = PaperBackground.DEFAULT
            onStrokesChanged = { saveScratchpad() }
            onRequestTextAt = { nx, ny -> stampInputTextAt(nx, ny) }
        }
        val canvasParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(RelativeLayout.START_OF, scrollRight.id)
        }
        root.addView(canvasView, canvasParams)

        // Authentic bottom cyan/blue-bordered multiline input bar matching 13_草稿本_文字输入框.png
        textInputBar = EditText(this).apply {
            visibility = View.GONE
            hint = "在这里输入文字，支持多行。点击画布任意位置即可放置。"
            setTextColor(Color.parseColor("#212121"))
            setHintTextColor(Color.parseColor("#757575"))
            textSize = 15f
            maxLines = 4
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(2), Color.parseColor("#00AEEF"))
            }
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    stampInputTextAt(0.18f, 0.25f)
                    true
                } else false
            }
        }
        val inputParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.START_OF, scrollRight.id)
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            setMargins(dp(4), 0, dp(2), dp(4))
        }
        root.addView(textInputBar, inputParams)

        populateRightToolbar()
        setContentView(root)
        selectTool(ScratchTool.BRUSH)
    }

    private fun populateRightToolbar() {
        llRightTools.removeAllViews()
        toolButtons.clear()

        // 8 authentic white line-art icons (NO labels under icons matching 10_草稿本_默认横线纸.png)
        val btnBrush = makeIconBtn(R.drawable.fd_vec_brush, "毛笔") { selectTool(ScratchTool.BRUSH) }
        val btnPencil = makeIconBtn(R.drawable.fd_vec_pencil, "铅笔") { selectTool(ScratchTool.PENCIL) }
        val btnText = makeIconBtn(R.drawable.fd_vec_text, "文字输入") { selectTool(ScratchTool.TEXT) }
        val btnEraser = makeIconBtn(R.drawable.fd_vec_eraser, "橡皮擦") { selectTool(ScratchTool.ERASER) }
        btnEraser.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空草稿纸")
                .setMessage("确定要清除当前全部笔迹吗？（可通过撤销恢复）")
                .setPositiveButton("清空") { _, _ -> canvasView.clearPage() }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        val btnColorize = makeIconBtn(R.drawable.fd_vec_palette, "调色板") { showColorWheelDialog() }
        val btnGraphics = makeIconBtn(R.drawable.fd_vec_ruler, "图形工具") { selectTool(ScratchTool.SHAPE) }
        btnGraphics.setOnLongClickListener {
            showShapePickerDialog()
            true
        }
        val btnRotate = makeIconBtn(R.drawable.fd_vec_rotate, "旋转画布") {
            canvasView.isRotatedPortraitPaper = !canvasView.isRotatedPortraitPaper
            btnRotateView?.alpha = if (canvasView.isRotatedPortraitPaper) 1f else 0.92f
        }
        btnRotateView = btnRotate
        val btnBestFit = makeIconBtn(R.drawable.fd_vec_bestfit, "自适应与更多") {
            showMoreActionsDialog()
        }

        toolButtons[ScratchTool.BRUSH] = btnBrush
        toolButtons[ScratchTool.PENCIL] = btnPencil
        toolButtons[ScratchTool.TEXT] = btnText
        toolButtons[ScratchTool.ERASER] = btnEraser
        toolButtons[ScratchTool.SHAPE] = btnGraphics

        llRightTools.addView(btnBrush)
        llRightTools.addView(btnPencil)
        llRightTools.addView(btnText)
        llRightTools.addView(btnEraser)
        llRightTools.addView(btnColorize)
        llRightTools.addView(btnGraphics)
        llRightTools.addView(btnRotate)
        llRightTools.addView(btnBestFit)
    }

    private fun selectTool(tool: ScratchTool) {
        canvasView.activeTool = tool
        textInputBar.visibility = if (tool == ScratchTool.TEXT) View.VISIBLE else View.GONE
        toolButtons.forEach { (t, view) ->
            view.background = if (t == tool) {
                GradientDrawable().apply {
                    setColor(Color.parseColor("#33FFFFFF"))
                    setStroke(dp(1), Color.WHITE)
                    cornerRadius = dp(4).toFloat()
                }
            } else null
        }
    }

    private fun makeIconBtn(iconRes: Int, desc: String, onClick: () -> Unit): View {
        val d = resources.displayMetrics.density
        val size = (42 * d).toInt()
        val pad = (8 * d).toInt()
        return ImageView(this).apply {
            setImageResource(iconRes)
            contentDescription = desc
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = (4 * d).toInt()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun stampInputTextAt(nx: Float, ny: Float) {
        val pending = textInputBar.text?.toString()?.trim().orEmpty()
        if (pending.isNotBlank()) {
            canvasView.addTextStroke(nx, ny, pending)
            textInputBar.setText("")
            return
        }
        val et = EditText(this).apply {
            hint = "在这里输入文字，支持多行"
            minLines = 2
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        AlertDialog.Builder(this)
            .setTitle("文字输入")
            .setView(et)
            .setPositiveButton("添加") { _, _ ->
                canvasView.addTextStroke(nx, ny, et.text.toString())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** Authentic white card HSV Color Wheel + Value gradient bar dialog matching 15_草稿本_调色盘.png */
    private fun showColorWheelDialog() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val hsv = floatArrayOf(230f, 0.85f, 0.55f)
        Color.colorToHSV(canvasView.activeColor, hsv)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(22), dp(24), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(4).toFloat()
            }
            elevation = dp(10).toFloat()
        }

        lateinit var valueBar: BrightnessGradientView
        val preview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(14)).apply { bottomMargin = dp(10) }
            background = GradientDrawable().apply {
                setColor(Color.HSVToColor(hsv))
                setStroke(dp(1), Color.parseColor("#CCCCCC"))
                cornerRadius = dp(3).toFloat()
            }
        }
        fun syncPreview() {
            (preview.background as? GradientDrawable)?.setColor(Color.HSVToColor(hsv))
        }

        val wheel = RainbowRingView(this, hsv) { hue, sat ->
            hsv[0] = hue
            hsv[1] = sat
            valueBar.setBaseHueSat(hue, sat)
            syncPreview()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(190), dp(190)).apply {
                bottomMargin = dp(16)
            }
        }

        valueBar = BrightnessGradientView(this, hsv) { valComp ->
            hsv[2] = valComp
            syncPreview()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(190), dp(28)).apply {
                bottomMargin = dp(14)
            }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(dp(190), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val btnCancel = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "取消"
            setTextColor(Color.parseColor("#666666"))
            setOnClickListener { dlg.dismiss() }
        }
        val btnOk = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "确定"
            setTextColor(UserProfileStore.getThemePrimaryColor(this@FingerDrawActivity))
            setOnClickListener {
                canvasView.activeColor = Color.HSVToColor(hsv)
                if (canvasView.activeTool == ScratchTool.ERASER) {
                    selectTool(ScratchTool.PENCIL)
                }
                dlg.dismiss()
            }
        }
        btnRow.addView(btnCancel)
        btnRow.addView(btnOk)

        card.addView(wheel)
        card.addView(valueBar)
        card.addView(preview)
        card.addView(btnRow)

        dlg.setContentView(card)
        dlg.show()
    }

    private class RainbowRingView(
        ctx: Context,
        private val hsv: FloatArray,
        private val onPick: (hue: Float, sat: Float) -> Unit
    ) : View(ctx) {
        private val ringColors = intArrayOf(
            Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED
        )
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 4f
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val outerR = minOf(cx, cy) * 0.94f
            val strokeW = outerR * 0.34f
            val r = outerR - strokeW / 2f
            ringPaint.strokeWidth = strokeW
            ringPaint.shader = SweepGradient(cx, cy, ringColors, null)
            canvas.drawCircle(cx, cy, r, ringPaint)

            val rad = Math.toRadians(-hsv[0].toDouble())
            val px = cx + (r * kotlin.math.cos(rad)).toFloat()
            val py = cy + (r * kotlin.math.sin(rad)).toFloat()
            canvas.drawCircle(px, py, strokeW * 0.28f, dotPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE)) {
                val dx = event.x - width / 2f
                val dy = event.y - height / 2f
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > width * 0.14f) {
                    var deg = Math.toDegrees(-atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (deg < 0f) deg += 360f
                    onPick(deg, 1f)
                    invalidate()
                }
                return true
            }
            return super.onTouchEvent(event)
        }
    }

    private class BrightnessGradientView(
        ctx: Context,
        private val hsv: FloatArray,
        private val onPickValue: (Float) -> Unit
    ) : View(ctx) {
        private var hue = hsv[0]
        private var sat = hsv[1]
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#5C85B5")
            strokeWidth = 3f
        }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#1976D2")
            strokeWidth = 4f
        }

        fun setBaseHueSat(h: Float, s: Float) {
            hue = h; sat = s
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val pure = Color.HSVToColor(floatArrayOf(hue, sat, 1f))
            barPaint.shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                intArrayOf(Color.BLACK, pure, Color.WHITE),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(1.5f, 1.5f, width - 1.5f, height - 1.5f, barPaint)
            canvas.drawRect(1.5f, 1.5f, width - 1.5f, height - 1.5f, borderPaint)
            val tx = (hsv[2] * 0.65f * width).coerceIn(6f, width - 6f)
            canvas.drawLine(tx, 2f, tx, height - 2f, thumbPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE)) {
                val ratio = (event.x / width.toFloat().coerceAtLeast(1f)).coerceIn(0.05f, 1f)
                hsv[2] = if (ratio <= 0.5f) ratio * 2f else 1f
                if (ratio > 0.5f) {
                    sat = (1f - (ratio - 0.5f) * 2f).coerceIn(0f, 1f)
                    hsv[1] = sat
                }
                onPickValue(hsv[2])
                invalidate()
                return true
            }
            return super.onTouchEvent(event)
        }
    }

    private fun showShapePickerDialog() {
        val kinds = ShapeKind.values()
        val labels = kinds.map { it.label }.toTypedArray()
        val idx = kinds.indexOf(canvasView.activeShapeKind).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("选择图形 / 直尺工具")
            .setSingleChoiceItems(labels, idx) { dlg, which ->
                canvasView.activeShapeKind = kinds[which]
                selectTool(ScratchTool.SHAPE)
                dlg.dismiss()
            }
            .show()
    }

    private fun showMoreActionsDialog() {
        val opts = arrayOf(
            "切换适配 / 满屏画布",
            "更换草稿纸底纹（${canvasView.paperBackground.label}）",
            "撤销上一笔",
            "重做下一笔",
            "保存到我的收藏夹"
        )
        AlertDialog.Builder(this)
            .setTitle("画布与更多操作")
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> canvasView.isCenteredFitMode = !canvasView.isCenteredFitMode
                    1 -> showPaperBackgroundDialog()
                    2 -> canvasView.undo()
                    3 -> canvasView.redo()
                    4 -> {
                        val bmp = canvasView.exportToBitmap()
                        AddToQuestionBookDialog.show(
                            activity = this,
                            sourceType = "SCRATCHPAD",
                            defaultTitle = "草稿本笔记",
                            defaultBook = "草稿本存档",
                            defaultTag = "草稿",
                            bitmap = bmp
                        )
                    }
                }
            }
            .show()
    }

    private fun showPaperBackgroundDialog() {
        val values = PaperBackground.values()
        val labels = values.map { it.label }.toTypedArray()
        val curIdx = values.indexOf(canvasView.paperBackground).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("选择草稿纸背景")
            .setSingleChoiceItems(labels, curIdx) { dlg, which ->
                canvasView.paperBackground = values[which]
                dlg.dismiss()
            }
            .show()
    }

    private fun scratchFile(): File = File(filesDir, "scratchpad_pages.json")

    private fun loadScratchpad() {
        strokes.clear()
        val f = scratchFile()
        if (f.exists()) {
            runCatching {
                val root = JSONObject(f.readText())
                val arr = root.optJSONArray("pages")
                if (arr != null && arr.length() > 0) {
                    val pArr = arr.getJSONArray(0)
                    for (j in 0 until pArr.length()) {
                        strokes.add(ScratchStroke.fromJson(pArr.getJSONObject(j)))
                    }
                }
            }
        }
    }

    private fun saveScratchpad() {
        runCatching {
            val pagesArr = JSONArray()
            val pArr = JSONArray()
            for (s in strokes) pArr.put(s.toJson())
            pagesArr.put(pArr)
            val root = JSONObject().put("pages", pagesArr)
            scratchFile().writeText(root.toString())
        }
    }

    private fun seedDemoMathScratch() {
        strokes.add(
            ScratchStroke(
                isText = true,
                color = Color.parseColor("#1A237E"),
                points = mutableListOf(0.10f to 0.14f),
                text = "二次函数草稿演算：\ny = x² - 4x + 3 = (x - 2)² - 1\n对称轴 x = 2，顶点 (2, -1)"
            )
        )
        val parabola = mutableListOf<Pair<Float, Float>>()
        for (i in 0..40) {
            val t = -1f + 2f * i / 40f
            val x = 0.42f + t * 0.22f
            val y = 0.72f - t * t * 0.28f
            parabola.add(x to y)
        }
        strokes.add(
            ScratchStroke(
                isText = false,
                color = Color.parseColor("#D32F2F"),
                width = 4.5f,
                points = parabola
            )
        )
        strokes.add(
            ScratchStroke(
                isText = false,
                color = Color.parseColor("#1976D2"),
                width = 3f,
                points = mutableListOf(0.18f to 0.65f, 0.68f to 0.65f)
            )
        )
        strokes.add(
            ScratchStroke(
                isText = false,
                color = Color.parseColor("#1976D2"),
                width = 3f,
                points = mutableListOf(0.42f to 0.36f, 0.42f to 0.80f)
            )
        )
        saveScratchpad()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
