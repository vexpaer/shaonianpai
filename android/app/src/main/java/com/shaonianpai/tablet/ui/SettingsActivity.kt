package com.shaonianpai.tablet.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.Repository

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences("shaonianpai_prefs", MODE_PRIVATE)
        val swLock = findViewById<Switch>(R.id.swLockIcons)
        val etServer = findViewById<EditText>(R.id.etServerUrl)
        val tvStatus = findViewById<TextView>(R.id.tvConnectionStatus)

        swLock.isChecked = prefs.getBoolean("LockIcons", false)
        swLock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("LockIcons", isChecked).apply()
        }

        findViewById<Button>(R.id.btnResetPositions).setOnClickListener {
            val ed = prefs.edit()
            for (k in listOf("rewards", "clock", "schedule", "notebook", "study", "light", "bookbag")) {
                ed.remove("pos_x_$k").remove("pos_y_$k")
            }
            ed.apply()
            Toast.makeText(this, "已还原全部 7 个桌面图标默认坐标", Toast.LENGTH_SHORT).show()
        }

        etServer.setText(Repository.getServerUrl(this))
        findViewById<Button>(R.id.btnSaveServer).setOnClickListener {
            Repository.setServerUrl(this, etServer.text.toString().trim())
            tvStatus.text = "正在测试连接..."
            Thread {
                val (tasks, online) = Repository.fetchDeliveriesSync(this)
                runOnUiThread {
                    tvStatus.text = if (online) {
                        "状态：连接成功（拉取到 ${tasks.size} 个任务）"
                    } else {
                        "状态：未连上服务器，已自动回退离线测试数据（${tasks.size} 个任务）"
                    }
                }
            }.start()
        }
    }
}
