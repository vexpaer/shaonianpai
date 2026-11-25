package com.shaonianpai.tablet.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.AssetItem
import com.shaonianpai.tablet.data.Repository
import com.shaonianpai.tablet.pdf.AnnotTool
import com.shaonianpai.tablet.pdf.FoxitPalette
import com.shaonianpai.tablet.pdf.OutlineEntry
import com.shaonianpai.tablet.pdf.PageTextIndex
import com.shaonianpai.tablet.pdf.PdfAnnotCanvasView
import com.shaonianpai.tablet.pdf.PdfAnnotation
import com.shaonianpai.tablet.pdf.PdfAnnotationStore
import com.shaonianpai.tablet.pdf.PdfBookmark
import com.shaonianpai.tablet.pdf.PdfBoxTextExtractor
import com.shaonianpai.tablet.pdf.PdfDocState
import com.shaonianpai.tablet.pdf.PdfOpenCheck
import com.shaonianpai.tablet.pdf.PointNorm
import com.shaonianpai.tablet.pdf.RectNorm
import com.shaonianpai.tablet.pdf.ReplyItem
import com.shaonianpai.tablet.pdf.SearchHit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentViewActivity : AppCompatActivity() {

    private lateinit var asset: AssetItem
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var pdfCanvas: PdfAnnotCanvasView
    private lateinit var topBarContainer: View
    private lateinit var bottomBar: View
    private lateinit var mainBottomBar: View
    private lateinit var annotBottomBar: View
    private lateinit var llAnnotQuickStrip: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvPdfTitle: TextView
    private lateinit var tvPdfSubtitle: TextView
    private lateinit var tvPageBadge: TextView
    private lateinit var btnBookmarkToggle: ImageButton
    private lateinit var btnFloatPrev: ImageView
    private lateinit var btnFloatNext: ImageView

    // Search views
    private lateinit var searchBar: View
    private lateinit var etSearchQuery: EditText
    private lateinit var tvSearchCounter: TextView

    // Tool prompt pill & popup sheets
    private lateinit var toolPromptBar: View
    private lateinit var tvActiveToolHint: TextView
    private lateinit var btnContinuousToggle: ImageView
    private lateinit var tvContinuousLabel: TextView
    private lateinit var moreToolsSheet: View
    private lateinit var propertySheet: View
    private lateinit var viewSettingsSheet: View

    // Property controls
    private lateinit var llColorPalette: LinearLayout
    private lateinit var vStrokePreviewDot: View
    private lateinit var tvStrokeWidthVal: TextView
    private lateinit var sbLineWidth: SeekBar
    private lateinit var llOpacityOptions: LinearLayout
    private var quickPropColorDot: View? = null
    private var quickUndoView: View? = null
    private var quickRedoView: View? = null
    private val quickToolViews = mutableMapOf<AnnotTool, View>()

    // Side panel views
    private lateinit var tvSidePanelTitle: TextView
    private lateinit var btnSidePanelClear: TextView
    private lateinit var tabSideAnnot: TextView
    private lateinit var tabSideOutline: TextView
    private lateinit var tabSideBookmark: TextView
    private lateinit var lvSidePanel: ListView
    private lateinit var tvSidePanelEmpty: TextView

    // Document & annotation state
    private var sourceFile: File? = null
    private var renderFile: File? = null
    private var docPassword: String? = null
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var currentPage = 0
    private var pageCount = 0

    private lateinit var docState: PdfDocState
    private val undoStack = ArrayDeque<List<PdfAnnotation>>()
    private val redoStack = ArrayDeque<List<PdfAnnotation>>()
    private val pageTextCache = mutableMapOf<Int, PageTextIndex>()
    private var outlineItems: List<OutlineEntry> = emptyList()
    private var searchHits: List<SearchHit> = emptyList()
    private var currentSearchIdx = -1

    private var annotMode = false
    private var continuousMode = true
    private var chromeVisible = true
    private var nightMode = false
    private var rotationLocked = false
    private var activeSideTab = 0 // 0=annot, 1=outline, 2=bookmark
    private var selectedStampIdx = 0

    private val toolColors = mutableMapOf<AnnotTool, Int>()
    private val toolAlphas = mutableMapOf<AnnotTool, Int>()
    private val toolWidths = mutableMapOf<AnnotTool, Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documentview)

        @Suppress("DEPRECATION")
        asset = (intent.getSerializableExtra("asset") as? AssetItem)
            ?: Repository.builtInFallbackTasks().first().assets.first()

        docState = PdfAnnotationStore.load(this, asset.assetId)
        initToolDefaults()
        bindViews()
        setupSidePanelWidth()
        setupTopBar()
        setupBottomBars()
        setupMoreToolsSheet()
        setupPropertySheet()
        setupViewSettingsSheet()
        setupSidePanel()
        setupCanvasCallbacks()

        val requestedPage = intent.getIntExtra("page", -1)
        currentPage = if (requestedPage >= 0) requestedPage else Repository.getSavedPdfPage(this, asset.assetId)

        if (intent.getBooleanExtra("demo_seed_annots", false)) {
            seedDemoAnnotationsIfEmpty()
        }

        loadPdfDocument()
    }

    private fun initToolDefaults() {
        val finePen = com.shaonianpai.tablet.data.UserProfileStore.getFinePenWidth(this)
        for (t in AnnotTool.values()) {
            toolColors[t] = FoxitPalette.defaultColorFor(t)
            toolAlphas[t] = FoxitPalette.defaultAlphaFor(t)
            toolWidths[t] = when (t) {
                AnnotTool.PENCIL -> finePen
                AnnotTool.UNDERLINE, AnnotTool.SQUIGGLY, AnnotTool.STRIKEOUT -> 3f
                else -> 4f
            }
        }
    }

    private fun bindViews() {
        drawerLayout = findViewById(R.id.pdfDrawerLayout)
        pdfCanvas = findViewById(R.id.pdfCanvas)
        topBarContainer = findViewById(R.id.pdfTopBarContainer)
        bottomBar = findViewById(R.id.pdfBottomBar)
        mainBottomBar = findViewById(R.id.pdfMainBottomBar)
        annotBottomBar = findViewById(R.id.pdfAnnotBottomBar)
        llAnnotQuickStrip = findViewById(R.id.llAnnotQuickStrip)
        progressBar = findViewById(R.id.progressBar1)
        tvPdfTitle = findViewById(R.id.tvPdfTitle)
        tvPdfSubtitle = findViewById(R.id.tvPdfSubtitle)
        tvPageBadge = findViewById(R.id.tvPageBadge)
        btnBookmarkToggle = findViewById(R.id.btnBookmarkToggle)
        btnFloatPrev = findViewById(R.id.btnFloatPrev)
        btnFloatNext = findViewById(R.id.btnFloatNext)

        searchBar = findViewById(R.id.pdfSearchBar)
        etSearchQuery = findViewById(R.id.etSearchQuery)
        tvSearchCounter = findViewById(R.id.tvSearchCounter)

        toolPromptBar = findViewById(R.id.pdfToolPromptBar)
        tvActiveToolHint = findViewById(R.id.tvActiveToolHint)
        btnContinuousToggle = findViewById(R.id.btnContinuousToggle)
        tvContinuousLabel = findViewById(R.id.tvContinuousLabel)
        moreToolsSheet = findViewById(R.id.pdfMoreToolsSheet)
        propertySheet = findViewById(R.id.pdfPropertySheet)
        viewSettingsSheet = findViewById(R.id.pdfViewSettingsSheet)

        llColorPalette = findViewById(R.id.llColorPalette)
        vStrokePreviewDot = findViewById(R.id.vStrokePreviewDot)
        tvStrokeWidthVal = findViewById(R.id.tvStrokeWidthVal)
        sbLineWidth = findViewById(R.id.sbLineWidth)
        llOpacityOptions = findViewById(R.id.llOpacityOptions)

        tvSidePanelTitle = findViewById(R.id.tvSidePanelTitle)
        btnSidePanelClear = findViewById(R.id.btnSidePanelClear)
        tabSideAnnot = findViewById(R.id.tabSideAnnot)
        tabSideOutline = findViewById(R.id.tabSideOutline)
        tabSideBookmark = findViewById(R.id.tabSideBookmark)
        lvSidePanel = findViewById(R.id.lvSidePanel)
        tvSidePanelEmpty = findViewById(R.id.tvSidePanelEmpty)

        tvPdfTitle.text = asset.title
        updateSubtitle()
    }

    private fun setupSidePanelWidth() {
        val side = findViewById<View>(R.id.pdfSidePanel)
        val dm = resources.displayMetrics
        val targetW = (dm.widthPixels * 0.84f).toInt().coerceAtMost(dp(340))
        side.layoutParams = side.layoutParams.apply { width = targetW }
    }

    private fun setupTopBar() {
        findViewById<View>(R.id.btnPdfBack).setOnClickListener { finish() }

        btnBookmarkToggle.setOnClickListener {
            toggleBookmarkCurrentPage()
        }

        val goToPageBar = findViewById<View>(R.id.pdfGoToPageBar)
        val etGoToPage = findViewById<EditText>(R.id.etGoToPage)
        fun executeGoToPage() {
            val target = etGoToPage.text.toString().trim().toIntOrNull()
            if (target != null && pageCount > 0) {
                renderPage((target - 1).coerceIn(0, pageCount - 1))
                goToPageBar.visibility = View.GONE
            } else {
                Toast.makeText(this, "请输入 1 - $pageCount 之间的页码", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.btnGoToPageClear).setOnClickListener {
            if (etGoToPage.text.isNotEmpty()) etGoToPage.setText("") else goToPageBar.visibility = View.GONE
        }
        findViewById<View>(R.id.btnGoToPageConfirm).setOnClickListener { executeGoToPage() }
        etGoToPage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                executeGoToPage()
                true
            } else false
        }

        findViewById<View>(R.id.btnSearchToggle).setOnClickListener {
            goToPageBar.visibility = View.GONE
            if (searchBar.visibility == View.VISIBLE) {
                closeSearch()
            } else {
                hideAllPopupSheets()
                searchBar.visibility = View.VISIBLE
                etSearchQuery.requestFocus()
            }
        }

        findViewById<View>(R.id.btnSearchRun).setOnClickListener { runFullTextSearch() }
        etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runFullTextSearch()
                true
            } else false
        }
        findViewById<View>(R.id.btnSearchPrev).setOnClickListener { stepSearch(-1) }
        findViewById<View>(R.id.btnSearchNext).setOnClickListener { stepSearch(1) }
        findViewById<View>(R.id.btnSearchClose).setOnClickListener { closeSearch() }

        findViewById<View>(R.id.btnExportPdf).setOnClickListener { exportAnnotatedPdf() }
        findViewById<View>(R.id.btnOpenExternal).setOnClickListener {
            val opts = arrayOf("导出带标准批注的 PDF", "用系统 APP 打开", "跳转到指定页", "查看文档信息")
            AlertDialog.Builder(this)
                .setTitle("文档操作")
                .setItems(opts) { _, which ->
                    when (which) {
                        0 -> exportAnnotatedPdf()
                        1 -> openExternalPdf()
                        2 -> {
                            searchBar.visibility = View.GONE
                            goToPageBar.visibility = View.VISIBLE
                            etGoToPage.setText("${currentPage + 1}")
                            etGoToPage.requestFocus()
                        }
                        3 -> {
                            AlertDialog.Builder(this)
                                .setTitle("文档信息")
                                .setMessage(
                                    "标题：${asset.title}\n" +
                                        "页数：共 $pageCount 页\n" +
                                        "大小：${Repository.formatBytes(asset.bytes)}\n" +
                                        "批注数：${docState.annotations.size} 条\n" +
                                        "书签数：${docState.bookmarks.size} 个"
                                )
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    }
                }
                .show()
        }

        tvPageBadge.setOnClickListener {
            searchBar.visibility = View.GONE
            val show = goToPageBar.visibility != View.VISIBLE
            goToPageBar.visibility = if (show) View.VISIBLE else View.GONE
            if (show) {
                etGoToPage.setText("${currentPage + 1}")
                etGoToPage.selectAll()
                etGoToPage.requestFocus()
            }
        }
        btnFloatPrev.setOnClickListener { navigatePage(-1) }
        btnFloatNext.setOnClickListener { navigatePage(1) }

        val toggleContinuous = View.OnClickListener {
            continuousMode = !continuousMode
            updateContinuousUi()
            Toast.makeText(
                this,
                if (continuousMode) "已开启连续批注模式" else "已关闭连续批注（单次后回到浏览）",
                Toast.LENGTH_SHORT
            ).show()
        }
        btnContinuousToggle.setOnClickListener(toggleContinuous)
        tvContinuousLabel.setOnClickListener(toggleContinuous)
        updateContinuousUi()
    }

    private fun updateContinuousUi() {
        btnContinuousToggle.setImageResource(
            if (continuousMode) R.drawable.fx_continuous_on else R.drawable.fx_continuous_off
        )
        val tint = if (continuousMode) Color.parseColor("#81D4FA") else Color.parseColor("#90A4AE")
        btnContinuousToggle.setColorFilter(tint)
        tvContinuousLabel.setTextColor(tint)
    }

    private fun setupBottomBars() {
        findViewById<View>(R.id.btnBarPanel).setOnClickListener {
            hideAllPopupSheets()
            refreshSidePanelList()
            drawerLayout.openDrawer(GravityCompat.START)
        }
        findViewById<View>(R.id.btnBarEdit).setOnClickListener {
            enterAnnotationMode(AnnotTool.HIGHLIGHT)
        }
        findViewById<View>(R.id.btnBarQuickPencil).setOnClickListener {
            enterAnnotationMode(AnnotTool.PENCIL)
        }
        findViewById<View>(R.id.btnBarQuickHighlight).setOnClickListener {
            enterAnnotationMode(AnnotTool.HIGHLIGHT)
        }
        findViewById<View>(R.id.btnBarSetting).setOnClickListener {
            val showing = viewSettingsSheet.visibility == View.VISIBLE
            hideAllPopupSheets()
            if (!showing) viewSettingsSheet.visibility = View.VISIBLE
        }
        findViewById<View>(R.id.btnBarThumbnails).setOnClickListener {
            hideAllPopupSheets()
            showThumbnailsDialog()
        }

        // Build horizontally scrollable Foxit quick annotation bar
        llAnnotQuickStrip.removeAllViews()
        quickToolViews.clear()

        // 1. Done button
        llAnnotQuickStrip.addView(makeActionChip("完成", null, Color.parseColor("#179CD8"), true) {
            exitAnnotationMode()
        })

        // 2. Quick tools
        val quickTools = listOf(
            AnnotTool.HIGHLIGHT to R.drawable.fx_annot_highlight,
            AnnotTool.UNDERLINE to R.drawable.fx_annot_underline,
            AnnotTool.SQUIGGLY to R.drawable.fx_annot_squiggly,
            AnnotTool.STRIKEOUT to R.drawable.fx_annot_strikeout,
            AnnotTool.PENCIL to R.drawable.fx_annot_pencil,
            AnnotTool.NOTE to R.drawable.fx_annot_note,
            AnnotTool.TYPEWRITER to R.drawable.fx_annot_typewriter,
            AnnotTool.ERASER to R.drawable.fx_annot_eraser
        )
        for ((tool, iconRes) in quickTools) {
            val v = makeToolButton(tool.label, iconRes) {
                selectAnnotTool(tool)
            }
            quickToolViews[tool] = v
            llAnnotQuickStrip.addView(v)
        }

        // 3. More Tools (opens mt_moretools 3-group sheet)
        llAnnotQuickStrip.addView(makeActionChip("更多工具", null, Color.parseColor("#37474F"), false) {
            val showing = moreToolsSheet.visibility == View.VISIBLE
            hideAllPopupSheets()
            if (!showing) moreToolsSheet.visibility = View.VISIBLE
        })

        // 4. Property Bar button (live color circle)
        val propBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnClickListener {
                val showing = propertySheet.visibility == View.VISIBLE
                hideAllPopupSheets()
                if (!showing) {
                    refreshPropertySheetUI()
                    propertySheet.visibility = View.VISIBLE
                }
            }
        }
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        }
        quickPropColorDot = dot
        val propLabel = TextView(this).apply {
            text = "属性"
            textSize = 10f
            setTextColor(Color.parseColor("#424242"))
            setPadding(0, dp(2), 0, 0)
        }
        propBox.addView(dot)
        propBox.addView(propLabel)
        llAnnotQuickStrip.addView(propBox)

        // 5. Undo / Redo
        val undoBtn = makeToolButton("撤销", R.drawable.fx_annot_undo) { performUndo() }
        val redoBtn = makeToolButton("重做", R.drawable.fx_annot_redo) { performRedo() }
        quickUndoView = undoBtn
        quickRedoView = redoBtn
        llAnnotQuickStrip.addView(undoBtn)
        llAnnotQuickStrip.addView(redoBtn)
        updateUndoRedoUI()
    }

    private fun setupMoreToolsSheet() {
        findViewById<View>(R.id.btnCloseMoreTools).setOnClickListener {
            moreToolsSheet.visibility = View.GONE
        }

        val llMarkup = findViewById<LinearLayout>(R.id.llSheetMarkup)
        val llDrawing = findViewById<LinearLayout>(R.id.llSheetDrawing)
        val llOthers = findViewById<LinearLayout>(R.id.llSheetOthers)

        listOf(
            AnnotTool.HIGHLIGHT to R.drawable.fx_annot_highlight,
            AnnotTool.UNDERLINE to R.drawable.fx_annot_underline,
            AnnotTool.SQUIGGLY to R.drawable.fx_annot_squiggly,
            AnnotTool.STRIKEOUT to R.drawable.fx_annot_strikeout
        ).forEach { (tool, icon) ->
            llMarkup.addView(makeSheetToolItem(tool.label, icon) {
                moreToolsSheet.visibility = View.GONE
                selectAnnotTool(tool)
            })
        }

        listOf(
            AnnotTool.PENCIL to R.drawable.fx_annot_pencil,
            AnnotTool.ERASER to R.drawable.fx_annot_eraser,
            AnnotTool.LINE to R.drawable.fx_annot_line,
            AnnotTool.ARROW to R.drawable.fx_annot_arrow,
            AnnotTool.RECT to R.drawable.fx_annot_square,
            AnnotTool.OVAL to R.drawable.fx_annot_circle
        ).forEach { (tool, icon) ->
            llDrawing.addView(makeSheetToolItem(tool.label, icon) {
                moreToolsSheet.visibility = View.GONE
                selectAnnotTool(tool)
            })
        }

        listOf(
            AnnotTool.NOTE to R.drawable.fx_annot_note,
            AnnotTool.TYPEWRITER to R.drawable.fx_annot_typewriter,
            AnnotTool.TEXTBOX to R.drawable.fx_annot_textbox,
            AnnotTool.CALLOUT to R.drawable.fx_annot_callout,
            AnnotTool.STAMP to R.drawable.fx_annot_stamp
        ).forEach { (tool, icon) ->
            llOthers.addView(makeSheetToolItem(tool.label, icon) {
                moreToolsSheet.visibility = View.GONE
                if (tool == AnnotTool.STAMP) {
                    showStampPickerDialog()
                } else {
                    selectAnnotTool(tool)
                }
            })
        }
    }

    private fun setupPropertySheet() {
        findViewById<View>(R.id.btnCloseProperty).setOnClickListener {
            propertySheet.visibility = View.GONE
        }
        sbLineWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val w = (progress + 1).toFloat()
                val t = currentConfigurableTool()
                toolWidths[t] = w
                pdfCanvas.activeStrokeWidth = w
                tvStrokeWidthVal.text = "${w.toInt()}px"
                updateStrokePreviewDot()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        refreshPropertySheetUI()
    }

    private fun refreshPropertySheetUI() {
        val tool = currentConfigurableTool()
        val curColor = toolColors[tool] ?: FoxitPalette.defaultColorFor(tool)
        val curAlpha = toolAlphas[tool] ?: FoxitPalette.defaultAlphaFor(tool)
        val curWidth = toolWidths[tool] ?: 4f

        // Color palette row
        llColorPalette.removeAllViews()
        for (c in FoxitPalette.COLORS) {
            val selected = c == curColor
            val wrapper = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                    marginEnd = dp(6)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(if (selected) dp(3) else dp(1), if (selected) Color.parseColor("#179CD8") else Color.parseColor("#FFBDBDBD"))
                }
                setOnClickListener {
                    toolColors[tool] = c
                    pdfCanvas.activeColor = c
                    refreshPropertySheetUI()
                }
            }
            if (selected) {
                wrapper.addView(TextView(this).apply {
                    text = "✓"
                    textSize = 14f
                    setTextColor(if (c == Color.parseColor("#FFEB3B")) Color.BLACK else Color.WHITE)
                })
            }
            llColorPalette.addView(wrapper)
        }

        // Width controls
        sbLineWidth.progress = (curWidth.toInt() - 1).coerceIn(0, 11)
        tvStrokeWidthVal.text = "${curWidth.toInt()}px"
        updateStrokePreviewDot()

        // Opacity row (25%, 50%, 75%, 100%)
        llOpacityOptions.removeAllViews()
        val opacities = listOf(
            25 to R.drawable.fx_opacity_25,
            50 to R.drawable.fx_opacity_50,
            75 to R.drawable.fx_opacity_75,
            100 to R.drawable.fx_opacity_100
        )
        for ((pct, iconRes) in opacities) {
            val selected = curAlpha == pct
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (selected) setBackgroundResource(R.drawable.bg_fx_tool_selected)
                setOnClickListener {
                    toolAlphas[tool] = pct
                    pdfCanvas.activeAlpha = pct
                    refreshPropertySheetUI()
                }
            }
            box.addView(ImageView(this).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            })
            box.addView(TextView(this).apply {
                text = "$pct%"
                textSize = 11f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextColor(if (selected) Color.parseColor("#179CD8") else Color.parseColor("#515151"))
            })
            llOpacityOptions.addView(box)
        }

        // Sync quick-bar color dot
        quickPropColorDot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(curColor)
            setStroke(dp(2), Color.parseColor("#FF9E9E9E"))
        }
    }

    private fun updateStrokePreviewDot() {
        val tool = currentConfigurableTool()
        val curColor = toolColors[tool] ?: Color.RED
        val w = (toolWidths[tool] ?: 4f).toInt().coerceIn(1, 12)
        val sz = dp(6 + w * 2).coerceAtMost(dp(26))
        vStrokePreviewDot.layoutParams = vStrokePreviewDot.layoutParams.apply {
            width = sz
            height = sz
        }
        vStrokePreviewDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(curColor)
        }
    }

    private fun setupViewSettingsSheet() {
        findViewById<View>(R.id.btnCloseViewSettings).setOnClickListener {
            viewSettingsSheet.visibility = View.GONE
        }
        val ivNight = findViewById<ImageView>(R.id.ivViewNightIcon)
        val tvNight = findViewById<TextView>(R.id.tvViewNightLabel)
        findViewById<View>(R.id.btnViewNightMode).setOnClickListener {
            nightMode = !nightMode
            pdfCanvas.nightMode = nightMode
            ivNight.setImageResource(if (nightMode) R.drawable.fx_daynight_night else R.drawable.fx_daynight_day)
            tvNight.text = if (nightMode) "护眼夜间" else "日间模式"
        }

        val tvLock = findViewById<TextView>(R.id.tvViewLockLabel)
        findViewById<View>(R.id.btnViewLockScreen).setOnClickListener {
            rotationLocked = !rotationLocked
            requestedOrientation = if (rotationLocked) {
                ActivityInfo.SCREEN_ORIENTATION_LOCKED
            } else {
                ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            }
            tvLock.text = if (rotationLocked) "已锁方向" else "自动旋转"
            Toast.makeText(this, if (rotationLocked) "已锁定当前屏幕方向" else "已恢复自由旋转", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnViewResetZoom).setOnClickListener {
            pdfCanvas.resetZoom()
            viewSettingsSheet.visibility = View.GONE
        }

        findViewById<SeekBar>(R.id.sbBrightness).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val lp = window.attributes
                lp.screenBrightness = (progress.coerceAtLeast(10) / 100f)
                window.attributes = lp
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSidePanel() {
        tabSideAnnot.setOnClickListener { selectSideTab(0) }
        tabSideOutline.setOnClickListener { selectSideTab(1) }
        tabSideBookmark.setOnClickListener { selectSideTab(2) }

        btnSidePanelClear.setOnClickListener {
            if (activeSideTab == 0) {
                if (docState.annotations.isEmpty()) return@setOnClickListener
                AlertDialog.Builder(this)
                    .setTitle("清空全部批注")
                    .setMessage("确定要清空当前文档的全部 ${docState.annotations.size} 条批注吗？（可通过撤销恢复）")
                    .setPositiveButton("清空") { _, _ ->
                        pushUndoSnapshot()
                        docState.annotations.clear()
                        saveAndRefreshCanvas()
                        refreshSidePanelList()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else if (activeSideTab == 2) {
                if (docState.bookmarks.isEmpty()) return@setOnClickListener
                docState.bookmarks.clear()
                PdfAnnotationStore.save(this, docState)
                updateBookmarkIcon()
                refreshSidePanelList()
            }
        }
    }

    private fun selectSideTab(tab: Int) {
        activeSideTab = tab
        val blue = Color.parseColor("#179CD8")
        val gray = Color.parseColor("#666666")
        tabSideAnnot.setTextColor(if (tab == 0) blue else gray)
        tabSideOutline.setTextColor(if (tab == 1) blue else gray)
        tabSideBookmark.setTextColor(if (tab == 2) blue else gray)
        tvSidePanelTitle.text = when (tab) {
            0 -> "批注 (${docState.annotations.size})"
            1 -> "目录"
            else -> "书签 (${docState.bookmarks.size})"
        }
        btnSidePanelClear.visibility = if (tab == 1) View.GONE else View.VISIBLE
        refreshSidePanelList()
    }

    private fun refreshSidePanelList() {
        tvSidePanelTitle.text = when (activeSideTab) {
            0 -> "批注 (${docState.annotations.size})"
            1 -> "目录"
            else -> "书签 (${docState.bookmarks.size})"
        }

        when (activeSideTab) {
            0 -> {
                val sorted = docState.annotations.sortedWith(compareBy<PdfAnnotation> { it.pageIndex }.thenBy { it.createdAt })
                tvSidePanelEmpty.text = "暂无批注，点击底部「批注」开始标记"
                tvSidePanelEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
                lvSidePanel.adapter = object : BaseAdapter() {
                    override fun getCount() = sorted.size
                    override fun getItem(p: Int) = sorted[p]
                    override fun getItemId(p: Int) = p.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val a = sorted[position]
                        val row = LinearLayout(this@DocumentViewActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(12), dp(10), dp(10), dp(10))
                        }
                        val icon = ImageView(this@DocumentViewActivity).apply {
                            setImageResource(iconForAnnotationType(a.type))
                            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                        }
                        val info = LinearLayout(this@DocumentViewActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                marginStart = dp(10)
                            }
                        }
                        val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(a.updatedAt))
                        info.addView(TextView(this@DocumentViewActivity).apply {
                            text = "第 ${a.pageIndex + 1} 页 · ${a.type.label}  ($timeStr)"
                            textSize = 11f
                            setTextColor(Color.parseColor("#179CD8"))
                        })
                        info.addView(TextView(this@DocumentViewActivity).apply {
                            text = a.summaryText() + if (a.replies.isNotEmpty()) "  [${a.replies.size}条回复]" else ""
                            textSize = 13f
                            maxLines = 2
                            setTextColor(Color.parseColor("#212121"))
                        })
                        val del = ImageView(this@DocumentViewActivity).apply {
                            setImageResource(R.drawable.fx_annot_delete)
                            setPadding(dp(6), dp(6), dp(6), dp(6))
                            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
                            setOnClickListener {
                                pushUndoSnapshot()
                                docState.annotations.removeAll { it.id == a.id }
                                saveAndRefreshCanvas()
                                refreshSidePanelList()
                            }
                        }
                        row.addView(icon)
                        row.addView(info)
                        row.addView(del)
                        row.setOnClickListener {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            renderPage(a.pageIndex)
                            if (a.type in setOf(AnnotTool.NOTE, AnnotTool.TYPEWRITER, AnnotTool.TEXTBOX, AnnotTool.CALLOUT)) {
                                showNoteOrTextDialog(a, isNew = false)
                            }
                        }
                        return row
                    }
                }
            }
            1 -> {
                val entries = if (outlineItems.isNotEmpty()) {
                    outlineItems
                } else {
                    (0 until pageCount).map { OutlineEntry("第 ${it + 1} 页", it, 0) }
                }
                tvSidePanelEmpty.text = "当前文档无目录项"
                tvSidePanelEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                lvSidePanel.adapter = object : BaseAdapter() {
                    override fun getCount() = entries.size
                    override fun getItem(p: Int) = entries[p]
                    override fun getItemId(p: Int) = p.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val e = entries[position]
                        val row = LinearLayout(this@DocumentViewActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(14 + e.depth * 14), dp(12), dp(14), dp(12))
                        }
                        row.addView(TextView(this@DocumentViewActivity).apply {
                            text = e.title
                            textSize = 14f
                            setTextColor(if (e.pageIndex == currentPage) Color.parseColor("#179CD8") else Color.parseColor("#212121"))
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        row.addView(TextView(this@DocumentViewActivity).apply {
                            text = "P.${e.pageIndex + 1}"
                            textSize = 12f
                            setTextColor(Color.parseColor("#888888"))
                        })
                        row.setOnClickListener {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            renderPage(e.pageIndex)
                        }
                        return row
                    }
                }
            }
            else -> {
                val bms = docState.bookmarks.sortedBy { it.pageIndex }
                tvSidePanelEmpty.text = "暂无书签，点击顶栏书签图标添加"
                tvSidePanelEmpty.visibility = if (bms.isEmpty()) View.VISIBLE else View.GONE
                lvSidePanel.adapter = object : BaseAdapter() {
                    override fun getCount() = bms.size
                    override fun getItem(p: Int) = bms[p]
                    override fun getItemId(p: Int) = p.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val b = bms[position]
                        val row = LinearLayout(this@DocumentViewActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(14), dp(12), dp(10), dp(12))
                        }
                        row.addView(ImageView(this@DocumentViewActivity).apply {
                            setImageResource(R.drawable.fx_bookmark_selected)
                            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                        })
                        row.addView(TextView(this@DocumentViewActivity).apply {
                            text = "${b.title}  (第 ${b.pageIndex + 1} 页)"
                            textSize = 14f
                            setTextColor(Color.parseColor("#212121"))
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                marginStart = dp(10)
                            }
                        })
                        row.addView(ImageView(this@DocumentViewActivity).apply {
                            setImageResource(R.drawable.fx_annot_delete)
                            setPadding(dp(6), dp(6), dp(6), dp(6))
                            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
                            setOnClickListener {
                                docState.bookmarks.removeAll { it.pageIndex == b.pageIndex }
                                PdfAnnotationStore.save(this@DocumentViewActivity, docState)
                                updateBookmarkIcon()
                                refreshSidePanelList()
                            }
                        })
                        row.setOnClickListener {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            renderPage(b.pageIndex)
                        }
                        return row
                    }
                }
            }
        }
    }

    private fun setupCanvasCallbacks() {
        pdfCanvas.onToggleChrome = {
            if (moreToolsSheet.visibility == View.VISIBLE ||
                propertySheet.visibility == View.VISIBLE ||
                viewSettingsSheet.visibility == View.VISIBLE
            ) {
                hideAllPopupSheets()
            } else if (!annotMode) {
                chromeVisible = !chromeVisible
                val vis = if (chromeVisible) View.VISIBLE else View.GONE
                topBarContainer.visibility = vis
                bottomBar.visibility = vis
                btnFloatPrev.visibility = if (chromeVisible && currentPage > 0) View.VISIBLE else View.GONE
                btnFloatNext.visibility = if (chromeVisible && currentPage < pageCount - 1) View.VISIBLE else View.GONE
            }
        }

        pdfCanvas.onSwipePage = { delta -> navigatePage(delta) }

        pdfCanvas.onAnnotationCreated = { annot ->
            pushUndoSnapshot()
            docState.annotations.add(annot)
            saveAndRefreshCanvas()
            if (!continuousMode) {
                selectAnnotTool(AnnotTool.BROWSE)
            }
        }

        pdfCanvas.onAnnotationErased = { target ->
            pushUndoSnapshot()
            docState.annotations.removeAll { it.id == target.id }
            saveAndRefreshCanvas()
        }

        pdfCanvas.onAnnotationTapped = { tapped ->
            showAnnotationActionDialog(tapped)
        }

        pdfCanvas.onPlaceTextAnnotation = { tool, anchorPt, boxPt ->
            when (tool) {
                AnnotTool.STAMP -> {
                    val (label, col) = FoxitPalette.STAMPS[selectedStampIdx % FoxitPalette.STAMPS.size]
                    val a = PdfAnnotation(
                        pageIndex = currentPage,
                        type = AnnotTool.STAMP,
                        color = col,
                        alpha = 95,
                        strokeWidth = 4f,
                        points = mutableListOf(anchorPt),
                        text = label
                    )
                    pushUndoSnapshot()
                    docState.annotations.add(a)
                    saveAndRefreshCanvas()
                    if (!continuousMode) selectAnnotTool(AnnotTool.BROWSE)
                }
                AnnotTool.NOTE, AnnotTool.TYPEWRITER, AnnotTool.TEXTBOX, AnnotTool.CALLOUT -> {
                    val pts = if (tool == AnnotTool.CALLOUT) mutableListOf(anchorPt, boxPt) else mutableListOf(anchorPt)
                    val draft = PdfAnnotation(
                        pageIndex = currentPage,
                        type = tool,
                        color = toolColors[tool] ?: FoxitPalette.defaultColorFor(tool),
                        alpha = toolAlphas[tool] ?: 100,
                        strokeWidth = toolWidths[tool] ?: 4f,
                        points = pts,
                        text = ""
                    )
                    showNoteOrTextDialog(draft, isNew = true)
                }
                else -> Unit
            }
        }
    }

    private fun loadPdfDocument(password: String? = null) {
        Thread {
            val file = sourceFile ?: Repository.resolveAssetFileSync(this, asset)
            if (file == null || !file.exists()) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "无法读取 PDF 文件", Toast.LENGTH_SHORT).show()
                    }
                }
                return@Thread
            }
            sourceFile = file

            // Fast path: try Android native PdfRenderer directly first (instant <200ms render!)
            if (password.isNullOrEmpty()) {
                try {
                    val directPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val directRenderer = PdfRenderer(directPfd)
                    pfd?.close()
                    renderer?.close()
                    pfd = directPfd
                    renderer = directRenderer
                    renderFile = file
                    docPassword = null
                    pageCount = directRenderer.pageCount
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        renderPage(currentPage)
                        applyDemoUiIfRequested()
                    }
                    // Extract native PDF outline asynchronously without blocking first paint
                    outlineItems = PdfBoxTextExtractor.extractOutline(this, file, null)
                    return@Thread
                } catch (_: SecurityException) {
                    // Encrypted PDF -> fall through to PDFBox password check
                } catch (_: Exception) {
                    // Fall through to PDFBox fallback
                }
            }

            when (val check = PdfBoxTextExtractor.prepareRenderableFile(this, file, password)) {
                is PdfOpenCheck.PasswordRequired -> {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) showPasswordPromptDialog()
                    }
                }
                is PdfOpenCheck.Error -> {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this, "PDF 打开失败：${check.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                is PdfOpenCheck.Ready -> {
                    renderFile = check.renderFile
                    docPassword = check.password
                    try {
                        pfd?.close()
                        renderer?.close()
                        pfd = ParcelFileDescriptor.open(check.renderFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        renderer = PdfRenderer(pfd!!)
                        pageCount = renderer?.pageCount ?: 0
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            renderPage(currentPage)
                            applyDemoUiIfRequested()
                        }
                        outlineItems = PdfBoxTextExtractor.extractOutline(this, check.renderFile, null)
                    } catch (e: Exception) {
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                Toast.makeText(this, "PDF 渲染初始化失败：${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }.start()
    }

    private fun renderPage(index: Int) {
        val r = renderer ?: return
        if (r.pageCount <= 0) return
        currentPage = index.coerceIn(0, r.pageCount - 1)
        Repository.savePdfPage(this, asset.assetId, currentPage)

        r.openPage(currentPage).use { page ->
            val targetW = 1440
            val targetH = (targetW.toFloat() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(600)
            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val pageAnnots = docState.annotations.filter { it.pageIndex == currentPage }
            pdfCanvas.resetZoom()
            pdfCanvas.setPageContent(currentPage, bmp, pageAnnots)
            pdfCanvas.pageTextIndex = pageTextCache[currentPage]
            pdfCanvas.searchHighlightRects = searchHits.filter { it.pageIndex == currentPage }.flatMap { it.rects }

            tvPageBadge.text = "${currentPage + 1} / ${r.pageCount}"
            progressBar.progress = (((currentPage + 1) * 10000f) / r.pageCount).toInt()
            btnFloatPrev.visibility = if (chromeVisible && currentPage > 0) View.VISIBLE else View.GONE
            btnFloatNext.visibility = if (chromeVisible && currentPage < r.pageCount - 1) View.VISIBLE else View.GONE
            updateBookmarkIcon()
            updateSubtitle()
        }

        // Asynchronously extract PDF text bounding boxes for accurate text-snapping markup
        val rf = renderFile
        val pageToExtract = currentPage
        if (rf != null && !pageTextCache.containsKey(pageToExtract)) {
            Thread {
                val idx = PdfBoxTextExtractor.extractPageText(this, rf, pageToExtract, null)
                pageTextCache[pageToExtract] = idx
                runOnUiThread {
                    if (currentPage == pageToExtract) {
                        pdfCanvas.pageTextIndex = idx
                    }
                }
            }.start()
        }
    }

    private fun navigatePage(delta: Int) {
        val target = currentPage + delta
        if (target in 0 until pageCount) {
            renderPage(target)
        } else {
            Toast.makeText(this, if (delta < 0) "已经是第一页" else "已经是最后一页", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enterAnnotationMode(initialTool: AnnotTool) {
        annotMode = true
        hideAllPopupSheets()
        mainBottomBar.visibility = View.GONE
        annotBottomBar.visibility = View.VISIBLE
        selectAnnotTool(initialTool)
    }

    private fun exitAnnotationMode() {
        annotMode = false
        hideAllPopupSheets()
        pdfCanvas.activeTool = AnnotTool.BROWSE
        annotBottomBar.visibility = View.GONE
        toolPromptBar.visibility = View.GONE
        mainBottomBar.visibility = View.VISIBLE
    }

    private fun selectAnnotTool(tool: AnnotTool) {
        pdfCanvas.activeTool = tool
        if (tool != AnnotTool.BROWSE && tool != AnnotTool.ERASER) {
            pdfCanvas.activeColor = toolColors[tool] ?: FoxitPalette.defaultColorFor(tool)
            pdfCanvas.activeAlpha = toolAlphas[tool] ?: FoxitPalette.defaultAlphaFor(tool)
            pdfCanvas.activeStrokeWidth = toolWidths[tool] ?: 4f
            refreshPropertySheetUI()
        }

        quickToolViews.forEach { (t, v) ->
            if (t == tool) v.setBackgroundResource(R.drawable.bg_fx_tool_selected)
            else v.background = null
        }

        if (tool == AnnotTool.BROWSE) {
            toolPromptBar.visibility = View.GONE
        } else {
            toolPromptBar.visibility = View.VISIBLE
            tvActiveToolHint.text = when (tool) {
                AnnotTool.HIGHLIGHT -> "高亮强调：拖动选中文字自动贴合"
                AnnotTool.UNDERLINE -> "下划线：拖动选中文字"
                AnnotTool.SQUIGGLY -> "波浪线：拖动选中文字"
                AnnotTool.STRIKEOUT -> "删除线：拖动选中文字"
                AnnotTool.PENCIL -> "铅笔手绘：在页面自由书写"
                AnnotTool.LINE -> "直线：拖动绘制直线"
                AnnotTool.ARROW -> "箭头：拖动绘制指向箭头"
                AnnotTool.RECT -> "矩形：拖动框选重点区域"
                AnnotTool.OVAL -> "椭圆：拖动圈注重点"
                AnnotTool.ERASER -> "橡皮擦：点击或划过批注即可擦除"
                AnnotTool.NOTE -> "便签批注：轻触页面放置淡黄便签"
                AnnotTool.TYPEWRITER -> "打字写字：轻触页面输入文字"
                AnnotTool.TEXTBOX -> "文本框：轻触页面添加带框文字"
                AnnotTool.CALLOUT -> "标注框：从指向位置拖动放置说明框"
                AnnotTool.STAMP -> "盖章(${FoxitPalette.STAMPS[selectedStampIdx].first})：轻触页面盖章"
                else -> tool.label
            }
        }
    }

    private fun currentConfigurableTool(): AnnotTool {
        return if (pdfCanvas.activeTool == AnnotTool.BROWSE || pdfCanvas.activeTool == AnnotTool.ERASER) {
            AnnotTool.HIGHLIGHT
        } else pdfCanvas.activeTool
    }

    private fun hideAllPopupSheets() {
        moreToolsSheet.visibility = View.GONE
        propertySheet.visibility = View.GONE
        viewSettingsSheet.visibility = View.GONE
    }

    private fun pushUndoSnapshot() {
        undoStack.addLast(docState.annotations.map { it.deepCopy() })
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        updateUndoRedoUI()
    }

    private fun performUndo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "没有可撤销的操作", Toast.LENGTH_SHORT).show()
            return
        }
        redoStack.addLast(docState.annotations.map { it.deepCopy() })
        val prev = undoStack.removeLast()
        docState.annotations.clear()
        docState.annotations.addAll(prev)
        saveAndRefreshCanvas()
        updateUndoRedoUI()
    }

    private fun performRedo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "没有可重做的操作", Toast.LENGTH_SHORT).show()
            return
        }
        undoStack.addLast(docState.annotations.map { it.deepCopy() })
        val next = redoStack.removeLast()
        docState.annotations.clear()
        docState.annotations.addAll(next)
        saveAndRefreshCanvas()
        updateUndoRedoUI()
    }

    private fun updateUndoRedoUI() {
        quickUndoView?.alpha = if (undoStack.isEmpty()) 0.35f else 1f
        quickRedoView?.alpha = if (redoStack.isEmpty()) 0.35f else 1f
    }

    private fun saveAndRefreshCanvas() {
        PdfAnnotationStore.save(this, docState)
        pdfCanvas.updateAnnotations(docState.annotations.filter { it.pageIndex == currentPage })
        updateSubtitle()
    }

    private fun updateSubtitle() {
        val total = docState.annotations.size
        tvPdfSubtitle.text = "${total}注"
    }

    private fun toggleBookmarkCurrentPage() {
        val existing = docState.bookmarks.indexOfFirst { it.pageIndex == currentPage }
        if (existing >= 0) {
            docState.bookmarks.removeAt(existing)
            Toast.makeText(this, "已取消第 ${currentPage + 1} 页书签", Toast.LENGTH_SHORT).show()
        } else {
            docState.bookmarks.add(PdfBookmark(currentPage, "${asset.title} · 第 ${currentPage + 1} 页"))
            Toast.makeText(this, "已添加第 ${currentPage + 1} 页书签", Toast.LENGTH_SHORT).show()
        }
        PdfAnnotationStore.save(this, docState)
        updateBookmarkIcon()
    }

    private fun updateBookmarkIcon() {
        val marked = docState.bookmarks.any { it.pageIndex == currentPage }
        btnBookmarkToggle.setImageResource(if (marked) R.drawable.fx_bookmark_selected else R.drawable.fx_bookmark)
        btnBookmarkToggle.setColorFilter(if (marked) Color.parseColor("#FFEB3B") else Color.WHITE)
    }

    private fun showNoteOrTextDialog(annot: PdfAnnotation, isNew: Boolean) {
        val view = layoutInflater.inflate(R.layout.dialog_foxit_note, null)
        val tvTitle = view.findViewById<TextView>(R.id.tvNoteDialogTitle)
        val etContent = view.findViewById<EditText>(R.id.etNoteContent)
        val llRepliesSection = view.findViewById<View>(R.id.llRepliesSection)
        val llRepliesList = view.findViewById<LinearLayout>(R.id.llRepliesList)
        val etReplyInput = view.findViewById<EditText>(R.id.etReplyInput)
        val btnAddReply = view.findViewById<Button>(R.id.btnAddReply)

        tvTitle.text = when (annot.type) {
            AnnotTool.NOTE -> "便签批注（第 ${annot.pageIndex + 1} 页）"
            AnnotTool.TYPEWRITER -> "打字写字"
            AnnotTool.TEXTBOX -> "文本框批注"
            AnnotTool.CALLOUT -> "标注框说明"
            else -> "${annot.type.label} · 批注笔记"
        }
        etContent.setText(annot.text)

        fun renderReplies() {
            llRepliesList.removeAllViews()
            for (rp in annot.replies) {
                llRepliesList.addView(TextView(this).apply {
                    text = "${rp.author}：${rp.text}"
                    textSize = 13f
                    setTextColor(Color.parseColor("#333333"))
                    setPadding(dp(4), dp(3), dp(4), dp(3))
                })
            }
        }

        if (!isNew) {
            llRepliesSection.visibility = View.VISIBLE
            renderReplies()
            btnAddReply.setOnClickListener {
                val txt = etReplyInput.text.toString().trim()
                if (txt.isNotEmpty()) {
                    annot.replies.add(ReplyItem(com.shaonianpai.tablet.data.UserProfileStore.getUserName(this), txt))
                    annot.updatedAt = System.currentTimeMillis()
                    etReplyInput.setText("")
                    renderReplies()
                    saveAndRefreshCanvas()
                }
            }
        }

        val builder = AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                val txt = etContent.text.toString().trim()
                if (isNew) {
                    if (txt.isNotEmpty()) {
                        annot.text = txt
                        pushUndoSnapshot()
                        docState.annotations.add(annot)
                        saveAndRefreshCanvas()
                        if (!continuousMode) selectAnnotTool(AnnotTool.BROWSE)
                    }
                } else {
                    pushUndoSnapshot()
                    annot.text = txt
                    annot.updatedAt = System.currentTimeMillis()
                    saveAndRefreshCanvas()
                }
            }
            .setNegativeButton("取消", null)

        if (!isNew) {
            builder.setNeutralButton("删除") { _, _ ->
                pushUndoSnapshot()
                docState.annotations.removeAll { it.id == annot.id }
                saveAndRefreshCanvas()
            }
        }
        builder.show()
    }

    private fun showAnnotationActionDialog(annot: PdfAnnotation) {
        val actions = arrayOf("查看 / 编辑笔记与回复", "切换批注颜色", "删除此批注")
        AlertDialog.Builder(this)
            .setTitle("${annot.type.label} · ${annot.summaryText().take(24)}")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showNoteOrTextDialog(annot, isNew = false)
                    1 -> {
                        val names = arrayOf("荧光黄", "批改红", "福昕青蓝", "荧光绿", "醒目橙", "重点紫", "墨黑")
                        val cols = intArrayOf(
                            FoxitPalette.COLORS[0], FoxitPalette.COLORS[1], FoxitPalette.COLORS[2],
                            FoxitPalette.COLORS[3], FoxitPalette.COLORS[4], FoxitPalette.COLORS[5], FoxitPalette.COLORS[10]
                        )
                        AlertDialog.Builder(this)
                            .setTitle("选择新颜色")
                            .setItems(names) { _, cIdx ->
                                pushUndoSnapshot()
                                annot.color = cols[cIdx]
                                saveAndRefreshCanvas()
                            }
                            .show()
                    }
                    2 -> {
                        pushUndoSnapshot()
                        docState.annotations.removeAll { it.id == annot.id }
                        saveAndRefreshCanvas()
                    }
                }
            }
            .show()
    }

    private fun showStampPickerDialog() {
        val labels = FoxitPalette.STAMPS.map { "【${it.first}】教学印章" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择盖章样式")
            .setSingleChoiceItems(labels, selectedStampIdx) { dlg, which ->
                selectedStampIdx = which
                dlg.dismiss()
                selectAnnotTool(AnnotTool.STAMP)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showThumbnailsDialog() {
        val r = renderer ?: return
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = if (resources.displayMetrics.widthPixels < dp(560)) 2 else 3
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        scroll.addView(grid)

        val dlg = AlertDialog.Builder(this)
            .setTitle("页面缩略图（共 ${r.pageCount} 页）")
            .setView(scroll)
            .setNegativeButton("关闭", null)
            .create()

        for (i in 0 until r.pageCount) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(6), dp(6), dp(6))
                if (i == currentPage) setBackgroundResource(R.drawable.bg_fx_tool_selected)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                setOnClickListener {
                    dlg.dismiss()
                    renderPage(i)
                }
            }
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(120), dp(156))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.WHITE)
            }
            r.openPage(i).use { p ->
                val tw = 220
                val th = (tw.toFloat() * p.height / p.width.coerceAtLeast(1)).toInt().coerceAtLeast(120)
                val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                iv.setImageBitmap(bmp)
            }
            val cnt = docState.annotations.count { it.pageIndex == i }
            val lbl = TextView(this).apply {
                text = "第 ${i + 1} 页" + if (cnt > 0) " · ${cnt}注" else ""
                textSize = 12f
                setTextColor(if (i == currentPage) Color.parseColor("#179CD8") else Color.parseColor("#424242"))
                setPadding(0, dp(4), 0, 0)
            }
            card.addView(iv)
            card.addView(lbl)
            grid.addView(card)
        }
        dlg.show()
    }

    private fun showPageJumpDialog() {
        if (pageCount <= 0) return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "输入页码 (1 - $pageCount)"
            setText("${currentPage + 1}")
        }
        AlertDialog.Builder(this)
            .setTitle("跳转到指定页")
            .setView(input)
            .setPositiveButton("跳转") { _, _ ->
                val target = (input.text.toString().toIntOrNull() ?: (currentPage + 1)) - 1
                renderPage(target.coerceIn(0, pageCount - 1))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPasswordPromptDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "请输入 PDF 打开密码"
        }
        AlertDialog.Builder(this)
            .setTitle("加密 PDF 文档")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("确认") { _, _ ->
                loadPdfDocument(input.text.toString())
            }
            .setNegativeButton("取消") { _, _ -> finish() }
            .show()
    }

    private fun runFullTextSearch() {
        val query = etSearchQuery.text.toString().trim()
        val rf = renderFile ?: return
        if (query.isEmpty()) {
            searchHits = emptyList()
            currentSearchIdx = -1
            tvSearchCounter.text = "0/0"
            pdfCanvas.searchHighlightRects = emptyList()
            return
        }
        tvSearchCounter.text = "查找中..."
        Thread {
            val hits = PdfBoxTextExtractor.searchAllPages(this, rf, query, null)
            runOnUiThread {
                searchHits = hits
                if (hits.isEmpty()) {
                    currentSearchIdx = -1
                    tvSearchCounter.text = "0/0"
                    pdfCanvas.searchHighlightRects = emptyList()
                    Toast.makeText(this, "未找到「$query」", Toast.LENGTH_SHORT).show()
                } else {
                    currentSearchIdx = 0
                    jumpToSearchHit(0)
                }
            }
        }.start()
    }

    private fun stepSearch(delta: Int) {
        if (searchHits.isEmpty()) return
        currentSearchIdx = (currentSearchIdx + delta + searchHits.size) % searchHits.size
        jumpToSearchHit(currentSearchIdx)
    }

    private fun jumpToSearchHit(idx: Int) {
        val hit = searchHits.getOrNull(idx) ?: return
        tvSearchCounter.text = "${idx + 1}/${searchHits.size}"
        if (hit.pageIndex != currentPage) {
            renderPage(hit.pageIndex)
        } else {
            pdfCanvas.searchHighlightRects = searchHits.filter { it.pageIndex == currentPage }.flatMap { it.rects }
        }
    }

    private fun closeSearch() {
        searchBar.visibility = View.GONE
        searchHits = emptyList()
        currentSearchIdx = -1
        tvSearchCounter.text = "0/0"
        pdfCanvas.searchHighlightRects = emptyList()
    }

    private fun exportAnnotatedPdf() {
        val src = sourceFile ?: return
        Toast.makeText(this, "正在导出带标准批注的 PDF...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val outDir = File(cacheDir, "exports").apply { mkdirs() }
                val baseName = asset.title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5._-]"), "_")
                val outFile = File(outDir, "${baseName}_已批注.pdf")
                PdfAnnotationStore.exportAnnotatedPdf(this, src, docPassword, docState, outFile)
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", outFile)
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("批注 PDF 导出成功")
                        .setMessage("已写入标准 PDF 批注与手绘笔迹：\n${outFile.name}（${outFile.length() / 1024} KB）")
                        .setPositiveButton("分享/发送") { _, _ ->
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(share, "分享带批注的 PDF"))
                        }
                        .setNeutralButton("外部打开") { _, _ ->
                            val view = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { startActivity(view) }.onFailure {
                                Toast.makeText(this, "未找到系统 PDF 阅读器", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("完成", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun openExternalPdf() {
        val file = renderFile ?: sourceFile ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "用系统 APP 打开 PDF"))
        } catch (_: Exception) {
            Toast.makeText(this, "已缓存至本地，未检测到其他 PDF 应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun makeToolButton(label: String, iconRes: Int, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(4), dp(9), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(ImageView(this@DocumentViewActivity).apply {
                setImageResource(iconRes)
                if (iconRes == R.drawable.fx_annot_eraser) {
                    setColorFilter(Color.parseColor("#424242"))
                }
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            })
            addView(TextView(this@DocumentViewActivity).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextColor(Color.parseColor("#424242"))
                setPadding(0, dp(2), 0, 0)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun makeActionChip(label: String, @Suppress("UNUSED_PARAMETER") iconRes: Int?, bgColor: Int, filled: Boolean, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setTextColor(if (filled) Color.WHITE else bgColor)
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                if (filled) setColor(bgColor) else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), bgColor)
                }
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4)
                marginEnd = dp(6)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun makeSheetToolItem(label: String, iconRes: Int, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(ImageView(this@DocumentViewActivity).apply {
                setImageResource(iconRes)
                if (iconRes == R.drawable.fx_annot_eraser) {
                    setColorFilter(Color.parseColor("#424242"))
                }
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            })
            addView(TextView(this@DocumentViewActivity).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextColor(Color.parseColor("#333333"))
                setPadding(0, dp(4), 0, 0)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun iconForAnnotationType(type: AnnotTool): Int = when (type) {
        AnnotTool.HIGHLIGHT -> R.drawable.fx_type_highlight
        AnnotTool.UNDERLINE -> R.drawable.fx_type_underline
        AnnotTool.SQUIGGLY -> R.drawable.fx_type_squiggly
        AnnotTool.STRIKEOUT -> R.drawable.fx_type_strikeout
        AnnotTool.PENCIL -> R.drawable.fx_type_ink
        AnnotTool.LINE -> R.drawable.fx_type_line
        AnnotTool.ARROW -> R.drawable.fx_type_arrow
        AnnotTool.RECT -> R.drawable.fx_type_square
        AnnotTool.OVAL -> R.drawable.fx_type_circle
        AnnotTool.NOTE -> R.drawable.fx_type_note
        AnnotTool.TYPEWRITER, AnnotTool.TEXTBOX -> R.drawable.fx_type_typewriter
        AnnotTool.CALLOUT -> R.drawable.fx_type_callout
        AnnotTool.STAMP -> R.drawable.fx_type_stamp
        else -> R.drawable.fx_type_note
    }

    private fun seedDemoAnnotationsIfEmpty() {
        if (docState.annotations.isNotEmpty()) return
        docState.annotations.addAll(
            listOf(
                PdfAnnotation(
                    pageIndex = 0,
                    type = AnnotTool.HIGHLIGHT,
                    color = FoxitPalette.COLORS[0],
                    alpha = 55,
                    rects = mutableListOf(RectNorm(0.11f, 0.255f, 0.72f, 0.285f)),
                    text = "先天下之忧而忧，后天下之乐而乐"
                ),
                PdfAnnotation(
                    pageIndex = 0,
                    type = AnnotTool.UNDERLINE,
                    color = FoxitPalette.COLORS[3],
                    alpha = 100,
                    strokeWidth = 4f,
                    rects = mutableListOf(RectNorm(0.11f, 0.34f, 0.64f, 0.365f)),
                    text = "不以物喜，不以己悲"
                ),
                PdfAnnotation(
                    pageIndex = 0,
                    type = AnnotTool.SQUIGGLY,
                    color = FoxitPalette.COLORS[2],
                    alpha = 100,
                    strokeWidth = 3f,
                    rects = mutableListOf(RectNorm(0.11f, 0.42f, 0.52f, 0.445f)),
                    text = "滕子京谪守巴陵郡"
                ),
                PdfAnnotation(
                    pageIndex = 0,
                    type = AnnotTool.PENCIL,
                    color = FoxitPalette.COLORS[1],
                    alpha = 100,
                    strokeWidth = 4f,
                    points = mutableListOf(
                        PointNorm(0.68f, 0.52f),
                        PointNorm(0.75f, 0.50f),
                        PointNorm(0.82f, 0.525f),
                        PointNorm(0.84f, 0.57f),
                        PointNorm(0.77f, 0.60f),
                        PointNorm(0.68f, 0.58f),
                        PointNorm(0.67f, 0.53f)
                    )
                ),
                PdfAnnotation(
                    pageIndex = 0,
                    type = AnnotTool.NOTE,
                    color = Color.parseColor("#FFB300"),
                    alpha = 100,
                    points = mutableListOf(PointNorm(0.76f, 0.27f)),
                    text = "高频考点：理解范仲淹的政治抱负与古仁人之心，默写易错字注意「谪」「汀」。",
                    replies = mutableListOf(ReplyItem("语文老师", "期中必考默写句，请熟读成诵！"))
                ),
                PdfAnnotation(
                    pageIndex = 0,
                    type = AnnotTool.CALLOUT,
                    color = Color.parseColor("#1565C0"),
                    alpha = 100,
                    strokeWidth = 3f,
                    points = mutableListOf(PointNorm(0.45f, 0.65f), PointNorm(0.56f, 0.68f)),
                    text = "借景抒情 · 情景交融"
                ),
                PdfAnnotation(
                    pageIndex = 0,
                    type = AnnotTool.STAMP,
                    color = Color.parseColor("#D32F2F"),
                    alpha = 95,
                    points = mutableListOf(PointNorm(0.83f, 0.16f)),
                    text = "必背"
                )
            )
        )
        docState.bookmarks.add(PdfBookmark(0, "岳阳楼记 · 必背核心段"))
        PdfAnnotationStore.save(this, docState)
    }

    private fun applyDemoUiIfRequested() {
        when (intent.getStringExtra("demo_ui")) {
            "annot_bar" -> enterAnnotationMode(AnnotTool.HIGHLIGHT)
            "more_tools" -> {
                enterAnnotationMode(AnnotTool.PENCIL)
                moreToolsSheet.visibility = View.VISIBLE
            }
            "property" -> {
                enterAnnotationMode(AnnotTool.PENCIL)
                refreshPropertySheetUI()
                propertySheet.visibility = View.VISIBLE
            }
            "side_panel" -> {
                selectSideTab(0)
                drawerLayout.openDrawer(GravityCompat.START)
            }
            "view_settings" -> {
                viewSettingsSheet.visibility = View.VISIBLE
            }
            "note_dialog" -> {
                val n = docState.annotations.firstOrNull { it.type == AnnotTool.NOTE }
                if (n != null) showNoteOrTextDialog(n, isNew = false)
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
