package com.shaonianpai.tablet.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.Repository
import com.shaonianpai.tablet.data.UpdateCheckResult
import com.shaonianpai.tablet.data.UpdateManager

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences("shaonianpai_prefs", MODE_PRIVATE)

        // GitHub auto-update controls
        val tvCurrentVersion = findViewById<TextView>(R.id.tvCurrentVersion)
        val swAutoUpdate = findViewById<Switch>(R.id.swAutoUpdate)
        val etGithubRepo = findViewById<EditText>(R.id.etGithubRepo)
        val btnCheckUpdate = findViewById<Button>(R.id.btnCheckUpdate)
        val pbCheckUpdate = findViewById<ProgressBar>(R.id.pbCheckUpdate)
        val tvUpdateStatus = findViewById<TextView>(R.id.tvUpdateStatus)

        val curVer = UpdateManager.getCurrentVersionName(this)
        tvCurrentVersion.text = "当前版本：v$curVer"
        swAutoUpdate.isChecked = UpdateManager.isAutoCheckEnabled(this)
        swAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            UpdateManager.setAutoCheckEnabled(this, isChecked)
        }
        etGithubRepo.setText(UpdateManager.getGithubRepo(this))

        btnCheckUpdate.setOnClickListener {
            val repo = etGithubRepo.text.toString().trim().ifBlank { UpdateManager.DEFAULT_GITHUB_REPO }
            UpdateManager.setGithubRepo(this, repo)
            btnCheckUpdate.isEnabled = false
            pbCheckUpdate.visibility = View.VISIBLE
            tvUpdateStatus.text = "正在从 GitHub（$repo）检查最新版本..."
            Thread {
                val result = UpdateManager.checkLatestReleaseSync(this)
                runOnUiThread {
                    btnCheckUpdate.isEnabled = true
                    pbCheckUpdate.visibility = View.GONE
                    when (result) {
                        is UpdateCheckResult.UpdateAvailable -> {
                            tvUpdateStatus.text =
                                "发现新版本 ${result.release.tagName}（当前 v${result.currentVersion}），点击弹窗即可下载安装"
                            UpdateUiHelper.showUpdateDialog(this, result.currentVersion, result.release)
                        }
                        is UpdateCheckResult.UpToDate -> {
                            tvUpdateStatus.text =
                                "当前已是最新版本（v${result.currentVersion}，GitHub 最新：${result.latestTag}）"
                            Toast.makeText(this, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                        }
                        is UpdateCheckResult.Error -> {
                            tvUpdateStatus.text = result.message
                            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.start()
        }

        // Desk icon controls
        val swLock = findViewById<Switch>(R.id.swLockIcons)
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

        // Teacher server controls
        val etServer = findViewById<EditText>(R.id.etServerUrl)
        val tvStatus = findViewById<TextView>(R.id.tvConnectionStatus)
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
