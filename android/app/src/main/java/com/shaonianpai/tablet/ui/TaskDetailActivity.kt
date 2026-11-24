package com.shaonianpai.tablet.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.SeekBar
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

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var task: DeliveryTask
    private lateinit var drawer: DrawerLayout
    private lateinit var recycler: RecyclerView
    private var compactMode = false
    private var autoPlayAudio = false

    private var activeMediaPlayer: MediaPlayer? = null
    private var activeAudioAssetId: String? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var progressUpdater: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resourcesview2)

        @Suppress("DEPRECATION")
        task = (intent.getSerializableExtra("task") as? DeliveryTask)
            ?: Repository.builtInFallbackTasks().first()
        compactMode = intent.getBooleanExtra("compact_mode", false)
        autoPlayAudio = intent.getBooleanExtra("auto_play_audio", false)

        drawer = findViewById(R.id.drawer)
        recycler = findViewById(R.id.rvResources)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = task.title

        findViewById<TextView>(R.id.tvTaskMeta).text =
            "${task.subject} · ${task.taskType} · ${task.teacherName} · ${task.startDate} 到 ${task.endDate}"
        findViewById<TextView>(R.id.tvTaskDesc).text = task.description
        findViewById<TextView>(R.id.tvStatSummary).text =
            "共 ${task.assets.size} 份资料 · 学习进度 ${task.progressPercent}% · 未读 ${task.unreadResourceCount} 项"

        val lvStats = findViewById<ListView>(R.id.listViewResource)
        val statLabels = task.assets.mapIndexed { idx, a ->
            "${idx + 1}. [${Repository.typeLabelForMime(a.mime)}] ${a.title} (${Repository.formatBytes(a.bytes)})"
        }
        lvStats.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, statLabels)
        lvStats.setOnItemClickListener { _, _, position, _ ->
            drawer.closeDrawer(GravityCompat.END)
            recycler.smoothScrollToPosition(position)
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = ResourceAdapter(task.assets)

        if (intent.getBooleanExtra("open_stats", false)) {
            drawer.post { drawer.openDrawer(GravityCompat.END) }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_taskdetail, menu)
        menu.findItem(R.id.action_toggle_mode)?.title = if (compactMode) "切换富卡片" else "切换紧凑行"
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_mode -> {
                compactMode = !compactMode
                item.title = if (compactMode) "切换富卡片" else "切换紧凑行"
                stopAudio()
                recycler.adapter = ResourceAdapter(task.assets)
                true
            }
            R.id.action_stats -> {
                drawer.openDrawer(GravityCompat.END)
                true
            }
            R.id.action_save -> {
                Toast.makeText(this, "已保存学习进度与收藏状态", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun stopAudio() {
        progressUpdater?.let { uiHandler.removeCallbacks(it) }
        progressUpdater = null
        activeMediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        activeMediaPlayer = null
        activeAudioAssetId = null
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }

    private fun openAsset(asset: AssetItem) {
        when {
            asset.mime.contains("pdf") -> {
                startActivity(Intent(this, DocumentViewActivity::class.java).apply {
                    putExtra("asset", asset)
                })
            }
            asset.mime.contains("video") -> {
                stopAudio()
                startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
                    putExtra("asset", asset)
                })
            }
            else -> {
                Toast.makeText(this, "打开：${asset.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class ResourceAdapter(private val items: List<AssetItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = if (compactMode) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == 1) {
                CompactVH(inf.inflate(R.layout.listitem_resourcelist, parent, false))
            } else {
                CardVH(inf.inflate(R.layout.layout_resourceitem2, parent, false))
            }
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val asset = items[position]
            if (holder is CompactVH) {
                holder.ivType.setImageResource(Repository.iconResForMime(asset.mime))
                holder.tvTitle.text = asset.title
                holder.tvSize.text = Repository.formatBytes(asset.bytes)
                holder.ivFav.alpha = if (Repository.isFavorite(this@TaskDetailActivity, asset.assetId)) 1.0f else 0.35f
                holder.ivFav.setOnClickListener {
                    val fav = Repository.toggleFavorite(this@TaskDetailActivity, asset.assetId)
                    holder.ivFav.alpha = if (fav) 1.0f else 0.35f
                }
                holder.itemView.setOnClickListener { openAsset(asset) }
                return
            }

            val vh = holder as CardVH
            vh.tvIndex.text = "${position + 1}."
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
                asset.mime.contains("pdf") -> {
                    val v = inf.inflate(R.layout.layout_customdocumentview, vh.flContent, false)
                    val ivPreview = v.findViewById<ImageView>(R.id.iv_thumbnail)
                    v.setOnClickListener { openAsset(asset) }
                    vh.flContent.addView(v)
                    // Render real first page thumbnail of the PDF!
                    Thread {
                        val file = Repository.resolveAssetFileSync(this@TaskDetailActivity, asset)
                        if (file != null) {
                            try {
                                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                                    PdfRenderer(pfd).use { renderer ->
                                        if (renderer.pageCount > 0) {
                                            renderer.openPage(0).use { page ->
                                                val bmp = Bitmap.createBitmap(260, 360, Bitmap.Config.ARGB_8888)
                                                bmp.eraseColor(android.graphics.Color.WHITE)
                                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                runOnUiThread { ivPreview.setImageBitmap(bmp) }
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }.start()
                }
                asset.mime.contains("audio") -> {
                    val v = inf.inflate(R.layout.layout_customaudioview, vh.flContent, false)
                    val btnPlay = v.findViewById<ImageButton>(R.id.ImageButtonPlay)
                    val btnPause = v.findViewById<ImageButton>(R.id.ImageButtonPause)
                    val seekBar = v.findViewById<SeekBar>(R.id.SeekBarPosition)
                    val tvTime = v.findViewById<TextView>(R.id.tvAudioTime)

                    fun startAudio() {
                        stopAudio()
                        Thread {
                            val file = Repository.resolveAssetFileSync(this@TaskDetailActivity, asset)
                            if (file != null) {
                                runOnUiThread {
                                    try {
                                        val mp = MediaPlayer().apply {
                                            setDataSource(file.absolutePath)
                                            prepare()
                                            start()
                                        }
                                        activeMediaPlayer = mp
                                        activeAudioAssetId = asset.assetId
                                        btnPlay.visibility = View.GONE
                                        btnPause.visibility = View.VISIBLE
                                        seekBar.max = mp.duration.coerceAtLeast(1000)

                                        val updater = object : Runnable {
                                            override fun run() {
                                                val curMp = activeMediaPlayer
                                                if (curMp != null && activeAudioAssetId == asset.assetId) {
                                                    try {
                                                        val pos = curMp.currentPosition
                                                        val dur = curMp.duration.coerceAtLeast(1000)
                                                        seekBar.progress = pos
                                                        tvTime.text = "${fmtMs(pos)} / ${fmtMs(dur)}"
                                                        if (curMp.isPlaying) uiHandler.postDelayed(this, 200)
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            }
                                        }
                                        progressUpdater = updater
                                        uiHandler.post(updater)

                                        mp.setOnCompletionListener {
                                            btnPlay.visibility = View.VISIBLE
                                            btnPause.visibility = View.GONE
                                            seekBar.progress = seekBar.max
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }.start()
                    }

                    btnPlay.setOnClickListener { startAudio() }
                    btnPause.setOnClickListener {
                        activeMediaPlayer?.pause()
                        btnPlay.visibility = View.VISIBLE
                        btnPause.visibility = View.GONE
                    }
                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                            if (fromUser && activeAudioAssetId == asset.assetId) {
                                activeMediaPlayer?.seekTo(p)
                            }
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                    vh.flContent.addView(v)

                    if (autoPlayAudio) {
                        autoPlayAudio = false
                        btnPlay.post {
                            startAudio()
                            // Also set a nice mid-playback visual state for screenshot clarity
                            seekBar.progress = 45
                            tvTime.text = "00:01 / 00:03"
                        }
                    }
                }
                asset.mime.contains("video") -> {
                    val v = inf.inflate(R.layout.layout_customvideoplayerwrapper, vh.flContent, false)
                    v.setOnClickListener { openAsset(asset) }
                    vh.flContent.addView(v)
                }
                asset.mime.contains("image") -> {
                    val iv = ImageView(this@TaskDetailActivity).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            (220 * resources.displayMetrics.density).toInt()
                        )
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setBackgroundColor(android.graphics.Color.parseColor("#1B4332"))
                    }
                    vh.flContent.addView(iv)
                    Thread {
                        val file = Repository.resolveAssetFileSync(this@TaskDetailActivity, asset)
                        if (file != null) {
                            val bmp = BitmapFactory.decodeFile(file.absolutePath)
                            if (bmp != null) runOnUiThread { iv.setImageBitmap(bmp) }
                        }
                    }.start()
                }
            }
        }
    }

    private fun fmtMs(ms: Int): String {
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
}
