package com.shaonianpai.tablet.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.shaonianpai.tablet.R
import kotlin.math.abs

/**
 * Recreates original MyQuestionBookService + layout_questionbookbuttons.xml:
 * Global draggable floating button (#DDFFFFFF + 2dp #0066FF blue border) that expands into
 * 5 quick tools: 收藏夹/试题本, 截屏收藏, 拍照收藏, 草稿本, 速记.
 */
object FloatingQuickBar {

    private const val OVERLAY_TAG = "shaonianpai_floating_quick_bar"
    private const val PREF_NAME = "shaonianpai_prefs"
    const val KEY_ENABLED = "FloatingQuickBarEnabled"
    private const val KEY_POS_X = "fab_pos_x_norm"
    private const val KEY_POS_Y = "fab_pos_y_norm"

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun attachIfNeeded(activity: Activity) {
        if (activity is LoginActivity ||
            activity is FingerDrawActivity ||
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

        fun collapse() {
            expandStrip.visibility = View.GONE
        }

        // Build the 5 authentic expanded action buttons (matching layout_questionbookbuttons.xml)
        val btnNotebook = makeQbButton(activity, R.drawable.qb_btn_notebook, "收藏夹") {
            collapse()
            if (activity !is QuestionBookActivity) {
                activity.startActivity(Intent(activity, QuestionBookActivity::class.java).apply {
                    putExtra("initial_tab", "favorites")
                })
            } else {
                Toast.makeText(activity, "当前已在收藏夹 / 试题本", Toast.LENGTH_SHORT).show()
            }
        }
        val btnScreenshot = makeQbButton(activity, R.drawable.qb_btn_screencapture, "截屏") {
            collapse()
            captureCurrentScreen(activity, barContainer)
        }
        val btnCamera = makeQbButton(activity, R.drawable.qb_btn_imagecapture, "拍照") {
            collapse()
            activity.startActivity(Intent(activity, PhotoCaptureProxyActivity::class.java))
        }
        val btnScratchpad = makeQbButton(activity, R.drawable.qb_btn_freewrite, "草稿本") {
            collapse()
            activity.startActivity(Intent(activity, FingerDrawActivity::class.java))
        }
        val btnQuickNote = makeQbButton(activity, R.drawable.qb_btn_plus, "速记") {
            collapse()
            AddToQuestionBookDialog.show(
                activity = activity,
                sourceType = "NOTE",
                defaultTitle = "",
                defaultBook = "我的收藏夹",
                defaultTag = "重点"
            )
        }

        expandStrip.addView(btnNotebook)
        expandStrip.addView(btnScreenshot)
        expandStrip.addView(btnCamera)
        expandStrip.addView(btnScratchpad)
        expandStrip.addView(btnQuickNote)

        // Main draggable expand/collapse button (imageButtonNoteBook / ic_notebook_expand)
        val btnMain = makeQbButton(activity, R.drawable.qb_btn_expand, "工具", isMain = true) {}

        var expanded = false
        fun setExpanded(open: Boolean) {
            expanded = open
            expandStrip.visibility = if (open) View.VISIBLE else View.GONE
            barContainer.post {
                val parentW = content.width.coerceAtLeast(1)
                val maxX = (parentW - barContainer.width - dp(6)).coerceAtLeast(dp(6)).toFloat()
                if (barContainer.x > maxX) barContainer.x = maxX
            }
        }

        // Smooth drag + tap toggle on main button
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
                        val maxX = (content.width - barContainer.width - dp(4)).coerceAtLeast(dp(4)).toFloat()
                        val maxY = (content.height - barContainer.height - dp(4)).coerceAtLeast(dp(4)).toFloat()
                        barContainer.x = (startX + dx).coerceIn(dp(4).toFloat(), maxX)
                        barContainer.y = (startY + dy).coerceIn(dp(48).toFloat(), maxY)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (!moved) {
                        setExpanded(!expanded)
                    } else if (content.width > 0 && content.height > 0) {
                        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                            .putFloat(KEY_POS_X, barContainer.x / content.width)
                            .putFloat(KEY_POS_Y, barContainer.y / content.height)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }

        barContainer.addView(btnMain)
        barContainer.addView(expandStrip)

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        content.addView(barContainer, lp)

        // Restore persisted position (default: left-bottom-ish edge like original tablet)
        barContainer.post {
            val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val normX = prefs.getFloat(KEY_POS_X, 0.015f)
            val normY = prefs.getFloat(KEY_POS_Y, 0.78f)
            val maxX = (content.width - barContainer.width - dp(6)).coerceAtLeast(dp(6)).toFloat()
            val maxY = (content.height - barContainer.height - dp(10)).coerceAtLeast(dp(56)).toFloat()
            barContainer.x = (normX * content.width).coerceIn(dp(6).toFloat(), maxX)
            barContainer.y = (normY * content.height).coerceIn(dp(56).toFloat(), maxY)

            if (activity.intent.getBooleanExtra("demo_expand_fab", false)) {
                barContainer.x = dp(10).toFloat()
                barContainer.y = (content.height - dp(128)).coerceAtLeast(dp(72)).toFloat()
                setExpanded(true)
            }
        }
    }

    private fun makeQbButton(
        activity: Activity,
        iconRes: Int,
        label: String,
        isMain: Boolean = false,
        onClick: () -> Unit
    ): View {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val narrow = activity.resources.configuration.screenWidthDp < 440
        val btnW = if (narrow) dp(50) else dp(56)
        val iconSz = if (narrow) dp(24) else dp(28)

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_screen_questionbook)
            setPadding(dp(4), dp(5), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(btnW, btnW).apply {
                marginEnd = dp(5)
            }
            addView(ImageView(activity).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(iconSz, iconSz)
            })
            addView(TextView(activity).apply {
                text = label
                textSize = if (narrow) 9f else 10f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextColor(if (isMain) Color.parseColor("#0066FF") else Color.parseColor("#333333"))
            })
            if (!isMain) setOnClickListener { onClick() }
        }
    }

    private fun captureCurrentScreen(activity: Activity, overlay: View) {
        val root = activity.window?.decorView?.rootView ?: return
        overlay.visibility = View.INVISIBLE
        root.post {
            val bmp = try {
                val b = Bitmap.createBitmap(root.width.coerceAtLeast(1), root.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                root.draw(c)
                b
            } catch (_: Exception) {
                null
            } finally {
                overlay.visibility = View.VISIBLE
            }
            if (bmp == null) {
                Toast.makeText(activity, "截屏失败", Toast.LENGTH_SHORT).show()
                return@post
            }
            AddToQuestionBookDialog.show(
                activity = activity,
                sourceType = "SCREENSHOT",
                defaultTitle = "屏幕截图（${activity.title ?: "学习页面"}）",
                defaultBook = "我的收藏夹",
                defaultTag = "截屏",
                bitmap = bmp
            )
        }
    }
}
