package com.shaonianpai.tablet.ui

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.AssetItem
import com.shaonianpai.tablet.data.Repository

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var tvCurrent: TextView
    private lateinit var tvTotal: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnCenterPlay: ImageView
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            if (videoView.isPlaying) {
                val pos = videoView.currentPosition
                val dur = videoView.duration.coerceAtLeast(1000)
                seekBar.max = dur
                seekBar.progress = pos
                tvCurrent.text = fmtMs(pos)
                tvTotal.text = fmtMs(dur)
                handler.postDelayed(this, 250)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_videoplayer2)

        @Suppress("DEPRECATION")
        val asset = (intent.getSerializableExtra("asset") as? AssetItem)
            ?: Repository.builtInFallbackTasks().first().assets.first { it.mime.contains("video") }

        videoView = findViewById(R.id.videoView)
        tvCurrent = findViewById(R.id.current)
        tvTotal = findViewById(R.id.total)
        seekBar = findViewById(R.id.progress)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnCenterPlay = findViewById(R.id.btnCenterPlay)

        findViewById<TextView>(R.id.title).text = asset.title
        findViewById<ImageView>(R.id.back).setOnClickListener { finish() }

        btnPlayPause.setOnClickListener { togglePlay() }
        btnCenterPlay.setOnClickListener { togglePlay() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) videoView.seekTo(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        Thread {
            val file = Repository.resolveAssetFileSync(this, asset)
            if (file != null) {
                runOnUiThread {
                    videoView.setVideoURI(Uri.fromFile(file))
                    videoView.setOnPreparedListener { mp ->
                        mp.isLooping = true
                        val dur = videoView.duration.coerceAtLeast(1000)
                        tvTotal.text = fmtMs(dur)
                        seekBar.max = dur
                        videoView.start()
                        videoView.seekTo(1200)
                        handler.post(ticker)
                    }
                }
            }
        }.start()
    }

    private fun togglePlay() {
        if (videoView.isPlaying) {
            videoView.pause()
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            btnCenterPlay.visibility = View.VISIBLE
        } else {
            videoView.start()
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            btnCenterPlay.visibility = View.GONE
            handler.post(ticker)
        }
    }

    private fun fmtMs(ms: Int): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return String.format("%02d:%02d", sec / 60, sec % 60)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
