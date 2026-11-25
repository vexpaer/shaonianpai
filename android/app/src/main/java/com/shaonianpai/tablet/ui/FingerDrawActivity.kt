package com.shaonianpai.tablet.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.UserProfileStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Recreates original FingerDrawActivity / 草稿本:
 * - Clean full-bleed default yellow ruled legal-pad canvas (NO top-left text, NO bottom page bar).
 * - Right-side vertical toolbar without thick brush or back button:
 *   铅笔, 文字, 橡皮 (silent pen-style pixel eraser, long-press to clear), 调色板 (full palette dialog, no preview dot),
 *   纸张背景 (crisp lined-paper icon), 撤销, 重做, 收藏.
 */
class FingerDrawActivity : AppCompatActivity() {

    private lateinit var canvasView: ScratchpadCanvasView
    private lateinit var llRightTools: LinearLayout

    private val strokes = mutableListOf<ScratchStroke>()
    private val toolButtons = mutableMapOf<ScratchTool, View>()

    private val paletteColors = listOf(
        Color.parseColor("#212121"), Color.parseColor("#1A237E"), Color.parseColor("#0D47A1"), Color.parseColor("#1976D2"),
        Color.parseColor("#00838F"), Color.parseColor("#2E7D32"), Color.parseColor("#558B2F"), Color.parseColor("#F9A825"),
        Color.parseColor("#EF6C00"), Color.parseColor("#D84315"), Color.parseColor("#D32F2F"), Color.parseColor("#C2185B"),
        Color.parseColor("#7B1FA2"), Color.parseColor("#512DA8"), Color.parseColor("#4E342E"), Color.parseColor("#546E7A"),
        Color.parseColor("#616161"), Color.parseColor("#0288D1"), Color.parseColor("#00897B"), Color.parseColor("#43A047"),
        Color.parseColor("#FB8C00"), Color.parseColor("#E53935"), Color.parseColor("#8E24AA"), Color.parseColor("#FFFFFF")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadScratchpad()
        if (intent.getBooleanExtra("demo_seed_scratch", false) && strokes.isEmpty()) {
            seedDemoMathScratch()
        }
        buildLayout()
        canvasView.setPageStrokes(strokes)
    }

    private fun buildLayout() {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = android.widget.RelativeLayout(this).apply {
            setBackgroundColor(Color.parseColor("#808080"))
        }

        // Right-side vertical toolbar matching activity_fingerdraw.xml
        val scrollRight = ScrollView(this).apply {
            id = View.generateViewId()
            setBackgroundColor(UserProfileStore.getThemePrimaryColor(this@FingerDrawActivity))
            isVerticalScrollBarEnabled = false
        }
        llRightTools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }
        scrollRight.addView(llRightTools)

        val rightParams = android.widget.RelativeLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
        }
        root.addView(scrollRight, rightParams)

        // Full-bleed scratchpad canvas (NO top-left badge, NO bottom page bar!)
        canvasView = ScratchpadCanvasView(this).apply {
            paperBackground = PaperBackground.DEFAULT
            onStrokesChanged = { saveScratchpad() }
            onRequestTextAt = { nx, ny -> promptTextAt(nx, ny) }
        }
        val canvasParams = android.widget.RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(android.widget.RelativeLayout.START_OF, scrollRight.id)
        }
        root.addView(canvasView, canvasParams)

        populateRightToolbar()
        setContentView(root)
        selectTool(ScratchTool.PENCIL)
    }

    private fun populateRightToolbar() {
        llRightTools.removeAllViews()
        toolButtons.clear()

        // Authentic right toolbar: NO thick brush, NO back button!
        val pencilBtn = makeSideBtn("铅笔", R.drawable.fd_ic_pencil, null) { selectTool(ScratchTool.PENCIL) }
        val textBtn = makeSideBtn("文字", R.drawable.fd_ic_text, null) { selectTool(ScratchTool.TEXT) }
        val eraserBtn = makeSideBtn("橡皮", R.drawable.fd_ic_eraser, null) {
            // Silent selection — pen-style pixel eraser with NO toast popup!
            selectTool(ScratchTool.ERASER)
        }
        eraserBtn.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空草稿纸")
                .setMessage("确定要清除当前全部笔迹吗？（可通过撤销恢复）")
                .setPositiveButton("清空") { _, _ -> canvasView.clearPage() }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        toolButtons[ScratchTool.PENCIL] = pencilBtn
        toolButtons[ScratchTool.TEXT] = textBtn
        toolButtons[ScratchTool.ERASER] = eraserBtn

        llRightTools.addView(pencilBtn)
        llRightTools.addView(textBtn)
        llRightTools.addView(eraserBtn)

        // Color Palette button (NO preview dot under icon; opens rich palette dialog)
        llRightTools.addView(makeSideBtn("调色板", R.drawable.fd_ic_colorize, null) { showColorPaletteDialog() })

        // Paper background selector with proper lined-paper icon
        llRightTools.addView(makeSideBtn("纸张", R.drawable.fd_ic_paper_bg, null) { showPaperBackgroundDialog() })

        // Undo / Redo
        llRightTools.addView(makeSideBtn("撤销", R.drawable.fx_annot_undo, Color.WHITE) { canvasView.undo() })
        llRightTools.addView(makeSideBtn("重做", R.drawable.fx_annot_redo, Color.WHITE) { canvasView.redo() })

        // Save to Favorites
        llRightTools.addView(makeSideBtn("收藏", R.drawable.qb_btn_notebook, null) {
            val bmp = canvasView.exportToBitmap()
            AddToQuestionBookDialog.show(
                activity = this,
                sourceType = "SCRATCHPAD",
                defaultTitle = "草稿本笔记",
                defaultBook = "草稿本存档",
                defaultTag = "草稿",
                bitmap = bmp
            )
        })
    }

    private fun selectTool(tool: ScratchTool) {
        canvasView.activeTool = tool
        toolButtons.forEach { (t, view) ->
            view.background = if (t == tool) {
                GradientDrawable().apply {
                    setColor(Color.parseColor("#44FFFFFF"))
                    setStroke(dp(1), Color.WHITE)
                    cornerRadius = dp(8).toFloat()
                }
            } else null
        }
    }

    private fun makeSideBtn(label: String, iconRes: Int, tint: Int?, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            addView(ImageView(this@FingerDrawActivity).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (tint != null) setColorFilter(tint)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            })
            addView(TextView(this@FingerDrawActivity).apply {
                text = label
                textSize = 10f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
            setOnClickListener { onClick() }
        }
    }

    /** Full interactive color palette + RGB tuner + stroke thickness dialog */
    private fun showColorPaletteDialog() {
        var pickedColor = canvasView.activeColor
        var pickedWidth = canvasView.activeStrokeWidth

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(8))
        }

        val previewStrip = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)).apply {
                bottomMargin = dp(12)
            }
            background = GradientDrawable().apply {
                setColor(pickedColor)
                setStroke(dp(1), Color.parseColor("#BDBDBD"))
                cornerRadius = dp(6).toFloat()
            }
        }
        root.addView(previewStrip)

        // 24-color 6x4 swatch grid
        val swatchViews = mutableListOf<Pair<Int, View>>()
        fun refreshPreview() {
            (previewStrip.background as? GradientDrawable)?.setColor(pickedColor)
            swatchViews.forEach { (c, v) ->
                (v.background as? GradientDrawable)?.setStroke(
                    if (c == pickedColor) dp(3) else dp(1),
                    if (c == pickedColor) Color.parseColor("#1976D2") else Color.parseColor("#CCCCCC")
                )
            }
        }

        val cols = 6
        for (rowIdx in 0 until (paletteColors.size + cols - 1) / cols) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }
            for (cIdx in 0 until cols) {
                val idx = rowIdx * cols + cIdx
                if (idx >= paletteColors.size) break
                val color = paletteColors[idx]
                val sw = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                        marginStart = dp(4)
                        marginEnd = dp(4)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        setStroke(dp(1), Color.parseColor("#CCCCCC"))
                    }
                }
                swatchViews.add(color to sw)
                row.addView(sw)
            }
            root.addView(row)
        }

        // RGB Fine Tuning Sliders
        val tvR = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#D32F2F")) }
        val tvG = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#2E7D32")) }
        val tvB = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#1976D2")) }
        val sbR = SeekBar(this).apply { max = 255 }
        val sbG = SeekBar(this).apply { max = 255 }
        val sbB = SeekBar(this).apply { max = 255 }

        fun syncSlidersFromColor() {
            sbR.progress = Color.red(pickedColor)
            sbG.progress = Color.green(pickedColor)
            sbB.progress = Color.blue(pickedColor)
            tvR.text = "红 (R): ${sbR.progress}"
            tvG.text = "绿 (G): ${sbG.progress}"
            tvB.text = "蓝 (B): ${sbB.progress}"
            refreshPreview()
        }

        swatchViews.forEach { (color, sw) ->
            sw.setOnClickListener {
                pickedColor = color
                syncSlidersFromColor()
            }
        }

        val rgbListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    pickedColor = Color.rgb(sbR.progress, sbG.progress, sbB.progress)
                    tvR.text = "红 (R): ${sbR.progress}"
                    tvG.text = "绿 (G): ${sbG.progress}"
                    tvB.text = "蓝 (B): ${sbB.progress}"
                    refreshPreview()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        sbR.setOnSeekBarChangeListener(rgbListener)
        sbG.setOnSeekBarChangeListener(rgbListener)
        sbB.setOnSeekBarChangeListener(rgbListener)

        root.addView(tvR)
        root.addView(sbR)
        root.addView(tvG)
        root.addView(sbG)
        root.addView(tvB)
        root.addView(sbB)

        // Pen thickness slider
        val tvW = TextView(this).apply {
            text = "画笔粗细：${pickedWidth.toInt()} px"
            textSize = 12.5f
            setTextColor(Color.parseColor("#424242"))
            setPadding(0, dp(8), 0, dp(2))
        }
        val sbW = SeekBar(this).apply {
            max = 14
            progress = (pickedWidth.toInt() - 1).coerceIn(0, 14)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    pickedWidth = (progress + 1).toFloat()
                    tvW.text = "画笔粗细：${pickedWidth.toInt()} px"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(tvW)
        root.addView(sbW)

        syncSlidersFromColor()

        val sv = ScrollView(this).apply { addView(root) }
        AlertDialog.Builder(this)
            .setTitle("调色板")
            .setView(sv)
            .setPositiveButton("确定") { _, _ ->
                canvasView.activeColor = pickedColor
                canvasView.activeStrokeWidth = pickedWidth
                if (canvasView.activeTool == ScratchTool.ERASER) {
                    selectTool(ScratchTool.PENCIL)
                }
            }
            .setNegativeButton("取消", null)
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

    private fun promptTextAt(nx: Float, ny: Float) {
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
