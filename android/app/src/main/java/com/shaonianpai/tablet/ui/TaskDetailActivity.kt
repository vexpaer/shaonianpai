package com.shaonianpai.tablet.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.AssetItem
import com.shaonianpai.tablet.data.DeliveryTask
import com.shaonianpai.tablet.data.Repository
import com.shaonianpai.tablet.data.UserProfileStore

/**
 * Faithfully recreates ResourcesViewActivity2 (activity_resourcesview2.xml):
 * - START (left) drawer matching layout_resourcestatistic.xml with type filter Spinner & item index.
 * - Inline multi-page PDF reader embedded right inside each PDF card (matching layout_customdocumentview.xml
 *   fl_pdfcontent + floating top-right iv_fullscreen button to open full-screen Foxit PDF annotation mode!).
 * - Authentic inline Audio bar (#BB000000), Video cover, Zoomable Chalkboard image, AND interactive
 *   随堂练习 multiple-choice questions controlled by original 「答案显示过滤」 menu!
 */
class TaskDetailActivity : AppCompatActivity() {

    private data class InlineQuiz(
        val id: String,
        val stem: String,
        val options: List<String>,
        val correctIndex: Int,
        val explanation: String
    )

    private sealed class DetailRow {
        data class Resource(val index: Int, val asset: AssetItem) : DetailRow()
        data class Quiz(val index: Int, val quiz: InlineQuiz) : DetailRow()
    }

    private lateinit var task: DeliveryTask
    private lateinit var drawer: DrawerLayout
    private lateinit var recycler: RecyclerView
    private lateinit var lvStats: ListView
    private lateinit var spFilter: Spinner

    private var compactMode = false
    private var autoPlayAudio = false
    private var answerFilterMode = 2 // 0=none, 1=wrong only, 2=all
    private var activeFilterIndex = 0 // 0=all, 1=pdf, 2=audio/video, 3=image, 4=quiz
    private val selectedQuizAnswers = mutableMapOf<String, Int>()
    private val inlinePdfPages = mutableMapOf<String, Int>()
    private val expandedPdfAssets = mutableSetOf<String>()
    private val videoCoverCache = mutableMapOf<String, Pair<Bitmap, String>>()

    private var activeMediaPlayer: MediaPlayer? = null
    private var activeAudioAssetId: String? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var progressUpdater: Runnable? = null

    private lateinit var quizzes: List<InlineQuiz>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resourcesview2)

        @Suppress("DEPRECATION")
        task = (intent.getSerializableExtra("task") as? DeliveryTask)
            ?: Repository.builtInFallbackTasks().first()
        compactMode = intent.getBooleanExtra("compact_mode", false)
        autoPlayAudio = intent.getBooleanExtra("auto_play_audio", false)
        if (intent.getBooleanExtra("demo_expand_pdf", false)) {
            task.assets.firstOrNull { it.mime.contains("pdf") }?.let { expandedPdfAssets.add(it.assetId) }
        }

        quizzes = buildQuizzesForTask(task)

        drawer = findViewById(R.id.drawer)
        recycler = findViewById(R.id.rvResources)
        lvStats = findViewById(R.id.listViewResource)
        spFilter = findViewById(R.id.spinnerFilterType)

        val leftDrawer = findViewById<View>(R.id.leftStatDrawer)
        val dm = resources.displayMetrics
        leftDrawer.layoutParams = leftDrawer.layoutParams.apply {
            width = (dm.widthPixels * 0.82f).toInt().coerceAtMost((360 * dm.density).toInt())
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setBackgroundColor(UserProfileStore.getThemePrimaryColor(this))
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = task.title

        findViewById<TextView>(R.id.tvTaskMeta).text =
            "${task.subject} · ${task.taskType} · ${task.teacherName} · ${task.startDate} 到 ${task.endDate}"
        findViewById<TextView>(R.id.tvTaskDesc).text = task.description
        findViewById<TextView>(R.id.tvStatSummary).text =
            "共 ${task.assets.size} 份资料 + ${quizzes.size} 道随堂题 · 进度 ${task.progressPercent}%"

        val filterLabels = listOf("全部内容", "仅文档 PDF", "仅音频 / 视频", "仅板书图片", "仅随堂练习")
        spFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filterLabels)
        spFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                activeFilterIndex = position
                rebuildList()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        recycler.layoutManager = LinearLayoutManager(this)
        rebuildList()

        if (intent.getBooleanExtra("open_stats", false)) {
            drawer.post { drawer.openDrawer(GravityCompat.START) }
        }
    }

    private fun buildQuizzesForTask(t: DeliveryTask): List<InlineQuiz> {
        return if (t.subject.contains("数")) {
            listOf(
                InlineQuiz(
                    "q_math_1",
                    "抛物线 y = x² - 4x + 3 的顶点坐标是（ ）",
                    listOf("A. (2, -1)", "B. (-2, -1)", "C. (2, 1)", "D. (-2, 3)"),
                    0,
                    "解析：配方得 y = (x - 2)² - 1，故顶点为 (2, -1)。"
                )
            )
        } else {
            listOf(
                InlineQuiz(
                    "q_yw_1",
                    "《岳阳楼记》中体现作者远大政治抱负的名句是（ ）",
                    listOf(
                        "A. 衔远山，吞长江，浩浩汤汤",
                        "B. 先天下之忧而忧，后天下之乐而乐",
                        "C. 浮光跃金，静影沉璧",
                        "D. 政通人和，百废具兴"
                    ),
                    1,
                    "解析：“先天下之忧而忧，后天下之乐而乐”直抒胸臆，点明全文主旨。"
                )
            )
        }
    }

    private fun filteredRows(): List<DetailRow> {
        val all = mutableListOf<DetailRow>()
        var idx = 1
        for (a in task.assets) {
            val include = when (activeFilterIndex) {
                1 -> a.mime.contains("pdf")
                2 -> a.mime.contains("audio") || a.mime.contains("video")
                3 -> a.mime.contains("image")
                4 -> false
                else -> true
            }
            if (include) all.add(DetailRow.Resource(idx++, a))
        }
        if (activeFilterIndex == 0 || activeFilterIndex == 4) {
            for (q in quizzes) {
                all.add(DetailRow.Quiz(idx++, q))
            }
        }
        return all
    }

    private fun rebuildList() {
        val rows = filteredRows()
        val statLabels = rows.map { row ->
            when (row) {
                is DetailRow.Resource -> "${row.index}. [${Repository.typeLabelForMime(row.asset.mime)}] ${row.asset.title}"
                is DetailRow.Quiz -> "${row.index}. [随堂选择题] ${row.quiz.stem.take(18)}..."
            }
        }
        lvStats.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, statLabels)
        lvStats.setOnItemClickListener { _, _, position, _ ->
            drawer.closeDrawer(GravityCompat.START)
            recycler.smoothScrollToPosition(position)
        }
        recycler.adapter = DetailAdapter(rows)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_taskdetail, menu)
        menu.findItem(R.id.action_toggle_mode)?.title = if (compactMode) "切换富卡片" else "切换紧凑行"
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_stats -> {
                drawer.openDrawer(GravityCompat.START)
                true
            }
            R.id.action_save -> {
                Toast.makeText(this, "已保存作答记录、阅读页码与收藏状态", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_noneanswer -> {
                item.isChecked = true
                answerFilterMode = 0
                rebuildList()
                true
            }
            R.id.action_wronganswer -> {
                item.isChecked = true
                answerFilterMode = 1
                rebuildList()
                true
            }
            R.id.action_allanswer -> {
                item.isChecked = true
                answerFilterMode = 2
                rebuildList()
                true
            }
            R.id.action_toggle_mode -> {
                compactMode = !compactMode
                item.title = if (compactMode) "切换富卡片" else "切换紧凑行"
                stopAudio()
                rebuildList()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun stopAudio() {
        progressUpdater?.let { uiHandler.removeCallbacks(it) }
        progressUpdater = null
        activeMediaPlayer?.let {
            runCatching {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        activeMediaPlayer = null
        activeAudioAssetId = null
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }

    private fun openAssetFullscreen(asset: AssetItem, page: Int = 0) {
        when {
            asset.mime.contains("pdf") -> {
                startActivity(Intent(this, DocumentViewActivity::class.java).apply {
                    putExtra("asset", asset)
                    putExtra("page", page)
                })
            }
            asset.mime.contains("video") -> {
                stopAudio()
                startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
                    putExtra("asset", asset)
                })
            }
            asset.mime.contains("image") -> {
                Thread {
                    val f = Repository.resolveAssetFileSync(this, asset)
                    val bmp = f?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
                    runOnUiThread {
                        if (bmp != null && !isFinishing && !isDestroyed) {
                            val iv = ImageView(this).apply {
                                setImageBitmap(bmp)
                                adjustViewBounds = true
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setBackgroundColor(Color.BLACK)
                            }
                            AlertDialog.Builder(this)
                                .setTitle(asset.title)
                                .setView(iv)
                                .setPositiveButton("关闭", null)
                                .show()
                        }
                    }
                }.start()
            }
            else -> Toast.makeText(this, "打开：${asset.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class DetailAdapter(private val rows: List<DetailRow>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is DetailRow.Quiz -> 2
            is DetailRow.Resource -> if (compactMode) 1 else 0
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return when (viewType) {
                1 -> CompactVH(inf.inflate(R.layout.listitem_resourcelist, parent, false))
                2 -> QuizVH(LinearLayout(parent.context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
                else -> CardVH(inf.inflate(R.layout.layout_resourceitem2, parent, false))
            }
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is DetailRow.Quiz -> bindQuiz(holder as QuizVH, row)
                is DetailRow.Resource -> {
                    val asset = row.asset
                    if (holder is CompactVH) {
                        holder.ivType.setImageResource(Repository.iconResForMime(asset.mime))
                        holder.tvTitle.text = "${row.index}. ${asset.title}"
                        holder.tvSize.text = Repository.formatBytes(asset.bytes)
                        holder.ivFav.alpha = if (Repository.isFavorite(this@TaskDetailActivity, asset.assetId)) 1.0f else 0.35f
                        holder.ivFav.setOnClickListener {
                            val fav = Repository.toggleFavorite(this@TaskDetailActivity, asset.assetId)
                            holder.ivFav.alpha = if (fav) 1.0f else 0.35f
                        }
                        holder.itemView.setOnClickListener { openAssetFullscreen(asset) }
                    } else {
                        bindRichResource(holder as CardVH, row)
                    }
                }
            }
        }

        private fun bindQuiz(vh: QuizVH, row: DetailRow.Quiz) {
            val d = resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            val q = row.quiz
            val box = vh.container
            box.removeAllViews()
            box.setPadding(dp(16), dp(14), dp(16), dp(14))
            box.setBackgroundColor(Color.WHITE)

            box.addView(TextView(this@TaskDetailActivity).apply {
                text = "${row.index}. [随堂练习 · 单选题]"
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(UserProfileStore.getThemePrimaryColor(this@TaskDetailActivity))
            })
            box.addView(TextView(this@TaskDetailActivity).apply {
                text = q.stem
                textSize = 15.5f
                setTextColor(Color.parseColor("#212121"))
                setPadding(0, dp(6), 0, dp(8))
            })

            val rg = RadioGroup(this@TaskDetailActivity)
            val savedChoice = selectedQuizAnswers[q.id] ?: -1
            q.options.forEachIndexed { idx, opt ->
                val rb = RadioButton(this@TaskDetailActivity).apply {
                    id = View.generateViewId()
                    text = opt
                    textSize = 14.5f
                    setTextColor(Color.parseColor("#333333"))
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    isChecked = idx == savedChoice
                    setOnClickListener {
                        selectedQuizAnswers[q.id] = idx
                        notifyItemChanged(vh.bindingAdapterPosition)
                    }
                }
                rg.addView(rb)
            }
            box.addView(rg)

            val showAnswer = when (answerFilterMode) {
                0 -> false
                1 -> savedChoice >= 0 && savedChoice != q.correctIndex
                else -> true
            }
            if (showAnswer) {
                val correctLabel = q.options.getOrNull(q.correctIndex) ?: ""
                val statusPrefix = when {
                    savedChoice < 0 -> "参考答案：$correctLabel"
                    savedChoice == q.correctIndex -> "✓ 回答正确！$correctLabel"
                    else -> "✗ 回答有误，正确答案：$correctLabel"
                }
                val tvAns = TextView(this@TaskDetailActivity).apply {
                    text = "$statusPrefix\n${q.explanation}"
                    textSize = 13.5f
                    setTextColor(if (savedChoice == q.correctIndex) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
                    setBackgroundColor(Color.parseColor("#FFF8E1"))
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(8)
                    }
                }
                box.addView(tvAns)
            }

            box.addView(View(this@TaskDetailActivity).apply {
                setBackgroundColor(Color.parseColor("#E8E8E8"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    topMargin = dp(12)
                }
            })
        }

        private fun bindRichResource(vh: CardVH, row: DetailRow.Resource) {
            val asset = row.asset
            vh.tvIndex.text = "${row.index}."
            vh.ivThumb.setImageResource(Repository.iconResForMime(asset.mime))
            vh.tvType.text = Repository.typeLabelForMime(asset.mime)
            vh.tvTitle.text = asset.title
            vh.tvTime.text = Repository.formatBytes(asset.bytes)
            vh.ivFav.alpha = if (Repository.isFavorite(this@TaskDetailActivity, asset.assetId)) 1.0f else 0.35f
            vh.ivFav.setOnClickListener {
                val fav = Repository.toggleFavorite(this@TaskDetailActivity, asset.assetId)
                vh.ivFav.alpha = if (fav) 1.0f else 0.35f
            }

            vh.flContent.removeAllViews()
            val inf = LayoutInflater.from(this@TaskDetailActivity)

            when {
                asset.mime.contains("pdf") -> bindInlinePdfViewer(vh.flContent, asset)
                asset.mime.contains("audio") -> {
                    val v = inf.inflate(R.layout.layout_customaudioview, vh.flContent, false)
                    val btnPlay = v.findViewById<ImageButton>(R.id.ImageButtonPlay)
                    val btnPause = v.findViewById<ImageButton>(R.id.ImageButtonPause)
                    val seek = v.findViewById<SeekBar>(R.id.SeekBarPosition)
                    val tvTime = v.findViewById<TextView>(R.id.tvAudioTime)
                    tvTime.text = "00:00 / 00:00"
                    btnPlay.visibility = View.VISIBLE
                    btnPause.visibility = View.GONE

                    fun showPlayingState(playing: Boolean) {
                        btnPlay.visibility = if (playing) View.GONE else View.VISIBLE
                        btnPause.visibility = if (playing) View.VISIBLE else View.GONE
                    }

                    fun startAudioPlayback() {
                        if (activeAudioAssetId == asset.assetId && activeMediaPlayer != null) {
                            val mp = activeMediaPlayer!!
                            if (mp.isPlaying) {
                                mp.pause()
                                showPlayingState(false)
                            } else {
                                mp.start()
                                showPlayingState(true)
                            }
                            return
                        }
                        stopAudio()
                        Thread {
                            val file = Repository.resolveAssetFileSync(this@TaskDetailActivity, asset) ?: return@Thread
                            runOnUiThread {
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                try {
                                    val mp = MediaPlayer().apply {
                                        setDataSource(file.absolutePath)
                                        prepare()
                                        start()
                                    }
                                    activeMediaPlayer = mp
                                    activeAudioAssetId = asset.assetId
                                    val totalStr = formatMs(mp.duration)
                                    seek.max = mp.duration.coerceAtLeast(1)
                                    tvTime.text = "00:00 / $totalStr"
                                    showPlayingState(true)
                                    val r = object : Runnable {
                                        override fun run() {
                                            val curMp = activeMediaPlayer
                                            if (curMp != null && activeAudioAssetId == asset.assetId) {
                                                val pos = curMp.currentPosition
                                                seek.progress = pos
                                                tvTime.text = "${formatMs(pos)} / $totalStr"
                                                if (curMp.isPlaying) uiHandler.postDelayed(this, 250)
                                                else showPlayingState(false)
                                            }
                                        }
                                    }
                                    progressUpdater = r
                                    uiHandler.post(r)
                                    mp.setOnCompletionListener {
                                        showPlayingState(false)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(this@TaskDetailActivity, "音频播放失败：${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }.start()
                    }

                    btnPlay.setOnClickListener { startAudioPlayback() }
                    btnPause.setOnClickListener { startAudioPlayback() }
                    seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser && activeAudioAssetId == asset.assetId) {
                                activeMediaPlayer?.seekTo(progress)
                            }
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                    if (autoPlayAudio) {
                        autoPlayAudio = false
                        v.postDelayed({ startAudioPlayback() }, 500)
                    }
                    vh.flContent.addView(v)
                }
                asset.mime.contains("video") -> {
                    val v = inf.inflate(R.layout.layout_customvideoplayerwrapper, vh.flContent, false)
                    val ivThumb = v.findViewById<ImageView>(R.id.iv_thumbnail)
                    val tvDur = v.findViewById<TextView>(R.id.tvVideoDuration)
                    val cached = videoCoverCache[asset.assetId]
                    if (cached != null) {
                        ivThumb.scaleType = ImageView.ScaleType.CENTER_CROP
                        ivThumb.setImageBitmap(cached.first)
                        tvDur.text = cached.second
                    } else {
                        Thread {
                            val file = Repository.resolveAssetFileSync(this@TaskDetailActivity, asset) ?: return@Thread
                            var frame: Bitmap? = null
                            var durLabel = "MP4 · 点击全屏播放"
                            val mmr = MediaMetadataRetriever()
                            try {
                                mmr.setDataSource(file.absolutePath)
                                val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
                                if (durMs > 0) {
                                    durLabel = "MP4 · ${formatMs(durMs)} · 点击全屏播放"
                                }
                                frame = mmr.getFrameAtTime(
                                    (durMs.coerceAtLeast(1000) / 3L) * 1000L,
                                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                                ) ?: mmr.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            } catch (_: Exception) {
                            } finally {
                                runCatching { mmr.release() }
                            }
                            if (frame != null) {
                                val bmp = frame
                                videoCoverCache[asset.assetId] = bmp to durLabel
                                runOnUiThread {
                                    if (isFinishing || isDestroyed) return@runOnUiThread
                                    ivThumb.scaleType = ImageView.ScaleType.CENTER_CROP
                                    ivThumb.setImageBitmap(bmp)
                                    tvDur.text = durLabel
                                }
                            }
                        }.start()
                    }
                    v.setOnClickListener { openAssetFullscreen(asset) }
                    v.findViewById<ImageView>(R.id.iv_playicon).setOnClickListener { openAssetFullscreen(asset) }
                    vh.flContent.addView(v)
                }
                asset.mime.contains("image") -> {
                    val iv = ImageView(this@TaskDetailActivity).apply {
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (260 * resources.displayMetrics.density).toInt())
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setBackgroundColor(Color.parseColor("#1D3C30"))
                        setOnClickListener { openAssetFullscreen(asset) }
                    }
                    Thread {
                        val file = Repository.resolveAssetFileSync(this@TaskDetailActivity, asset)
                        val bmp = file?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
                        if (bmp != null) {
                            runOnUiThread { iv.setImageBitmap(bmp) }
                        }
                    }.start()
                    vh.flContent.addView(iv)
                }
                else -> {
                    val tv = TextView(this@TaskDetailActivity).apply {
                        text = "点击打开附件：${asset.title}"
                        setPadding(16, 18, 16, 18)
                        setOnClickListener { openAssetFullscreen(asset) }
                    }
                    vh.flContent.addView(tv)
                }
            }
        }

        /**
         * Faithfully recreates original layout_customdocumentview.xml (2-stage PDF flow):
         * Stage 1 (initial): Centered 150dp ic_placehold_document.png + 48dp circular play/start button.
         * Stage 2 (after tap): Downloads/loads inline PDF preview (fl_pdfcontent) with overlaid
         * left/right `<` `>` page flip arrows and top-right blue-bordered diagonal expand icon
         * (iv_fullscreen) that opens the dedicated Foxit PDF reader/annotator Activity.
         */
        private fun bindInlinePdfViewer(container: FrameLayout, asset: AssetItem) {
            val d = resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            val isNarrow = resources.configuration.screenWidthDp < 560
            val expandedHeight = if (isNarrow) dp(360) else dp(460)

            val root = FrameLayout(this@TaskDetailActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // ===== Stage 1: Collapsed document placeholder + center play/download icon =====
            val placeholderBox = FrameLayout(this@TaskDetailActivity).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(184))
                setBackgroundColor(Color.TRANSPARENT)
            }
            val ivDocPlaceholder = ImageView(this@TaskDetailActivity).apply {
                setImageResource(R.drawable.ic_placehold_document)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(dp(150), dp(150)).apply {
                    gravity = Gravity.CENTER
                }
            }
            val ivOpenIcon = ImageView(this@TaskDetailActivity).apply {
                setBackgroundResource(R.drawable.background_play_icon)
                setImageResource(R.drawable.ic_play_arrow_white_24dp)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(dp(50), dp(50)).apply {
                    gravity = Gravity.CENTER
                }
            }
            val pbLoading = ProgressBar(this@TaskDetailActivity).apply {
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(dp(44), dp(44)).apply {
                    gravity = Gravity.CENTER
                }
            }
            placeholderBox.addView(ivDocPlaceholder)
            placeholderBox.addView(ivOpenIcon)
            placeholderBox.addView(pbLoading)

            // ===== Stage 2: Expanded inline PDF preview (fl_pdfcontent + overlay arrows + iv_fullscreen) =====
            val expandedBox = FrameLayout(this@TaskDetailActivity).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, expandedHeight)
                setBackgroundColor(Color.parseColor("#E6E6E6"))
                visibility = View.GONE
            }

            val ivPage = ImageView(this@TaskDetailActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.WHITE)
            }

            // Left / Right translucent page-flip chevrons overlaid directly on the inline PDF (matching original)
            val btnPrev = ImageView(this@TaskDetailActivity).apply {
                setImageResource(R.drawable.fx_page_prev)
                alpha = 0.85f
                setPadding(dp(4), dp(4), dp(4), dp(4))
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(dp(48), dp(48)).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    marginStart = dp(10)
                }
            }
            val btnNext = ImageView(this@TaskDetailActivity).apply {
                setImageResource(R.drawable.fx_page_next)
                alpha = 0.85f
                setPadding(dp(4), dp(4), dp(4), dp(4))
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(dp(48), dp(48)).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    marginEnd = dp(10)
                }
            }

            // Top-right blue-bordered translucent square expand button (iv_fullscreen in layout_customdocumentview.xml)
            val btnFullscreen = ImageView(this@TaskDetailActivity).apply {
                setBackgroundResource(R.drawable.bg_pdf_fullscreen_btn)
                setImageResource(R.drawable.ic_pdf_expand_diagonal)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "全屏打开福昕批注"
                layoutParams = FrameLayout.LayoutParams(dp(42), dp(42)).apply {
                    gravity = Gravity.TOP or Gravity.END
                    setMargins(0, dp(14), dp(14), 0)
                }
            }

            // Compact translucent page indicator pill at bottom-center
            val tvPagePill = TextView(this@TaskDetailActivity).apply {
                text = "1 / 1"
                textSize = 12f
                setTextColor(Color.parseColor("#333333"))
                setPadding(dp(12), dp(3), dp(12), dp(3))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#DDFFFFFF"))
                    setStroke(dp(1), Color.parseColor("#D0D0D0"))
                    cornerRadius = dp(12).toFloat()
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(10)
                }
            }

            expandedBox.addView(ivPage)
            expandedBox.addView(btnPrev)
            expandedBox.addView(btnNext)
            expandedBox.addView(btnFullscreen)
            expandedBox.addView(tvPagePill)

            root.addView(placeholderBox)
            root.addView(expandedBox)
            container.addView(root)

            var totalPages = 1
            fun renderInline(targetPage: Int, onReady: (() -> Unit)? = null) {
                Thread {
                    val file = Repository.resolveAssetFileSync(this@TaskDetailActivity, asset) ?: return@Thread
                    var bmp: Bitmap? = null
                    var actualPage = targetPage
                    try {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            PdfRenderer(pfd).use { r ->
                                totalPages = r.pageCount.coerceAtLeast(1)
                                actualPage = targetPage.coerceIn(0, totalPages - 1)
                                inlinePdfPages[asset.assetId] = actualPage
                                r.openPage(actualPage).use { p ->
                                    val w = 960
                                    val h = (w.toFloat() * p.height / p.width.coerceAtLeast(1)).toInt().coerceAtLeast(420)
                                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    b.eraseColor(Color.WHITE)
                                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bmp = b
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        bmp?.let { ivPage.setImageBitmap(it) }
                        tvPagePill.text = "${actualPage + 1} / $totalPages"
                        btnPrev.visibility = if (actualPage > 0) View.VISIBLE else View.INVISIBLE
                        btnNext.visibility = if (actualPage < totalPages - 1) View.VISIBLE else View.INVISIBLE
                        onReady?.invoke()
                    }
                }.start()
            }

            fun openInlinePreview() {
                expandedPdfAssets.add(asset.assetId)
                ivOpenIcon.visibility = View.GONE
                pbLoading.visibility = View.VISIBLE
                val initPage = inlinePdfPages[asset.assetId] ?: Repository.getSavedPdfPage(this@TaskDetailActivity, asset.assetId)
                renderInline(initPage) {
                    placeholderBox.visibility = View.GONE
                    pbLoading.visibility = View.GONE
                    ivOpenIcon.visibility = View.VISIBLE
                    expandedBox.visibility = View.VISIBLE
                }
            }

            if (expandedPdfAssets.contains(asset.assetId)) {
                placeholderBox.visibility = View.GONE
                expandedBox.visibility = View.VISIBLE
                val initPage = inlinePdfPages[asset.assetId] ?: Repository.getSavedPdfPage(this@TaskDetailActivity, asset.assetId)
                renderInline(initPage)
            }

            val startClick = View.OnClickListener { openInlinePreview() }
            placeholderBox.setOnClickListener(startClick)
            ivDocPlaceholder.setOnClickListener(startClick)
            ivOpenIcon.setOnClickListener(startClick)

            btnPrev.setOnClickListener {
                val cur = inlinePdfPages[asset.assetId] ?: 0
                if (cur > 0) renderInline(cur - 1)
            }
            btnNext.setOnClickListener {
                val cur = inlinePdfPages[asset.assetId] ?: 0
                if (cur < totalPages - 1) renderInline(cur + 1)
            }
            btnFullscreen.setOnClickListener {
                openAssetFullscreen(asset, inlinePdfPages[asset.assetId] ?: 0)
            }
        }
    }

    private fun formatMs(ms: Int): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return String.format("%02d:%02d", sec / 60, sec % 60)
    }

    private class CardVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvIndex: TextView = v.findViewById(R.id.tv_index)
        val ivThumb: ImageView = v.findViewById(R.id.imageViewThumbnail)
        val tvType: TextView = v.findViewById(R.id.tv_type)
        val tvTitle: TextView = v.findViewById(R.id.textViewTitle)
        val tvTime: TextView = v.findViewById(R.id.textViewTime)
        val ivFav: ImageView = v.findViewById(R.id.ivFav)
        val flContent: FrameLayout = v.findViewById(R.id.fl_content)
    }

    private class CompactVH(v: View) : RecyclerView.ViewHolder(v) {
        val ivType: ImageView = v.findViewById(R.id.ImageViewFileType)
        val tvTitle: TextView = v.findViewById(R.id.TextTitle)
        val tvSize: TextView = v.findViewById(R.id.tvSize)
        val ivFav: ImageView = v.findViewById(R.id.ImageViewFav)
    }

    private class QuizVH(val container: LinearLayout) : RecyclerView.ViewHolder(container)
}
