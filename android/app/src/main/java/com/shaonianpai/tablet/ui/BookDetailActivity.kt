package com.shaonianpai.tablet.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.Repository

class BookDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_detail)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp)
        toolbar.setNavigationOnClickListener { finish() }

        if (resources.configuration.screenWidthDp < 600) {
            val container = findViewById<android.widget.LinearLayout>(R.id.bookContentContainer)
            val coverCol = findViewById<android.widget.LinearLayout>(R.id.bookCoverColumn)
            container.orientation = android.widget.LinearLayout.VERTICAL
            coverCol.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (container.childCount > 1) {
                val metaCol = container.getChildAt(1)
                val d = resources.displayMetrics.density
                metaCol.layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (16 * d).toInt() }
            }
        }

        val firstTask = Repository.builtInFallbackTasks().first()
        findViewById<Button>(R.id.btnOpenSamplePdf).setOnClickListener {
            startActivity(Intent(this, DocumentViewActivity::class.java).putExtra("asset", firstTask.assets.first()))
        }
        findViewById<Button>(R.id.btnPlaySampleAudio).setOnClickListener {
            Toast.makeText(this, "打开《岳阳楼记》配套课文范读", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, TaskDetailActivity::class.java).apply {
                putExtra("task", firstTask)
                putExtra("auto_play_audio", true)
            })
        }
    }
}
