package com.shaonianpai.tablet.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shaonianpai.tablet.R

/**
 * Recreates the authentic centered "我的书包" popup dialog matching 24_我的书包.png:
 * - Centered floating white dialog over dimmed desktop (instead of separate full-screen activity)
 * - Diagonal blue/yellow geometric banner header (userinfo_background.jpg) + centered tilted folder art (ic_appsfolder)
 * - "我的书包" title top-left, white ✓ button top-right, "全部" tab with bottom white underline indicator
 * - Interactive grid of Shaonianpai learning modules + installed device apps
 */
object BookbagDialog {

    private data class BagApp(
        val label: String,
        val icon: Drawable?,
        val category: String, // "learn" or "device"
        val onLaunch: () -> Unit
    )

    fun show(activity: Activity) {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val dlg = Dialog(activity)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val dm = activity.resources.displayMetrics
        val dlgW = (dm.widthPixels * if (dm.widthPixels > dm.heightPixels) 0.48f else 0.88f)
            .toInt().coerceIn(dp(300), dp(560))
        val dlgH = (dm.heightPixels * 0.82f).toInt().coerceIn(dp(380), dp(620))

        val cardRoot = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat()
        }

        // Authentic geometric blue/yellow banner header (include_dialog_header_no_tab + Tab)
        val bannerBox = RelativeLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96))
        }
        bannerBox.addView(ImageView(activity).apply {
            setImageResource(R.drawable.userinfo_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })
        // Bottom tab tint band (#441565C0)
        val tabStrip = LinearLayout(activity).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#661A3E9E"))
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
        }
        bannerBox.addView(tabStrip)

        // Overhanging central folder+pens illustration (ic_appsfolder.png)
        bannerBox.addView(ImageView(activity).apply {
            setImageResource(R.drawable.ic_appsfolder)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = RelativeLayout.LayoutParams(dp(74), dp(74)).apply {
                addRule(RelativeLayout.CENTER_HORIZONTAL)
                topMargin = dp(8)
            }
        })

        // Top row: "我的书包" + white ✓ checkmark
        bannerBox.addView(TextView(activity).apply {
            text = "我的书包"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(14), dp(16), 0)
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
            }
        })
        bannerBox.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_done_white_24dp)
            background = null
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = RelativeLayout.LayoutParams(dp(48), dp(48)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
            }
            setOnClickListener { dlg.dismiss() }
        })

        // Tabs inside tabStrip: "全部" / "设备应用" (active tab shows crisp 2dp white bottom line matching 24_我的书包.png)
        var currentFilter = "all"
        lateinit var rv: RecyclerView
        var allApps = listOf<BagApp>()

        val tabAll = makeTabItem(activity, "全部", true)
        val tabDevice = makeTabItem(activity, "设备应用", false)
        tabStrip.addView(tabAll.first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        tabStrip.addView(tabDevice.first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        fun refreshGrid() {
            val filtered = when (currentFilter) {
                "device" -> allApps.filter { it.category == "device" }
                else -> allApps
            }
            rv.adapter = BagGridAdapter(activity, filtered)
        }

        tabAll.first.setOnClickListener {
            currentFilter = "all"
            tabAll.second.visibility = View.VISIBLE
            tabDevice.second.visibility = View.INVISIBLE
            refreshGrid()
        }
        tabDevice.first.setOnClickListener {
            currentFilter = "device"
            tabAll.second.visibility = View.INVISIBLE
            tabDevice.second.visibility = View.VISIBLE
            refreshGrid()
        }

        cardRoot.addView(bannerBox)

        val cols = if (dlgW >= dp(460)) 4 else 3
        rv = RecyclerView(activity).apply {
            layoutManager = GridLayoutManager(activity, cols)
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(14), dp(12), dp(14))
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        cardRoot.addView(rv)

        val builtIn = listOf(
            BagApp("自主学习", ContextCompat.getDrawable(activity, R.drawable.ic_ruiyicloud_selflearn), "learn") {
                dlg.dismiss()
                activity.startActivity(Intent(activity, SelfLearnActivity::class.java))
            },
            BagApp("我的资源库", ContextCompat.getDrawable(activity, R.drawable.ic_folder), "learn") {
                dlg.dismiss()
                activity.startActivity(Intent(activity, ResourceLibraryActivity::class.java))
            },
            BagApp("我的试题本", ContextCompat.getDrawable(activity, R.drawable.qb_btn_notebook), "learn") {
                dlg.dismiss()
                activity.startActivity(Intent(activity, QuestionBookActivity::class.java))
            },
            BagApp("草稿本", ContextCompat.getDrawable(activity, R.drawable.qb_btn_freewrite), "learn") {
                dlg.dismiss()
                activity.startActivity(Intent(activity, FingerDrawActivity::class.java))
            },
            BagApp("日历课程表", ContextCompat.getDrawable(activity, R.drawable.ic_desktop_schedule), "learn") {
                dlg.dismiss()
                activity.startActivity(Intent(activity, ScheduleActivity::class.java))
            },
            BagApp("电子教材", ContextCompat.getDrawable(activity, R.drawable.ic_pdf), "learn") {
                dlg.dismiss()
                activity.startActivity(Intent(activity, BookDetailActivity::class.java))
            },
            BagApp("设置", ContextCompat.getDrawable(activity, R.drawable.set_ic_ui), "learn") {
                dlg.dismiss()
                activity.startActivity(Intent(activity, SettingsActivity::class.java))
            }
        )
        allApps = builtIn
        refreshGrid()

        // Async load installed launcher apps on background thread
        Thread {
            val deviceApps = mutableListOf<BagApp>()
            runCatching {
                val pm = activity.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                @Suppress("DEPRECATION")
                val resolves = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                for (ri in resolves.sortedBy { it.loadLabel(pm).toString().lowercase() }) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == activity.packageName) continue
                    val label = ri.loadLabel(pm).toString()
                    val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
                    deviceApps.add(BagApp(label, icon, "device") {
                        dlg.dismiss()
                        val launch = pm.getLaunchIntentForPackage(pkg)
                        if (launch != null) activity.startActivity(launch)
                        else Toast.makeText(activity, "无法启动 $label", Toast.LENGTH_SHORT).show()
                    })
                }
            }
            if (deviceApps.isNotEmpty()) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && dlg.isShowing) {
                        allApps = builtIn + deviceApps
                        refreshGrid()
                    }
                }
            }
        }.start()

        dlg.setContentView(cardRoot)
        dlg.show()
        dlg.window?.setLayout(dlgW, dlgH)
    }

    private fun makeTabItem(activity: Activity, label: String, active: Boolean): Pair<FrameLayout, View> {
        val d = activity.resources.displayMetrics.density
        val box = FrameLayout(activity)
        val tv = TextView(activity).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val indicator = View(activity).apply {
            setBackgroundColor(Color.WHITE)
            visibility = if (active) View.VISIBLE else View.INVISIBLE
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (2.5f * d).toInt()).apply {
                gravity = Gravity.BOTTOM
            }
        }
        box.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        box.addView(indicator)
        return box to indicator
    }

    private class BagGridAdapter(
        private val activity: Activity,
        private val list: List<BagApp>
    ) : RecyclerView.Adapter<BagGridAdapter.VH>() {

        class VH(val root: LinearLayout, val iv: ImageView, val tv: TextView) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val d = activity.resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            val root = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), dp(10), dp(6), dp(10))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
                setTextColor(Color.parseColor("#333333"))
                setPadding(0, dp(6), 0, 0)
            }
            root.addView(iv)
            root.addView(tv)
            return VH(root, iv, tv)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.iv.setImageDrawable(item.icon ?: ContextCompat.getDrawable(activity, R.drawable.ic_appsfolder))
            holder.tv.text = item.label
            holder.root.setOnClickListener { item.onLaunch() }
        }
    }
}
