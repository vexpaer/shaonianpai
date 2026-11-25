package com.shaonianpai.tablet.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.UserProfileStore

/**
 * Recreates the original Bookbag -> All Apps launcher grid (dialog_allapps.xml):
 * Displays quick entries for Shaonianpai learning modules plus all launcher apps installed on the device.
 */
class AppsLauncherActivity : AppCompatActivity() {

    private data class AppEntry(
        val label: String,
        val subtitle: String,
        val icon: Drawable?,
        val onLaunch: () -> Unit
    )

    private lateinit var rv: RecyclerView
    private var allEntries: List<AppEntry> = emptyList()
    private var queryText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }

        val toolbar = Toolbar(this).apply {
            setBackgroundColor(UserProfileStore.getThemePrimaryColor(this@AppsLauncherActivity))
            setTitleTextColor(Color.WHITE)
            title = "软件与应用入口"
            setNavigationIcon(R.drawable.ic_arrow_back_white_24dp)
            setNavigationOnClickListener { finish() }
            elevation = dp(4).toFloat()
        }
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        // Search filter bar
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val etSearch = EditText(this).apply {
            hint = "搜索学习应用或已安装软件..."
            textSize = 14f
            isSingleLine = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F1F4F8"))
                cornerRadius = dp(18).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    queryText = s?.toString()?.trim().orEmpty()
                    renderGrid()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        searchBox.addView(etSearch)
        root.addView(searchBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val cols = when {
            resources.configuration.screenWidthDp >= 820 -> 7
            resources.configuration.screenWidthDp >= 600 -> 5
            else -> 4
        }
        rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@AppsLauncherActivity, cols)
            setPadding(dp(10), dp(12), dp(10), dp(16))
            clipToPadding = false
        }
        root.addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        loadApps()
    }

    private fun loadApps() {
        val builtIn = listOf(
            AppEntry("自主学习", "课程任务", ContextCompat.getDrawable(this, R.drawable.ic_ruiyicloud_selflearn)) {
                startActivity(Intent(this, SelfLearnActivity::class.java))
            },
            AppEntry("我的资源库", "学习资料", ContextCompat.getDrawable(this, R.drawable.ic_folder)) {
                startActivity(Intent(this, ResourceLibraryActivity::class.java))
            },
            AppEntry("收藏夹 / 试题本", "错题收藏", ContextCompat.getDrawable(this, R.drawable.qb_btn_notebook)) {
                startActivity(Intent(this, QuestionBookActivity::class.java))
            },
            AppEntry("草稿本", "手写演算", ContextCompat.getDrawable(this, R.drawable.qb_btn_freewrite)) {
                startActivity(Intent(this, FingerDrawActivity::class.java))
            },
            AppEntry("日历课程表", "每周课表", ContextCompat.getDrawable(this, R.drawable.ic_desktop_schedule)) {
                startActivity(Intent(this, ScheduleActivity::class.java))
            },
            AppEntry("电子教材", "人教数字教材", ContextCompat.getDrawable(this, R.drawable.ic_pdf)) {
                startActivity(Intent(this, BookDetailActivity::class.java))
            },
            AppEntry("系统设置", "偏好与壁纸", ContextCompat.getDrawable(this, R.drawable.set_ic_ui)) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        )

        allEntries = builtIn
        renderGrid()

        Thread {
            val installed = mutableListOf<AppEntry>()
            try {
                val pm = packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                @Suppress("DEPRECATION")
                val resolves = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                for (ri in resolves.sortedBy { it.loadLabel(pm).toString().lowercase() }) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == packageName) continue
                    val label = ri.loadLabel(pm).toString()
                    val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
                    installed.add(
                        AppEntry(label, "设备应用", icon) {
                            val launch = pm.getLaunchIntentForPackage(pkg)
                            if (launch != null) {
                                startActivity(launch)
                            } else {
                                Toast.makeText(this, "无法启动 $label", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            } catch (_: Exception) {
            }
            if (installed.isNotEmpty()) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        allEntries = builtIn + installed
                        renderGrid()
                    }
                }
            }
        }.start()
    }

    private fun renderGrid() {
        val filtered = if (queryText.isEmpty()) allEntries
        else allEntries.filter {
            it.label.contains(queryText, ignoreCase = true) ||
                it.subtitle.contains(queryText, ignoreCase = true)
        }
        rv.adapter = AppAdapter(filtered)
    }

    private inner class AppAdapter(private val list: List<AppEntry>) :
        RecyclerView.Adapter<AppAdapter.VH>() {

        inner class VH(val root: LinearLayout, val iv: ImageView, val tv: TextView) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val d = resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            val box = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), dp(12), dp(6), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#EAEEF3"))
                }
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) }
            }
            val iv = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            }
            val tv = TextView(parent.context).apply {
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(Color.parseColor("#212121"))
                setPadding(0, dp(6), 0, 0)
            }
            box.addView(iv)
            box.addView(tv)
            return VH(box, iv, tv)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.iv.setImageDrawable(item.icon ?: ContextCompat.getDrawable(this@AppsLauncherActivity, R.drawable.ic_appsfolder))
            holder.tv.text = item.label
            holder.root.setOnClickListener { item.onLaunch() }
        }
    }
}
