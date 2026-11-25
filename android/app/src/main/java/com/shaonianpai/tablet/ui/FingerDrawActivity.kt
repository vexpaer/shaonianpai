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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shaonianpai.tablet.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Recreates original FingerDrawActivity / 草稿本:
 * Multi-page scratchpad with right-side vertical toolbar (pencil, brush, text, eraser + long-press clear,
 * color picker, 6 paper backgrounds, undo/redo, save to QuestionBook/Favorites) and bottom page switcher.
 */
class FingerDrawActivity : AppCompatActivity() {

    private lateinit var canvasView: ScratchpadCanvasView
    private lateinit var tvPageInfo: TextView
    private lateinit var tvPaperBadge: TextView
    private lateinit var llRightTools: LinearLayout

    private val pages = mutableListOf<MutableList<ScratchStroke>>()
    private var currentPage = 0
    private val toolButtons = mutableMapOf<ScratchTool, View>()
    private var colorDotView: View? = null

    private val palette = listOf(
        "蓝黑墨水" to Color.parseColor("#1A237E"),
        "纯黑" to Color.parseColor("#212121"),
        "批改红" to Color.parseColor("#D32F2F"),
        "湖蓝" to Color.parseColor("#1976D2"),
        "草绿" to Color.parseColor("#2E7D32"),
        "醒目橙" to Color.parseColor("#EF6C00"),
        "重点紫" to Color.parseColor("#7B1FA2"),
        "荧光黄" to Color.parseColor("#FBC02D")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadScratchpadPages()
        if (intent.getBooleanExtra("demo_seed_scratch", false) && pages.first().isEmpty()) {
            seedDemoMathScratch()
        }
        buildLayout()
        showPage(currentPage)
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
            setBackgroundColor(Color.parseColor("#1976D2"))
            isVerticalScrollBarEnabled = false
        }
        llRightTools = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }
        scrollRight.addView(llRightTools)

        val rightParams = android.widget.RelativeLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
        }
        root.addView(scrollRight, rightParams)

        // Main scratchpad canvas to the left of right toolbar
        canvasView = ScratchpadCanvasView(this).apply {
            onStrokesChanged = { saveScratchpadPages() }
            onRequestTextAt = { nx, ny -> promptTextAt(nx, ny) }
        }
        val canvasParams = android.widget.RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(android.widget.RelativeLayout.START_OF, scrollRight.id)
        }
        root.addView(canvasView, canvasParams)

        // Top-left title & paper indicator badge (like original textViewQuestionIndex)
        tvPaperBadge = TextView(this).apply {
            text = "草稿本 · 横线"
            textSize = 14f
            setTextColor(Color.parseColor("#212121"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DDEEEEEE"))
                cornerRadius = dp(6).toFloat()
            }
            setOnClickListener { showPaperBackgroundDialog() }
        }
        val badgeParams = android.widget.RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
            setMargins(dp(12), dp(12), 0, 0)
        }
        root.addView(tvPaperBadge, badgeParams)

        // Bottom-center multi-page navigator (matching original layoutNewPage)
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EEFFFFFF"))
                setStroke(dp(1), Color.parseColor("#B0BEC5"))
                cornerRadius = dp(20).toFloat()
            }
            elevation = dp(4).toFloat()
        }
        val btnPrev = makeNavPillBtn("◀") {
            if (currentPage > 0) showPage(currentPage - 1)
            else Toast.makeText(this, "已是第 1 页", Toast.LENGTH_SHORT).show()
        }
        tvPageInfo = TextView(this).apply {
            text = "第 1 / 1 页"
            textSize = 13f
            setTextColor(Color.parseColor("#212121"))
            setPadding(dp(12), 0, dp(12), 0)
        }
        val btnNext = makeNavPillBtn("▶") {
            if (currentPage < pages.size - 1) showPage(currentPage + 1)
            else {
                pages.add(mutableListOf())
                showPage(pages.size - 1)
                saveScratchpadPages()
                Toast.makeText(this, "已新建第 ${pages.size} 页草稿纸", Toast.LENGTH_SHORT).show()
            }
        }
        val btnNewPage = makeNavPillBtn("+新页") {
            pages.add(mutableListOf())
            showPage(pages.size - 1)
            saveScratchpadPages()
            Toast.makeText(this, "已新建第 ${pages.size} 页草稿纸", Toast.LENGTH_SHORT).show()
        }
        navBar.addView(btnPrev)
        navBar.addView(tvPageInfo)
        navBar.addView(btnNext)
        navBar.addView(btnNewPage)

        val navParams = android.widget.RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
            addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL)
            bottomMargin = dp(14)
        }
        root.addView(navBar, navParams)

        populateRightToolbar()
        setContentView(root)
        selectTool(ScratchTool.PENCIL)
    }

    private fun populateRightToolbar() {
        llRightTools.removeAllViews()
        toolButtons.clear()

        // 1. Back / exit
        llRightTools.addView(makeSideBtn("返回", R.drawable.ic_arrow_back_white_24dp, null) { finish() })

        // 2. Drawing tools using authentic original icons
        val pencilBtn = makeSideBtn("铅笔", R.drawable.fd_ic_pencil, null) { selectTool(ScratchTool.PENCIL) }
        val brushBtn = makeSideBtn("粗笔", R.drawable.fd_ic_brush, null) { selectTool(ScratchTool.BRUSH) }
        val textBtn = makeSideBtn("文字", R.drawable.fd_ic_text, null) { selectTool(ScratchTool.TEXT) }
        val eraserBtn = makeSideBtn("橡皮", R.drawable.fd_ic_eraser, null) {
            selectTool(ScratchTool.ERASER)
            Toast.makeText(this, "橡皮擦：划过笔迹擦除，长按清空本页", Toast.LENGTH_SHORT).show()
        }
        eraserBtn.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空当前草稿页")
                .setMessage("确定要清除第 ${currentPage + 1} 页的全部笔迹吗？（可通过撤销恢复）")
                .setPositiveButton("清空") { _, _ -> canvasView.clearPage() }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        toolButtons[ScratchTool.PENCIL] = pencilBtn
        toolButtons[ScratchTool.BRUSH] = brushBtn
        toolButtons[ScratchTool.TEXT] = textBtn
        toolButtons[ScratchTool.ERASER] = eraserBtn

        llRightTools.addView(pencilBtn)
        llRightTools.addView(brushBtn)
        llRightTools.addView(textBtn)
        llRightTools.addView(eraserBtn)

        // 3. Color picker with live dot
        val colorContainer = makeSideBtn("颜色", R.drawable.fd_ic_colorize, null) { showColorDialog() }
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { topMargin = dp(2) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(canvasView.activeColor)
                setStroke(dp(1), Color.WHITE)
            }
        }
        colorDotView = dot
        (colorContainer as LinearLayout).addView(dot)
        llRightTools.addView(colorContainer)

        // 4. Paper background switcher (6 original backgrounds)
        llRightTools.addView(makeSideBtn("纸张", R.drawable.fd_ic_rotate, null) { showPaperBackgroundDialog() })

        // 5. Undo / Redo
        llRightTools.addView(makeSideBtn("撤销", R.drawable.fx_annot_undo, Color.WHITE) { canvasView.undo() })
        llRightTools.addView(makeSideBtn("重做", R.drawable.fx_annot_redo, Color.WHITE) { canvasView.redo() })

        // 6. Save to Favorites / QuestionBook
        llRightTools.addView(makeSideBtn("收藏", R.drawable.qb_btn_notebook, null) {
            val bmp = canvasView.exportToBitmap()
            AddToQuestionBookDialog.show(
                activity = this,
                sourceType = "SCRATCHPAD",
                defaultTitle = "草稿本第 ${currentPage + 1} 页",
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

    private fun showColorDialog() {
        val names = palette.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择画笔颜色")
            .setItems(names) { _, which ->
                val col = palette[which].second
                canvasView.activeColor = col
                colorDotView?.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(col)
                    setStroke(dp(1), Color.WHITE)
                }
            }
            .show()
    }

    private fun showPaperBackgroundDialog() {
        val bgs = PaperBackground.values()
        val labels = bgs.map { it.label }.toTypedArray()
        val curIdx = bgs.indexOf(canvasView.paperBackground).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("草稿纸背景（原版 6 种样式）")
            .setSingleChoiceItems(labels, curIdx) { dlg, which ->
                canvasView.paperBackground = bgs[which]
                tvPaperBadge.text = "草稿本 · ${bgs[which].label}"
                getSharedPreferences("shaonianpai_prefs", MODE_PRIVATE)
                    .edit().putString("scratch_bg", bgs[which].name).apply()
                dlg.dismiss()
            }
            .show()
    }

    private fun promptTextAt(nx: Float, ny: Float) {
        val et = EditText(this).apply {
            hint = "在这里输入文字，支持多行"
            minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle("插入文字")
            .setView(et)
            .setPositiveButton("确定") { _, _ ->
                canvasView.addTextStroke(nx, ny, et.text.toString().trim())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPage(index: Int) {
        currentPage = index.coerceIn(0, pages.size - 1)
        canvasView.setPageStrokes(pages[currentPage])
        tvPageInfo.text = "第 ${currentPage + 1} / ${pages.size} 页"
        val savedBg = getSharedPreferences("shaonianpai_prefs", MODE_PRIVATE)
            .getString("scratch_bg", PaperBackground.HOZ_LINE.name)
        val bg = runCatching { PaperBackground.valueOf(savedBg ?: "") }.getOrDefault(PaperBackground.HOZ_LINE)
        canvasView.paperBackground = bg
        tvPaperBadge.text = "草稿本 · ${bg.label}"
    }

    private fun scratchFile(): File = File(File(filesDir, "scratchpad").apply { mkdirs() }, "pages.json")

    private fun loadScratchpadPages() {
        pages.clear()
        val f = scratchFile()
        if (f.exists()) {
            runCatching {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val pArr = arr.getJSONArray(i)
                    val list = mutableListOf<ScratchStroke>()
                    for (j in 0 until pArr.length()) {
                        list.add(ScratchStroke.fromJson(pArr.getJSONObject(j)))
                    }
                    pages.add(list)
                }
            }
        }
        if (pages.isEmpty()) pages.add(mutableListOf())
    }

    private fun saveScratchpadPages() {
        val root = JSONArray()
        for (p in pages) {
            val pArr = JSONArray()
            for (s in p) pArr.put(s.toJson())
            root.put(pArr)
        }
        runCatching { scratchFile().writeText(root.toString()) }
    }

    private fun seedDemoMathScratch() {
        val p = pages.first()
        p.add(
            ScratchStroke(
                isText = true,
                color = Color.parseColor("#1A237E"),
                points = mutableListOf(0.08f to 0.16f),
                text = "二次函数草稿演算：y = x² - 4x + 3 = (x - 2)² - 1\n对称轴 x = 2，顶点 (2, -1)"
            )
        )
        // Draw simple parabola sketch & axis
        p.add(
            ScratchStroke(
                color = Color.parseColor("#455A64"),
                width = 3f,
                points = mutableListOf(0.14f to 0.62f, 0.72f to 0.62f)
            )
        )
        p.add(
            ScratchStroke(
                color = Color.parseColor("#455A64"),
                width = 3f,
                points = mutableListOf(0.24f to 0.34f, 0.24f to 0.78f)
            )
        )
        val parabola = mutableListOf<Pair<Float, Float>>()
        for (i in 0..24) {
            val t = -1.5f + i * (3.0f / 24f)
            val x = 0.42f + t * 0.11f
            val y = 0.70f - (t * t) * 0.15f
            parabola.add(x to y)
        }
        p.add(ScratchStroke(color = Color.parseColor("#D32F2F"), width = 5f, points = parabola))
        p.add(
            ScratchStroke(
                isText = true,
                color = Color.parseColor("#D32F2F"),
                points = mutableListOf(0.46f to 0.76f),
                text = "顶点 (2, -1) 最小值"
            )
        )
        saveScratchpadPages()
    }

    private fun makeSideBtn(label: String, iconRes: Int, tint: Int?, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            addView(ImageView(this@FingerDrawActivity).apply {
                setImageResource(iconRes)
                if (tint != null) setColorFilter(tint)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                scaleType = ImageView.ScaleType.FIT_CENTER
            })
            addView(TextView(this@FingerDrawActivity).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextColor(Color.WHITE)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun makeNavPillBtn(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(Color.parseColor("#1976D2"))
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
