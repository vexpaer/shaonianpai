package com.shaonianpai.tablet.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.Repository
import com.shaonianpai.tablet.data.UpdateCheckResult
import com.shaonianpai.tablet.data.UpdateManager
import com.shaonianpai.tablet.data.UserProfileStore
import kotlin.math.abs

/**
 * Pure Shaonianpai Study-Desk Desktop:
 * - Background stretches full-screen (fitXY) in BOTH landscape & portrait (supports custom wallpaper from Settings).
 * - All 7 desk items use normalized relative coordinates [0..1] + OnLayoutChangeListener + live container
 *   dimensions on drag so rotating landscape <-> portrait NEVER hides icons or traps drag at a stale boundary.
 * - Open book (notebook) -> ResourceLibraryActivity (我的资源库); Bookbag -> AppsLauncherActivity (软件入口).
 * - Left drawer clears monochrome tint so all full-color feature icons render cleanly.
 * - Bottom-right shows ONLY the version number.
 */
class DesktopActivity : AppCompatActivity() {

    private data class DeskItemSpec(
        val key: String,
        val label: String,
        val drawableRes: Int,
        val defaultRelX: Float, // normalized 0..1 relative to 1024 base width
        val defaultRelY: Float, // normalized 0..1 relative to 768 base height
        val relW: Float,
        val relH: Float
    )

    private val items = listOf(
        DeskItemSpec("rewards", "人教数字教材", R.drawable.ic_desktop_rewards, 180f / 1024f, 250f / 768f, 103f / 1024f, 138f / 768f),
        DeskItemSpec("clock", "课程闹钟", R.drawable.ic_desktop_clock, 270f / 1024f, 290f / 768f, 62f / 1024f, 103f / 768f),
        DeskItemSpec("schedule", "日历 / 课程表", R.drawable.ic_desktop_schedule, 350f / 1024f, 300f / 768f, 101f / 1024f, 72f / 768f),
        DeskItemSpec("notebook", "我的资源库", R.drawable.ic_desktop_notebook, 460f / 1024f, 320f / 768f, 148f / 1024f, 65f / 768f),
        DeskItemSpec("study", "自主学习", R.drawable.ic_desktop_study, 610f / 1024f, 330f / 768f, 95f / 1024f, 46f / 768f),
        DeskItemSpec("light", "系统设置", R.drawable.ic_desktop_light, 680f / 1024f, 170f / 768f, 131f / 1024f, 208f / 768f),
        DeskItemSpec("bookbag", "软件与应用入口", R.drawable.ic_desktop_bookbag, 780f / 1024f, 260f / 768f, 132f / 1024f, 122f / 768f)
    )

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rootView: RelativeLayout
    private lateinit var ivDesktopBg: ImageView
    private lateinit var deskContainer: FrameLayout
    private lateinit var navView: NavigationView
    private lateinit var tvStatus: TextView
    private lateinit var tvVersion: TextView

    private var lastDeskW = -1
    private var lastDeskH = -1

    companion object {
        private var startupUpdateChecked = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desktop)

        drawerLayout = findViewById(R.id.drawerLayout)
        rootView = findViewById(R.id.MainDesktopView2)
        ivDesktopBg = findViewById(R.id.ivDesktopBg)
        deskContainer = findViewById(R.id.MainDesktopView)
        navView = findViewById(R.id.navigationView)
        tvStatus = findViewById(R.id.textViewIMState)
        tvVersion = findViewById(R.id.textViewVersion)

        // Preserve full-color original icons in left navigation drawer (fixes broken grey squares under 「功能」)
        navView.itemIconTintList = null

        // Bottom-right displays ONLY the version string
        tvStatus.visibility = View.GONE
        tvVersion.text = "v${UpdateManager.getCurrentVersionName(this)}"

        findViewById<ImageView>(R.id.imageButtonDrawer).setOnClickListener {
            syncDrawerHeader()
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.buttonNetworkCheck -> checkServer(showToast = true)
                R.id.buttonGoOffline -> Toast.makeText(this, "离线学习数据已缓存（3 科 5 任务）", Toast.LENGTH_SHORT).show()
                R.id.buttonSynchronizeNow -> checkServer(showToast = true)
                R.id.buttonSettings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.buttonLogout -> startActivity(Intent(this, LoginActivity::class.java))
                R.id.nav_schedule -> startActivity(Intent(this, ScheduleActivity::class.java))
                R.id.nav_selflearn -> startActivity(Intent(this, SelfLearnActivity::class.java))
                R.id.nav_library -> startActivity(Intent(this, ResourceLibraryActivity::class.java))
                R.id.nav_questionbook -> startActivity(Intent(this, QuestionBookActivity::class.java))
                R.id.nav_apps -> BookbagDialog.show(this)
                R.id.nav_clock -> startActivity(Intent(this, ScheduleActivity::class.java))
                R.id.nav_rewards -> startActivity(Intent(this, BookDetailActivity::class.java))
                R.id.nav_brightness -> showBrightnessDialog()
            }
            true
        }

        // Always re-layout desk icons when the actual laid-out dimensions change (rotation / split-screen)
        deskContainer.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val cw = r - l
            val ch = b - t
            if (cw > 0 && ch > 0 && (cw != lastDeskW || ch != lastDeskH)) {
                lastDeskW = cw
                lastDeskH = ch
                deskContainer.post { layoutDeskItems() }
            }
        }

        rootView.post { refreshDesktop(forceRelayout = true) }
        checkServer(showToast = false)
        maybeAutoCheckUpdate()

        if (intent.getBooleanExtra("open_drawer", false)) {
            drawerLayout.post {
                syncDrawerHeader()
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }
        if (intent.getBooleanExtra("demo_open_bookbag", false)) {
            rootView.post { BookbagDialog.show(this) }
        }
    }

    private fun showBrightnessDialog() {
        val sb = android.widget.SeekBar(this).apply {
            max = 100
            val cur = ((window.attributes.screenBrightness.takeIf { it > 0f } ?: 0.75f) * 100).toInt()
            progress = cur
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val lp = window.attributes
                        lp.screenBrightness = (p / 100f).coerceIn(0.08f, 1f)
                        window.attributes = lp
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        val d = resources.displayMetrics.density
        sb.setPadding((20 * d).toInt(), (16 * d).toInt(), (20 * d).toInt(), (12 * d).toInt())
        android.app.AlertDialog.Builder(this)
            .setTitle("屏幕亮度设置")
            .setView(sb)
            .setPositiveButton("确定", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        rootView.post { refreshDesktop(forceRelayout = true) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        lastDeskW = -1
        lastDeskH = -1
        deskContainer.requestLayout()
        deskContainer.post { refreshDesktop(forceRelayout = true) }
    }

    private fun refreshDesktop(forceRelayout: Boolean = false) {
        UserProfileStore.applyDesktopBgTo(this, ivDesktopBg)
        syncDrawerHeader()
        if (forceRelayout) {
            lastDeskW = deskContainer.width
            lastDeskH = deskContainer.height
            layoutDeskItems()
        }
    }

    private fun syncDrawerHeader() {
        val header = navView.getHeaderView(0) ?: return
        header.setBackgroundColor(UserProfileStore.getThemePrimaryColor(this))
        val ivAvatar = header.findViewById<ImageView>(R.id.iv_thumbnail)
        val tvName = header.findViewById<TextView>(R.id.textViewStudentName)
        val tvInfo = header.findViewById<TextView>(R.id.textViewServerInfo)
        UserProfileStore.applyAvatarTo(this, ivAvatar)
        val name = UserProfileStore.getUserName(this)
        val sid = UserProfileStore.getStudentId(this)
        tvName.text = name
        tvInfo.text = "$sid · ${Repository.getServerUrl(this).removePrefix("http://").removePrefix("https://")}"
        ivAvatar.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun maybeAutoCheckUpdate() {
        if (startupUpdateChecked || !UpdateManager.isAutoCheckEnabled(this)) return
        startupUpdateChecked = true
        Thread {
            val result = UpdateManager.checkLatestReleaseSync(this)
            if (result is UpdateCheckResult.UpdateAvailable) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        UpdateUiHelper.showUpdateDialog(this, result.currentVersion, result.release)
                    }
                }
            }
        }.start()
    }

    private fun checkServer(showToast: Boolean) {
        Thread {
            val (tasks, online) = Repository.fetchDeliveriesSync(this)
            if (showToast) {
                runOnUiThread {
                    val msg = if (online) {
                        "已连接教师服务 · 同步 ${tasks.size} 项自主学习任务"
                    } else {
                        "当前为离线缓存模式 · 本地 ${tasks.size} 项任务"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun layoutDeskItems() {
        val canvasW = deskContainer.width.toFloat()
        val canvasH = deskContainer.height.toFloat()
        if (canvasW <= 0f || canvasH <= 0f) return

        deskContainer.removeAllViews()
        val prefs = getSharedPreferences("shaonianpai_prefs", MODE_PRIVATE)
        val locked = prefs.getBoolean("LockIcons", false)
        val visibleKeys = UserProfileStore.getVisibleDeskIcons(this)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val minTapPx = (36 * resources.displayMetrics.density).toInt()

        for (spec in items) {
            if (!visibleKeys.contains(spec.key)) continue

            val rx = (when {
                prefs.contains("pos_rx_${spec.key}") -> prefs.getFloat("pos_rx_${spec.key}", spec.defaultRelX)
                prefs.contains("pos_x_${spec.key}") -> prefs.getFloat("pos_x_${spec.key}", spec.defaultRelX * 1024f) / 1024f
                else -> spec.defaultRelX
            }).coerceIn(0f, 0.92f)

            val ry = (when {
                prefs.contains("pos_ry_${spec.key}") -> prefs.getFloat("pos_ry_${spec.key}", spec.defaultRelY)
                prefs.contains("pos_y_${spec.key}") -> prefs.getFloat("pos_y_${spec.key}", spec.defaultRelY * 768f) / 768f
                else -> spec.defaultRelY
            }).coerceIn(0f, 0.92f)

            val itemW = (spec.relW * canvasW).toInt().coerceIn(minTapPx, canvasW.toInt().coerceAtLeast(minTapPx))
            val itemH = (spec.relH * canvasH).toInt().coerceIn(minTapPx, canvasH.toInt().coerceAtLeast(minTapPx))
            val leftPx = (rx * canvasW).toInt().coerceIn(0, (canvasW.toInt() - itemW).coerceAtLeast(0))
            val topPx = (ry * canvasH).toInt().coerceIn(0, (canvasH.toInt() - itemH).coerceAtLeast(0))

            val iv = ImageView(this).apply {
                setImageResource(spec.drawableRes)
                scaleType = ImageView.ScaleType.FIT_XY
                contentDescription = spec.label
            }
            val ilp = FrameLayout.LayoutParams(itemW, itemH).apply {
                leftMargin = leftPx
                topMargin = topPx
            }

            var downRawX = 0f
            var downRawY = 0f
            var startLeft = 0
            var startTop = 0
            var dragging = false

            iv.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        val clp = v.layoutParams as FrameLayout.LayoutParams
                        startLeft = clp.leftMargin
                        startTop = clp.topMargin
                        dragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!locked && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            dragging = true
                        }
                        if (dragging) {
                            // Always read LIVE container width/height so rotation NEVER creates a stale center wall!
                            val liveW = deskContainer.width.coerceAtLeast(1)
                            val liveH = deskContainer.height.coerceAtLeast(1)
                            val clp = v.layoutParams as FrameLayout.LayoutParams
                            val maxLeft = (liveW - clp.width).coerceAtLeast(0)
                            val maxTop = (liveH - clp.height).coerceAtLeast(0)
                            clp.leftMargin = (startLeft + dx).toInt().coerceIn(0, maxLeft)
                            clp.topMargin = (startTop + dy).toInt().coerceIn(0, maxTop)
                            v.layoutParams = clp
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragging) {
                            val liveW = deskContainer.width.toFloat().coerceAtLeast(1f)
                            val liveH = deskContainer.height.toFloat().coerceAtLeast(1f)
                            val clp = v.layoutParams as FrameLayout.LayoutParams
                            prefs.edit()
                                .putFloat("pos_rx_${spec.key}", (clp.leftMargin / liveW).coerceIn(0f, 0.92f))
                                .putFloat("pos_ry_${spec.key}", (clp.topMargin / liveH).coerceIn(0f, 0.92f))
                                .apply()
                        } else {
                            openDeskItem(spec.key)
                        }
                        true
                    }
                    else -> false
                }
            }
            deskContainer.addView(iv, ilp)
        }
    }

    private fun openDeskItem(key: String) {
        when (key) {
            "study" -> startActivity(Intent(this, SelfLearnActivity::class.java))
            "notebook" -> startActivity(Intent(this, ResourceLibraryActivity::class.java))
            "bookbag" -> BookbagDialog.show(this)
            "schedule", "clock" -> startActivity(Intent(this, ScheduleActivity::class.java))
            "rewards" -> startActivity(Intent(this, BookDetailActivity::class.java))
            "light" -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> startActivity(Intent(this, SelfLearnActivity::class.java))
        }
    }
}
