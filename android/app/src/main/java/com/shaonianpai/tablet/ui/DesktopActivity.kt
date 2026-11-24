package com.shaonianpai.tablet.ui

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.Repository
import kotlin.math.abs
import kotlin.math.min

/**
 * Authentic Shaonianpai Desk Desktop (1024x768 mdpi base coordinate system).
 * Exact coordinates from APK res/values/dimens.xml & v2 implementation guide:
 * - rewards:  (180, 250) 103x138
 * - clock:    (270, 290)  62x103
 * - schedule: (350, 300) 101x72
 * - notebook: (460, 320) 148x65
 * - study:    (610, 330)  95x46  -> center (657, 353) -> opens SelfLearn ("自主学习")
 * - light:    (680, 170) 131x208
 * - bookbag:  (780, 260) 132x122
 */
class DesktopActivity : AppCompatActivity() {

    private data class DeskItemSpec(
        val key: String,
        val label: String,
        val drawableRes: Int,
        val defaultX: Float,
        val defaultY: Float,
        val baseW: Float,
        val baseH: Float
    )

    private val items = listOf(
        DeskItemSpec("rewards", "电子教材", R.drawable.ic_desktop_rewards, 180f, 250f, 103f, 138f),
        DeskItemSpec("clock", "课程闹钟", R.drawable.ic_desktop_clock, 270f, 290f, 62f, 103f),
        DeskItemSpec("schedule", "日历", R.drawable.ic_desktop_schedule, 350f, 300f, 101f, 72f),
        DeskItemSpec("notebook", "试题本", R.drawable.ic_desktop_notebook, 460f, 320f, 148f, 65f),
        DeskItemSpec("study", "自主学习", R.drawable.ic_desktop_study, 610f, 330f, 95f, 46f),
        DeskItemSpec("light", "系统设置", R.drawable.ic_desktop_light, 680f, 170f, 131f, 208f),
        DeskItemSpec("bookbag", "我的资源库", R.drawable.ic_desktop_bookbag, 780f, 260f, 132f, 122f)
    )

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var deskContainer: FrameLayout
    private lateinit var deskCanvas: FrameLayout
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desktop)

        drawerLayout = findViewById(R.id.drawerLayout)
        deskContainer = findViewById(R.id.MainDesktopView)
        deskCanvas = findViewById(R.id.deskCanvas)
        tvStatus = findViewById(R.id.textViewIMState)

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

        deskCanvas.post { layoutDeskItems() }
        checkServer()

        if (intent.getBooleanExtra("open_drawer", false)) {
            drawerLayout.post { drawerLayout.openDrawer(GravityCompat.START) }
        }
    }

    override fun onResume() {
        super.onResume()
        deskCanvas.post { layoutDeskItems() }
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
        val parentW = findViewById<android.view.View>(R.id.MainDesktopView2).width.toFloat()
        val parentH = findViewById<android.view.View>(R.id.MainDesktopView2).height.toFloat()
        if (parentW <= 0f || parentH <= 0f) return

        val scale = min(parentW / 1024f, parentH / 768f)
        val canvasW = (1024f * scale).toInt()
        val canvasH = (768f * scale).toInt()
        val offsetX = ((parentW - canvasW) / 2f).toInt()
        val offsetY = ((parentH - canvasH) / 2f).toInt()

        val lp = deskCanvas.layoutParams as android.widget.RelativeLayout.LayoutParams
        lp.width = canvasW
        lp.height = canvasH
        lp.leftMargin = offsetX
        lp.topMargin = offsetY
        deskCanvas.layoutParams = lp

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
                        if (!locked && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
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
