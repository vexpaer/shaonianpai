package com.shaonianpai.tablet.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.shaonianpai.tablet.R
import kotlin.math.abs

/**
 * Recreates original MyQuestionBookService + layout_questionbookbuttons.xml matching 02/05/18 screenshots:
 * - Collapsed: single white rounded-square button with thin blue border & red notebook + arrows (qb_btn_expand) docked at right edge.
 * - Expanded: expands into the 6 icon-only white/blue-bordered buttons horizontally (with NO text captions):
 *   1) 收起 (qb_btn_expand)  2) 收藏夹/试题本 (qb_btn_notebook)  3) 拍照 (qb_btn_imagecapture)
 *   4) 课堂投屏 (qb_btn_projector)  5) 截屏 (qb_btn_screencapture)  6) 草稿本/速算板 (qb_btn_freewrite)
 * - Rotation-safe OnLayoutChangeListener + live width/height drag clamping.
 */
object FloatingQuickBar {

    private const val OVERLAY_TAG = "shaonianpai_floating_quick_bar"
    private const val PREF_NAME = "shaonianpai_prefs"
    const val KEY_ENABLED = "FloatingQuickBarEnabled"
    private const val KEY_POS_X = "fab_pos_x_norm_v2"
    private const val KEY_POS_Y = "fab_pos_y_norm_v2"

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun attachIfNeeded(activity: Activity) {
        if (activity is LoginActivity ||
            activity is PhotoCaptureProxyActivity ||
            activity is VideoPlayerActivity
        ) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val existing = content.findViewWithTag<View>(OVERLAY_TAG)
        if (!isEnabled(activity)) {
            if (existing != null) content.removeView(existing)
            return
        }
        if (existing != null) {
            existing.bringToFront()
            repositionFromNorm(activity, content, existing)
            return
        }

        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val barContainer = LinearLayout(activity).apply {
            tag = OVERLAY_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(8).toFloat()
            clipChildren = false
            clipToPadding = false
        }

        val expandStrip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }

        var expanded = false
        fun setExpanded(open: Boolean) {
            expanded = open
            expandStrip.visibility = if (open) View.VISIBLE else View.GONE
            barContainer.post {
                repositionFromNorm(activity, content, barContainer)
            }
        }

        fun collapse() = setExpanded(false)

        // Authentic 5 additional icon-only buttons beside main button (total 6 in row matching 05_悬浮球展开.png):
        val btnNotebook = makeIconOnlyButton(activity, R.drawable.qb_btn_notebook, "收藏夹 / 试题本") {
            collapse()
            if (activity !is QuestionBookActivity) {
                activity.startActivity(Intent(activity, QuestionBookActivity::class.java).apply {
                    putExtra("initial_tab", "favorites")
                })
            } else {
                Toast.makeText(activity, "当前已在我的试题本", Toast.LENGTH_SHORT).show()
            }
        }
        val btnCamera = makeIconOnlyButton(activity, R.drawable.qb_btn_imagecapture, "拍照收藏") {
            collapse()
            activity.startActivity(Intent(activity, PhotoCaptureProxyActivity::class.java))
        }
        val btnProjector = makeIconOnlyButton(activity, R.drawable.qb_btn_projector, "无线投屏") {
            collapse()
            AlertDialog.Builder(activity)
                .setTitle("课堂无线投屏")
                .setMessage("当前处于离线演示模式。\n连接教室局域网教师端后，点击此处可将平板画面实时推送至教室大屏，或接收教师板书投影。")
                .setPositiveButton("打开白板演算") { _, _ ->
                    if (activity !is FingerDrawActivity) {
                        activity.startActivity(Intent(activity, FingerDrawActivity::class.java))
                    }
                }
                .setNegativeButton("关闭", null)
                .show()
        }
        val btnScreenshot = makeIconOnlyButton(activity, R.drawable.qb_btn_screencapture, "截屏收藏") {
            collapse()
            captureCurrentScreen(activity, barContainer)
        }
        val btnScratchpad = makeIconOnlyButton(activity, R.drawable.qb_btn_freewrite, "草稿本 / 自由批注") {
            collapse()
            if (activity !is FingerDrawActivity) {
                activity.startActivity(Intent(activity, FingerDrawActivity::class.java))
            } else {
                Toast.makeText(activity, "当前已在草稿本", Toast.LENGTH_SHORT).show()
            }
        }

        expandStrip.addView(btnNotebook)
        expandStrip.addView(btnCamera)
        expandStrip.addView(btnProjector)
        expandStrip.addView(btnScreenshot)
        expandStrip.addView(btnScratchpad)

        // Main draggable toggle button (qb_btn_expand matching 02/05/18 screenshots)
        val btnMain = makeIconOnlyButton(activity, R.drawable.qb_btn_expand, "快捷工具", isMain = true) {}

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var moved = false

        btnMain.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = barContainer.x
                    startY = barContainer.y
                    moved = false
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        val liveW = content.width.coerceAtLeast(1)
                        val liveH = content.height.coerceAtLeast(1)
                        val maxX = (liveW - barContainer.width - dp(6)).coerceAtLeast(dp(6)).toFloat()
                        val maxY = (liveH - barContainer.height - dp(8)).coerceAtLeast(dp(48)).toFloat()
                        barContainer.x = (startX + dx).coerceIn(dp(6).toFloat(), maxX)
                        barContainer.y = (startY + dy).coerceIn(dp(48).toFloat(), maxY)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (!moved) {
                        setExpanded(!expanded)
                    } else {
                        val liveW = content.width
                        val liveH = content.height
                        if (liveW > 0 && liveH > 0) {
                            activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                                .putFloat(KEY_POS_X, (barContainer.x / liveW).coerceIn(0f, 0.98f))
                                .putFloat(KEY_POS_Y, (barContainer.y / liveH).coerceIn(0f, 0.95f))
                                .apply()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        // Order left-to-right: main toggle button + 5 expanded tool buttons (= 6 buttons total!)
        barContainer.addView(btnMain)
        barContainer.addView(expandStrip)

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        content.addView(barContainer, lp)

        var lastW = -1
        var lastH = -1
        content.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val cw = r - l
            val ch = b - t
            if (cw > 0 && ch > 0 && (cw != lastW || ch != lastH)) {
                lastW = cw
                lastH = ch
                barContainer.post { repositionFromNorm(activity, content, barContainer) }
            }
        }

        barContainer.post {
            repositionFromNorm(activity, content, barContainer)
            if (activity.intent.getBooleanExtra("demo_expand_fab", false)) {
                setExpanded(true)
            }
        }
    }

    private fun repositionFromNorm(activity: Activity, content: ViewGroup, bar: View) {
        val cw = content.width
        val ch = content.height
        if (cw <= 0 || ch <= 0) return
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // Default docked on right-middle edge (02_主桌面.png / 10_草稿本.png / 20_自主学习.png)
        val normX = prefs.getFloat(KEY_POS_X, 0.94f).coerceIn(0f, 0.98f)
        val normY = prefs.getFloat(KEY_POS_Y, 0.56f).coerceIn(0f, 0.95f)
        val bw = bar.width.coerceAtLeast(dp(46))
        val bh = bar.height.coerceAtLeast(dp(46))
        val maxX = (cw - bw - dp(8)).coerceAtLeast(dp(8)).toFloat()
        val maxY = (ch - bh - dp(10)).coerceAtLeast(dp(56)).toFloat()
        bar.x = (normX * cw).coerceIn(dp(8).toFloat(), maxX)
        bar.y = (normY * ch).coerceIn(dp(56).toFloat(), maxY)
    }

    private fun makeIconOnlyButton(
        activity: Activity,
        iconRes: Int,
        desc: String,
        isMain: Boolean = false,
        onClick: () -> Unit
    ): View {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val btnSize = dp(46)

        return ImageView(activity).apply {
            setImageResource(iconRes)
            contentDescription = desc
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundResource(R.drawable.bg_screen_questionbook)
            setPadding(dp(7), dp(7), dp(7), dp(7))
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply {
                marginEnd = dp(5)
            }
            if (!isMain) setOnClickListener { onClick() }
        }
    }

    private fun captureCurrentScreen(activity: Activity, overlay: View) {
        val root = activity.window?.decorView?.rootView ?: return
        overlay.visibility = View.INVISIBLE
        root.post {
            val bmp = try {
                val w = root.width.coerceAtLeast(1)
                val h = root.height.coerceAtLeast(1)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    root.draw(Canvas(it))
                }
            } catch (_: Exception) {
                null
            } finally {
                overlay.visibility = View.VISIBLE
            }
            if (bmp == null) {
                Toast.makeText(activity, "当前页面无法截屏", Toast.LENGTH_SHORT).show()
                return@post
            }
            AddToQuestionBookDialog.show(
                activity = activity,
                sourceType = "SCREENSHOT",
                defaultTitle = "屏幕截图",
                defaultBook = "我的收藏夹",
                defaultTag = "截屏",
                bitmap = bmp
            )
        }
    }
}
