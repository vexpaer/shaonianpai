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
