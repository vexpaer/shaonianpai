package com.shaonianpai.tablet.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.AssetItem
import com.shaonianpai.tablet.data.Repository

class DocumentViewActivity : AppCompatActivity() {

    private lateinit var asset: AssetItem
    private lateinit var ivPdfPage: ImageView
    private lateinit var tvPageBadge: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: ScrollView

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documentview)

        @Suppress("DEPRECATION")
        asset = (intent.getSerializableExtra("asset") as? AssetItem)
            ?: Repository.builtInFallbackTasks().first().assets.first()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = asset.title

        ivPdfPage = findViewById(R.id.ivPdfPage)
        tvPageBadge = findViewById(R.id.tvPageBadge)
        progressBar = findViewById(R.id.progressBar1)
        scrollView = findViewById(R.id.pdfScroll)

        val requestedPage = intent.getIntExtra("page", -1)
        currentPage = if (requestedPage >= 0) requestedPage else Repository.getSavedPdfPage(this, asset.assetId)

        Thread {
            val file = Repository.resolveAssetFileSync(this, asset)
            if (file == null) {
                runOnUiThread { Toast.makeText(this, "无法读取 PDF 文件", Toast.LENGTH_SHORT).show() }
                return@Thread
            }
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd!!)
                runOnUiThread { renderPage(currentPage) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "PDF 解析失败：${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_documentview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_prev -> {
                if (currentPage > 0) renderPage(currentPage - 1)
                else Toast.makeText(this, "已经是第一页", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_next -> {
                val count = renderer?.pageCount ?: 1
                if (currentPage < count - 1) renderPage(currentPage + 1)
                else Toast.makeText(this, "已经是最后一页", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_open -> {
                Toast.makeText(this, "已缓存到本地，支持外部阅读器打开", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun renderPage(index: Int) {
        val r = renderer ?: return
        if (r.pageCount <= 0) return
        currentPage = index.coerceIn(0, r.pageCount - 1)
        Repository.savePdfPage(this, asset.assetId, currentPage)
        r.openPage(currentPage).use { page ->
            val targetW = 960
            val targetH = (targetW.toFloat() * page.height / page.width).toInt().coerceAtLeast(400)
            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            ivPdfPage.setImageBitmap(bmp)
            tvPageBadge.text = "第 ${currentPage + 1} / ${r.pageCount} 页"
            progressBar.progress = (((currentPage + 1) * 10000f) / r.pageCount).toInt()
            scrollView.scrollTo(0, 0)
        }
    }

    override fun onDestroy() {
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
