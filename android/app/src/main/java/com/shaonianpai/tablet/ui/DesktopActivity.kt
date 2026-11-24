package com.shaonianpai.tablet.ui

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.Repository
import com.shaonianpai.tablet.data.UpdateCheckResult
import com.shaonianpai.tablet.data.UpdateManager
import kotlin.math.abs
import kotlin.math.min

/**
 * Authentic Shaonianpai Desk Desktop:
 * - Landscape tablet: exact 1024x768 coordinate system with 7 draggable objects.
 * - Portrait phone: top full-width interactive desk + 2-column quick-launch cards below.
 * - Startup auto-update check against GitHub Releases (when enabled in Settings).
 */
class DesktopActivity : AppCompatActivity() {

    private data class DeskItemSpec(
        val key: String,
        val label: String,
        val subtitle: String,
        val drawableRes: Int,
        val quickIconRes: Int,
        val defaultX: Float,
        val defaultY: Float,
        val baseW: Float,
        val baseH: Float
    )

    private val items = listOf(
        DeskItemSpec("rewards", "人教数字教材", "电子课本与配套资源", R.drawable.ic_desktop_rewards, R.drawable.ic_pdf, 180f, 250f, 103f, 138f),
        DeskItemSpec("clock", "课程闹钟", "今日课程提醒", R.drawable.ic_desktop_clock, R.drawable.ic_desktop_clock, 270f, 290f, 62f, 103f),
        DeskItemSpec("schedule", "日历 / 课程表", "周课表与课节资料", R.drawable.ic_desktop_schedule, R.drawable.ic_desktop_schedule, 350f, 300f, 101f, 72f),
        DeskItemSpec("notebook", "试题本", "错题整理与解析", R.drawable.ic_desktop_notebook, R.drawable.ic_ruiyicloud_homework, 460f, 320f, 148f, 65f),
        DeskItemSpec("study", "自主学习", "老师推送的资料与任务", R.drawable.ic_desktop_study, R.drawable.ic_ruiyicloud_selflearn, 610f, 330f, 95f, 46f),
        DeskItemSpec("light", "系统设置", "更新检查与服务器", R.drawable.ic_desktop_light, R.drawable.ic_configure, 680f, 170f, 131f, 208f),
        DeskItemSpec("bookbag", "我的资源库", "离线文档与音视频", R.drawable.ic_desktop_bookbag, R.drawable.ic_folder, 780f, 260f, 132f, 122f)
    )

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rootView: RelativeLayout
    private lateinit var deskContainer: FrameLayout
    private lateinit var deskCanvas: FrameLayout
    private lateinit var portraitQuickPanel: ScrollView
    private lateinit var portraitGridContainer: LinearLayout
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
        deskContainer = findViewById(R.id.MainDesktopView)
        deskCanvas = findViewById(R.id.deskCanvas)
        portraitQuickPanel = findViewById(R.id.portraitQuickPanel)
        portraitGridContainer = findViewById(R.id.portraitGridContainer)
        tvStatus = findViewById(R.id.textViewIMState)
        tvVersion = findViewById(R.id.textViewVersion)

        tvVersion.text = "v${UpdateManager.getCurrentVersionName(this)}"

        findViewById<ImageView>(R.id.imageButtonDrawer).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val navView = findViewById<NavigationView>(R.id.navigationView)
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

        rootView.post { layoutDeskItems() }
        checkServer()
        maybeAutoCheckUpdate()

        if (intent.getBooleanExtra("open_drawer", false)) {
            drawerLayout.post { drawerLayout.openDrawer(GravityCompat.START) }
        }
    }

    override fun onResume() {
        super.onResume()
        rootView.post { layoutDeskItems() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rootView.post { layoutDeskItems() }
    }

    private fun maybeAutoCheckUpdate() {
        if (startupUpdateChecked || !UpdateManager.isAutoCheckEnabled(this)) return
        startupUpdateChecked = true
        Thread {
            val result = UpdateManager.checkLatestReleaseSync(this)
            if (result is UpdateCheckResult.UpdateAvailable) {
                runOnUiThread {
                    UpdateUiHelper.showUpdateDialog(this, result.currentVersion, result.release)
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
        val parentW = rootView.width.toFloat()
        val parentH = rootView.height.toFloat()
        if (parentW <= 0f || parentH <= 0f) return

        val isPortrait = parentH > parentW
        val scale = if (isPortrait) {
            parentW / 1024f
        } else {
            min(parentW / 1024f, parentH / 768f)
        }
        val canvasW = (1024f * scale).toInt()
        val canvasH = (768f * scale).toInt()
        val offsetX = ((parentW - canvasW) / 2f).toInt()
        val offsetY = if (isPortrait) 0 else ((parentH - canvasH) / 2f).toInt()

        rootView.setBackgroundColor(if (isPortrait) Color.parseColor("#F4F6F9") else Color.BLACK)
        val lp = deskCanvas.layoutParams as RelativeLayout.LayoutParams
        lp.width = canvasW
        lp.height = canvasH
        lp.leftMargin = offsetX
        lp.topMargin = offsetY
        deskCanvas.layoutParams = lp

        // Status & version text colors adapt to light portrait background vs dark desk edge
        val statusColor = if (isPortrait) Color.parseColor("#546E7A") else Color.parseColor("#EEFFFFFF")
        tvStatus.setTextColor(statusColor)
        tvVersion.setTextColor(statusColor)

        deskContainer.removeAllViews()
        val prefs = getSharedPreferences("shaonianpai_prefs", MODE_PRIVATE)
        val locked = prefs.getBoolean("LockIcons", false)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        for (spec in items) {
            val bx = prefs.getFloat("pos_x_${spec.key}", spec.defaultX)
            val by = prefs.getFloat("pos_y_${spec.key}", spec.defaultY)
            val iv = ImageView(this).apply {
                setImageResource(spec.drawableRes)
                scaleType = ImageView.ScaleType.FIT_XY
                contentDescription = spec.label
            }
            val ilp = FrameLayout.LayoutParams(
                (spec.baseW * scale).toInt(),
                (spec.baseH * scale).toInt()
            ).apply {
                leftMargin = (bx * scale).toInt()
                topMargin = (by * scale).toInt()
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
                        if (!locked && !isPortrait && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            dragging = true
                        }
                        if (dragging) {
                            val clp = v.layoutParams as FrameLayout.LayoutParams
                            clp.leftMargin = (startLeft + dx).toInt().coerceIn(0, canvasW - clp.width)
                            clp.topMargin = (startTop + dy).toInt().coerceIn(0, canvasH - clp.height)
                            v.layoutParams = clp
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragging) {
                            val clp = v.layoutParams as FrameLayout.LayoutParams
                            prefs.edit()
                                .putFloat("pos_x_${spec.key}", clp.leftMargin / scale)
                                .putFloat("pos_y_${spec.key}", clp.topMargin / scale)
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

        if (isPortrait) {
            portraitQuickPanel.visibility = View.VISIBLE
            buildPortraitQuickGrid()
        } else {
            portraitQuickPanel.visibility = View.GONE
        }
    }

    private fun buildPortraitQuickGrid() {
        portraitGridContainer.removeAllViews()
        val d = resources.displayMetrics.density

        portraitGridContainer.addView(TextView(this).apply {
            text = "学习功能快捷入口"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#37474F"))
            setPadding((4 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), (10 * d).toInt())
        })

        val quickItems = listOf(
            items.first { it.key == "study" },
            items.first { it.key == "bookbag" },
            items.first { it.key == "notebook" },
            items.first { it.key == "schedule" },
            items.first { it.key == "rewards" },
            items.first { it.key == "light" }
        )

        for (i in quickItems.indices step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * d).toInt() }
            }
            for (j in 0..1) {
                if (i + j >= quickItems.size) break
                val spec = quickItems[i + j]
                val card = CardView(this).apply {
                    radius = 10 * d
                    cardElevation = 2 * d
                    setCardBackgroundColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (j == 0) marginEnd = (6 * d).toInt() else marginStart = (6 * d).toInt()
                    }
                    setOnClickListener { openDeskItem(spec.key) }
                }
                val inner = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt())
                }
                val icon = ImageView(this).apply {
                    setImageResource(spec.quickIconRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt())
                }
                val textCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = (10 * d).toInt()
                    }
                }
                textCol.addView(TextView(this).apply {
                    text = spec.label
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#212121"))
                })
                textCol.addView(TextView(this).apply {
                    text = spec.subtitle
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#607D8B"))
                    maxLines = 1
                })
                inner.addView(icon)
                inner.addView(textCol)
                card.addView(inner)
                row.addView(card)
            }
            portraitGridContainer.addView(row)
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
