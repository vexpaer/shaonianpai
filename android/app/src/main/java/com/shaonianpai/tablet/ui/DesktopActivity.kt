package com.shaonianpai.tablet.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
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
 * - All 7 desk items are stored & mapped using normalized relative coordinates [0..1] so they adapt seamlessly
 *   across landscape tablet & portrait phone, and remain draggable in both orientations unless locked.
 * - Left drawer displays live User Avatar, User Name, Class Name, and Server Info from UserProfileStore.
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
        DeskItemSpec("notebook", "试题本", R.drawable.ic_desktop_notebook, 460f / 1024f, 320f / 768f, 148f / 1024f, 65f / 768f),
        DeskItemSpec("study", "自主学习", R.drawable.ic_desktop_study, 610f / 1024f, 330f / 768f, 95f / 1024f, 46f / 768f),
        DeskItemSpec("light", "系统设置", R.drawable.ic_desktop_light, 680f / 1024f, 170f / 768f, 131f / 1024f, 208f / 768f),
        DeskItemSpec("bookbag", "我的资源库", R.drawable.ic_desktop_bookbag, 780f / 1024f, 260f / 768f, 132f / 1024f, 122f / 768f)
    )

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rootView: RelativeLayout
    private lateinit var ivDesktopBg: ImageView
    private lateinit var deskContainer: FrameLayout
    private lateinit var navView: NavigationView
    private lateinit var tvStatus: TextView
    private lateinit var tvVersion: TextView

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

        tvVersion.text = "v${UpdateManager.getCurrentVersionName(this)}"

        findViewById<ImageView>(R.id.imageButtonDrawer).setOnClickListener {
            syncDrawerHeader()
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.buttonNetworkCheck -> checkServer()
                R.id.buttonGoOffline -> Toast.makeText(this, "离线学习数据已缓存（3 科 5 任务）", Toast.LENGTH_SHORT).show()
                R.id.buttonSynchronizeNow -> checkServer()
                R.id.buttonSettings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.buttonLogout -> startActivity(Intent(this, LoginActivity::class.java))
                R.id.nav_selflearn -> startActivity(Intent(this, SelfLearnActivity::class.java))
                R.id.nav_library -> startActivity(Intent(this, ResourceLibraryActivity::class.java))
                R.id.nav_questionbook -> startActivity(Intent(this, QuestionBookActivity::class.java))
                R.id.nav_schedule -> startActivity(Intent(this, ScheduleActivity::class.java))
                R.id.nav_bookdetail -> startActivity(Intent(this, BookDetailActivity::class.java))
            }
            true
        }

        rootView.post { refreshDesktop() }
        checkServer()
        maybeAutoCheckUpdate()

        if (intent.getBooleanExtra("open_drawer", false)) {
            drawerLayout.post {
                syncDrawerHeader()
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rootView.post { refreshDesktop() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rootView.post { refreshDesktop() }
    }

    private fun refreshDesktop() {
        UserProfileStore.applyDesktopBgTo(this, ivDesktopBg)
        syncDrawerHeader()
        layoutDeskItems()
    }

    private fun syncDrawerHeader() {
        val header = navView.getHeaderView(0) ?: return
        header.setBackgroundColor(UserProfileStore.getThemePrimaryColor(this))
        val ivAvatar = header.findViewById<ImageView>(R.id.iv_thumbnail)
        val tvName = header.findViewById<TextView>(R.id.textViewStudentName)
        val tvInfo = header.findViewById<TextView>(R.id.textViewServerInfo)
        UserProfileStore.applyAvatarTo(this, ivAvatar)
        val name = UserProfileStore.getUserName(this)
        val cls = UserProfileStore.getClassName(this)
        val sid = UserProfileStore.getStudentId(this)
        tvName.text = "$name（$cls）"
        tvInfo.text = "学号 $sid · ${Repository.getServerUrl(this).removePrefix("http://").removePrefix("https://")}"
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

    private fun checkServer() {
        Thread {
            val (tasks, online) = Repository.fetchDeliveriesSync(this)
            runOnUiThread {
                tvStatus.text = if (online) {
                    "已连接教师服务 · ${tasks.size} 项自主学习任务"
                } else {
                    "离线缓存模式 · ${tasks.size} 项本地任务"
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

            // Migrate legacy pixel coords if user dragged in an older version, otherwise use relative [0..1]
            val rx = if (prefs.contains("pos_rx_${spec.key}")) {
                prefs.getFloat("pos_rx_${spec.key}", spec.defaultRelX)
            } else if (prefs.contains("pos_x_${spec.key}")) {
                (prefs.getFloat("pos_x_${spec.key}", spec.defaultRelX * 1024f) / 1024f).coerceIn(0f, 0.92f)
            } else {
                spec.defaultRelX
            }

            val ry = if (prefs.contains("pos_ry_${spec.key}")) {
                prefs.getFloat("pos_ry_${spec.key}", spec.defaultRelY)
            } else if (prefs.contains("pos_y_${spec.key}")) {
                (prefs.getFloat("pos_y_${spec.key}", spec.defaultRelY * 768f) / 768f).coerceIn(0f, 0.92f)
            } else {
                spec.defaultRelY
            }

            val itemW = (spec.relW * canvasW).toInt().coerceAtLeast(minTapPx)
            val itemH = (spec.relH * canvasH).toInt().coerceAtLeast(minTapPx)
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
                            val clp = v.layoutParams as FrameLayout.LayoutParams
                            clp.leftMargin = (startLeft + dx).toInt().coerceIn(0, (canvasW.toInt() - clp.width).coerceAtLeast(0))
                            clp.topMargin = (startTop + dy).toInt().coerceIn(0, (canvasH.toInt() - clp.height).coerceAtLeast(0))
                            v.layoutParams = clp
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragging) {
                            val clp = v.layoutParams as FrameLayout.LayoutParams
                            prefs.edit()
                                .putFloat("pos_rx_${spec.key}", clp.leftMargin / canvasW)
                                .putFloat("pos_ry_${spec.key}", clp.topMargin / canvasH)
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
        val intent = when (key) {
            "study" -> Intent(this, SelfLearnActivity::class.java)
            "bookbag" -> Intent(this, ResourceLibraryActivity::class.java)
            "notebook" -> Intent(this, QuestionBookActivity::class.java)
            "schedule", "clock" -> Intent(this, ScheduleActivity::class.java)
            "rewards" -> Intent(this, BookDetailActivity::class.java)
            "light" -> Intent(this, SettingsActivity::class.java)
            else -> Intent(this, SelfLearnActivity::class.java)
        }
        startActivity(intent)
    }
}
