package com.shaonianpai.tablet.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.Repository
import com.shaonianpai.tablet.data.UpdateCheckResult
import com.shaonianpai.tablet.data.UpdateManager
import com.shaonianpai.tablet.data.UserProfileStore
import java.io.File

/**
 * Authentic 1-Level + 2-Level Settings matching original settings_headers.xml:
 * - Tablet landscape (>= 680dp): Classic 2-pane Master-Detail view.
 * - Portrait phone (< 680dp): 1-level header list -> click enters 2-level detail page with back navigation.
 * Includes User Profile (avatar / username / studentId / class), Desktop Background (custom gallery / presets),
 * Desk Icons (lock / ShowIcons / reset relative positions), Floating Bar toggle, Pen widths, Cache management,
 * Teacher Server, GitHub Auto-Update, and Android System Settings shortcut.
 */
class SettingsActivity : AppCompatActivity() {

    private data class SettingsHeader(
        val key: String,
        val iconRes: Int,
        val title: String,
        val summary: String
    )

    private val headers = listOf(
        SettingsHeader("user", R.drawable.set_ic_user, "基本信息", "用户头像、用户名、班级与登录设置"),
        SettingsHeader("ui", R.drawable.set_ic_ui, "界面与背景设置", "桌面背景图片、图标位置、悬浮条与主题"),
        SettingsHeader("draw", R.drawable.set_ic_draw, "手写笔设置", "设置细笔、粗笔粗细与默认批注习惯"),
        SettingsHeader("cache", R.drawable.set_ic_cache, "缓存设置", "查看分项缓存大小与清理临时缓存"),
        SettingsHeader("misc", R.drawable.set_ic_misc, "其它与服务器", "教师资料服务地址、夜间休眠与杂项"),
        SettingsHeader("about", R.drawable.set_ic_about, "关于与更新", "当前版本、GitHub 自动检查与更新日志"),
        SettingsHeader("system", R.drawable.set_ic_sys, "系统设置", "进入 Android 系统设置")
    )

    private lateinit var toolbar: Toolbar
    private lateinit var svLevel1: ScrollView
    private lateinit var llLevel1: LinearLayout
    private lateinit var vDivider: View
    private lateinit var svLevel2: ScrollView
    private lateinit var llLevel2: LinearLayout

    private var isTwoPane = true
    private var inPhoneLevel2 = false
    private var activeKey = "user"

    companion object {
        private const val REQ_PICK_AVATAR = 401
        private const val REQ_PICK_BG = 402
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setBackgroundColor(UserProfileStore.getThemePrimaryColor(this))
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp)
        toolbar.setNavigationOnClickListener { handleBackAction() }

        svLevel1 = findViewById(R.id.svLevel1Headers)
        llLevel1 = findViewById(R.id.llLevel1Headers)
        vDivider = findViewById(R.id.vSettingsDivider)
        svLevel2 = findViewById(R.id.svLevel2Detail)
        llLevel2 = findViewById(R.id.llLevel2Content)

        isTwoPane = resources.configuration.screenWidthDp >= 680
        val reqSection = intent.getStringExtra("demo_section")
        if (!reqSection.isNullOrBlank() && headers.any { it.key == reqSection }) {
            activeKey = reqSection
            if (!isTwoPane) inPhoneLevel2 = true
        }

        applyPaneVisibility()
        renderLevel1Headers()
        renderLevel2Detail(activeKey)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackAction()
    }

    private fun handleBackAction() {
        if (!isTwoPane && inPhoneLevel2) {
            inPhoneLevel2 = false
            applyPaneVisibility()
            renderLevel1Headers()
        } else {
            finish()
        }
    }

    private fun applyPaneVisibility() {
        if (isTwoPane) {
            svLevel1.visibility = View.VISIBLE
            svLevel1.layoutParams = LinearLayout.LayoutParams(dp(296), ViewGroup.LayoutParams.MATCH_PARENT)
            vDivider.visibility = View.VISIBLE
            svLevel2.visibility = View.VISIBLE
            supportActionBar?.title = "设置"
        } else {
            vDivider.visibility = View.GONE
            if (inPhoneLevel2) {
                svLevel1.visibility = View.GONE
                svLevel2.visibility = View.VISIBLE
                supportActionBar?.title = headers.firstOrNull { it.key == activeKey }?.title ?: "设置"
            } else {
                svLevel1.visibility = View.VISIBLE
                svLevel1.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                svLevel2.visibility = View.GONE
                supportActionBar?.title = "设置"
            }
        }
    }

    private fun renderLevel1Headers() {
        llLevel1.removeAllViews()
        val primary = UserProfileStore.getThemePrimaryColor(this)

        // Compact user profile banner at top of Level 1
        val profileCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.parseColor("#EEF5FC"))
            setOnClickListener { openSection("user") }
        }
        val ivMiniAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        UserProfileStore.applyAvatarTo(this, ivMiniAvatar)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
        }
        col.addView(TextView(this).apply {
            text = UserProfileStore.getUserName(this@SettingsActivity)
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
        })
        col.addView(TextView(this).apply {
            text = "${UserProfileStore.getClassName(this@SettingsActivity)} · 学号 ${UserProfileStore.getStudentId(this@SettingsActivity)}"
            textSize = 12f
            setTextColor(Color.parseColor("#607D8B"))
        })
        profileCard.addView(ivMiniAvatar)
        profileCard.addView(col)
        llLevel1.addView(profileCard)
        llLevel1.addView(makeDivider())

        for (h in headers) {
            val selected = isTwoPane && h.key == activeKey
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(13), dp(14), dp(13))
                setBackgroundColor(if (selected) Color.parseColor("#E3F2FD") else Color.WHITE)
                setOnClickListener { openSection(h.key) }
            }
            row.addView(ImageView(this).apply {
                setImageResource(h.iconRes)
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
                scaleType = ImageView.ScaleType.FIT_CENTER
            })
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                }
            }
            textCol.addView(TextView(this).apply {
                text = h.title
                textSize = 15f
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (selected) primary else Color.parseColor("#212121"))
            })
            textCol.addView(TextView(this).apply {
                text = h.summary
                textSize = 12f
                maxLines = 1
                setTextColor(Color.parseColor("#78909C"))
            })
            row.addView(textCol)
            row.addView(TextView(this).apply {
                text = "›"
                textSize = 20f
                setTextColor(if (selected) primary else Color.parseColor("#B0BEC5"))
            })
            llLevel1.addView(row)
            llLevel1.addView(makeDivider())
        }
    }

    private fun openSection(key: String) {
        if (key == "system") {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }.onFailure {
                Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show()
            }
            return
        }
        activeKey = key
        if (!isTwoPane) {
            inPhoneLevel2 = true
            applyPaneVisibility()
        }
        renderLevel1Headers()
        renderLevel2Detail(key)
    }

    private fun renderLevel2Detail(key: String) {
        llLevel2.removeAllViews()
        svLevel2.scrollTo(0, 0)
        when (key) {
            "user" -> buildUserDetail()
            "ui" -> buildUiDetail()
            "draw" -> buildDrawDetail()
            "cache" -> buildCacheDetail()
            "misc" -> buildMiscDetail()
            "about" -> buildAboutDetail()
        }
    }

    // ==================== Level 2: 基本信息（头像、用户名、班级、登录设置） ====================
    private fun buildUserDetail() {
        llLevel2.addView(makeCategoryTitle("头像设置"))
        val avatarCard = makeCard()
        val avatarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ivPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        UserProfileStore.applyAvatarTo(this, ivPreview)
        val btnCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) }
        }
        btnCol.addView(TextView(this).apply {
            text = "用户头像"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
        })
        btnCol.addView(TextView(this).apply {
            text = "支持从相册自定义头像或选用内置头像，实时同步到桌面左侧抽屉"
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, dp(3), 0, dp(8))
        })
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(makePrimaryButton("相册选图") { pickImage(REQ_PICK_AVATAR) })
        btnRow.addView(makeOutlineButton("内置头像") { showAvatarPresetDialog() }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(8)
        })
        btnCol.addView(btnRow)
        avatarRow.addView(ivPreview)
        avatarRow.addView(btnCol)
        avatarCard.addView(avatarRow)
        llLevel2.addView(avatarCard)

        llLevel2.addView(makeCategoryTitle("当前账户信息"))
        val infoCard = makeCard()
        infoCard.addView(makePrefRow("用户名（姓名）", UserProfileStore.getUserName(this)) {
            promptEditValue("修改用户名", UserProfileStore.getUserName(this), "输入新的用户名") { v ->
                UserProfileStore.setUserName(this, v)
                renderLevel1Headers()
                renderLevel2Detail("user")
            }
        })
        infoCard.addView(makeDivider())
        infoCard.addView(makePrefRow("学号", UserProfileStore.getStudentId(this)) {
            promptEditValue("修改学号", UserProfileStore.getStudentId(this), "输入学号") { v ->
                UserProfileStore.setStudentId(this, v)
                renderLevel1Headers()
                renderLevel2Detail("user")
            }
        })
        infoCard.addView(makeDivider())
        infoCard.addView(makePrefRow("班级 / 学校", UserProfileStore.getClassName(this)) {
            promptEditValue("修改班级", UserProfileStore.getClassName(this), "如：九年级 3 班") { v ->
                UserProfileStore.setClassName(this, v)
                renderLevel1Headers()
                renderLevel2Detail("user")
            }
        })
        llLevel2.addView(infoCard)

        llLevel2.addView(makeCategoryTitle("登录设置"))
        val loginCard = makeCard()
        loginCard.addView(makeSwitchRow("保存登录密码", "选中后每次无需重复输入密码", UserProfileStore.isRememberPassword(this)) {
            UserProfileStore.setRememberPassword(this, it)
        })
        loginCard.addView(makeDivider())
        loginCard.addView(makeSwitchRow("自动登录", "配合保存密码，下次启动自动进入桌面", UserProfileStore.isAutoLogin(this)) {
            UserProfileStore.setAutoLogin(this, it)
        })
        loginCard.addView(makeDivider())
        loginCard.addView(makePrefRow("修改登录密码", "点击设置本地演示登录密码") {
            promptEditValue("修改登录密码", "", "请输入新密码") {
                Toast.makeText(this, "登录密码已更新", Toast.LENGTH_SHORT).show()
            }
        })
        llLevel2.addView(loginCard)
    }

    // ==================== Level 2: 界面与背景设置（背景图片、桌面图标、悬浮条、主题） ====================
    private fun buildUiDetail() {
        llLevel2.addView(makeCategoryTitle("桌面背景图片（横竖屏自动拉伸铺满）"))
        val bgCard = makeCard()
        val bgRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ivBgPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(118), dp(82))
            scaleType = ImageView.ScaleType.FIT_XY
        }
        UserProfileStore.applyDesktopBgTo(this, ivBgPreview)
        val bgInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) }
        }
        val curBgType = UserProfileStore.getDesktopBgType(this)
        val curBgName = UserProfileStore.BG_PRESETS.firstOrNull { it.first == curBgType }?.second ?: "原版书桌背景"
        bgInfo.addView(TextView(this).apply {
            text = "当前：$curBgName"
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
        })
        bgInfo.addView(TextView(this).apply {
            text = "横屏与竖屏都会自动拉伸填满屏幕，桌面 7 件物品按相对坐标跟随定位"
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, dp(3), 0, dp(8))
        })
        val bgBtnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bgBtnRow.addView(makePrimaryButton("相册换壁纸") { pickImage(REQ_PICK_BG) })
        bgBtnRow.addView(makeOutlineButton("预设 / 还原") { showBgPresetDialog() }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(8)
        })
        bgInfo.addView(bgBtnRow)
        bgRow.addView(ivBgPreview)
        bgRow.addView(bgInfo)
        bgCard.addView(bgRow)
        llLevel2.addView(bgCard)

        llLevel2.addView(makeCategoryTitle("桌面图标与悬浮工具条"))
        val prefs = getSharedPreferences("shaonianpai_prefs", MODE_PRIVATE)
        val iconCard = makeCard()
        iconCard.addView(makeSwitchRow("锁定桌面图标", "选中后不能随意拖动桌面 7 件物品", prefs.getBoolean("LockIcons", false)) {
            prefs.edit().putBoolean("LockIcons", it).apply()
        })
        iconCard.addView(makeDivider())
        val shownCnt = UserProfileStore.getVisibleDeskIcons(this).size
        iconCard.addView(makePrefRow("桌面显示图标...", "当前显示 $shownCnt / 7 个桌面物品（隐藏项仍可从左侧抽屉打开）") {
            showDeskIconsMultiSelectDialog()
        })
        iconCard.addView(makeDivider())
        iconCard.addView(makePrefRow("还原默认图标位置", "重置全部 7 个桌面物品的相对位置坐标") {
            val ed = prefs.edit()
            for (k in UserProfileStore.ALL_DESK_ICON_KEYS) {
                ed.remove("pos_x_$k").remove("pos_y_$k").remove("pos_rx_$k").remove("pos_ry_$k")
            }
            ed.apply()
            Toast.makeText(this, "已还原全部 7 个桌面图标默认相对坐标", Toast.LENGTH_SHORT).show()
        })
        iconCard.addView(makeDivider())
        iconCard.addView(makeSwitchRow("显示全局悬浮快捷条", "收藏夹 / 截屏 / 拍照 / 草稿本 / 速记", FloatingQuickBar.isEnabled(this)) {
            FloatingQuickBar.setEnabled(this, it)
            FloatingQuickBar.attachIfNeeded(this)
        })
        llLevel2.addView(iconCard)

        llLevel2.addView(makeCategoryTitle("主题配色"))
        val themeCard = makeCard()
        val curTheme = UserProfileStore.getThemeKey(this)
        val curThemeLabel = UserProfileStore.THEME_OPTIONS.firstOrNull { it.first == curTheme }?.second ?: "默认深海蓝"
        themeCard.addView(makePrefRow("界面主题", "当前：$curThemeLabel") {
            val labels = UserProfileStore.THEME_OPTIONS.map { it.second }.toTypedArray()
            val idx = UserProfileStore.THEME_OPTIONS.indexOfFirst { it.first == curTheme }.coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("选择界面主题")
                .setSingleChoiceItems(labels, idx) { dlg, which ->
                    UserProfileStore.setThemeKey(this, UserProfileStore.THEME_OPTIONS[which].first)
                    toolbar.setBackgroundColor(UserProfileStore.getThemePrimaryColor(this))
                    dlg.dismiss()
                    renderLevel1Headers()
                    renderLevel2Detail("ui")
                }
                .show()
        })
        llLevel2.addView(themeCard)
    }

    // ==================== Level 2: 手写笔设置 ====================
    private fun buildDrawDetail() {
        llLevel2.addView(makeCategoryTitle("笔粗细设置（同步应用于草稿本与 PDF 批注）"))
        val card = makeCard()
        val fine = UserProfileStore.getFinePenWidth(this).toInt()
        val thick = UserProfileStore.getThickPenWidth(this).toInt()
        card.addView(makePrefRow("细笔 / 铅笔默认粗细", "当前：${fine}px（推荐 2–4px）") {
            val opts = arrayOf("1px", "2px", "3px（默认）", "4px", "6px", "8px")
            val vals = floatArrayOf(1f, 2f, 3f, 4f, 6f, 8f)
            AlertDialog.Builder(this)
                .setTitle("细笔粗细")
                .setItems(opts) { _, w ->
                    UserProfileStore.setFinePenWidth(this, vals[w])
                    renderLevel2Detail("draw")
                }.show()
        })
        card.addView(makeDivider())
        card.addView(makePrefRow("粗笔默认粗细", "当前：${thick}px（推荐 8–12px）") {
            val opts = arrayOf("6px", "8px", "10px（默认）", "12px", "16px", "20px")
            val vals = floatArrayOf(6f, 8f, 10f, 12f, 16f, 20f)
            AlertDialog.Builder(this)
                .setTitle("粗笔粗细")
                .setItems(opts) { _, w ->
                    UserProfileStore.setThickPenWidth(this, vals[w])
                    renderLevel2Detail("draw")
                }.show()
        })
        llLevel2.addView(card)
    }

    // ==================== Level 2: 缓存设置 ====================
    private fun buildCacheDetail() {
        llLevel2.addView(makeCategoryTitle("分项存储与缓存统计"))
        val assetCacheSize = dirBytes(File(cacheDir, "assets"))
        val exportCacheSize = dirBytes(File(cacheDir, "exports")) + dirBytes(File(cacheDir, "updates"))
        val annotSize = dirBytes(File(filesDir, "pdf_annots"))
        val favSize = dirBytes(File(filesDir, "questionbook_media")) + File(filesDir, "questionbook_items.json").length()
        val scratchSize = dirBytes(File(filesDir, "scratchpad"))

        val card = makeCard()
        card.addView(makeInfoRow("课程资源与 PDF 缓存", Repository.formatBytes(assetCacheSize)))
        card.addView(makeDivider())
        card.addView(makeInfoRow("导出文件与安装包临时缓存", Repository.formatBytes(exportCacheSize)))
        card.addView(makeDivider())
        card.addView(makeInfoRow("PDF 批注数据（受保护）", Repository.formatBytes(annotSize)))
        card.addView(makeDivider())
        card.addView(makeInfoRow("我的收藏夹与草稿本（受保护）", Repository.formatBytes(favSize + scratchSize)))
        llLevel2.addView(card)

        llLevel2.addView(makeCategoryTitle("清理操作"))
        val cleanCard = makeCard()
        cleanCard.addView(makePrimaryButton("清除临时缓存（保留批注与收藏夹）") {
            File(cacheDir, "assets").deleteRecursively()
            File(cacheDir, "exports").deleteRecursively()
            File(cacheDir, "updates").deleteRecursively()
            Toast.makeText(this, "临时缓存已清理完成", Toast.LENGTH_SHORT).show()
            renderLevel2Detail("cache")
        })
        llLevel2.addView(cleanCard)
    }

    // ==================== Level 2: 其它与服务器 ====================
    private fun buildMiscDetail() {
        llLevel2.addView(makeCategoryTitle("教师资料服务地址"))
        val srvCard = makeCard()
        val etServer = EditText(this).apply {
            setText(Repository.getServerUrl(this@SettingsActivity))
            hint = "http://10.0.2.2:8765"
            setSingleLine(true)
        }
        val tvStatus = TextView(this).apply {
            text = "支持局域网教师端资料同步，断网自动回退离线测试资源"
            textSize = 12.5f
            setTextColor(Color.parseColor("#607D8B"))
            setPadding(0, dp(8), 0, 0)
        }
        srvCard.addView(etServer)
        srvCard.addView(makePrimaryButton("保存并测试连接") {
            Repository.setServerUrl(this, etServer.text.toString().trim())
            tvStatus.text = "正在测试连接..."
            Thread {
                val (tasks, online) = Repository.fetchDeliveriesSync(this)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        tvStatus.text = if (online) "连接成功（拉取到 ${tasks.size} 个任务）"
                        else "未连上服务器，已自动回退离线测试数据（${tasks.size} 个任务）"
                    }
                }
            }.start()
        }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8) })
        srvCard.addView(tvStatus)
        llLevel2.addView(srvCard)

        llLevel2.addView(makeCategoryTitle("其它设置"))
        val miscCard = makeCard()
        miscCard.addView(makeSwitchRow("夜晚平板休眠", "在每晚 23:00 到次日 6:00 期间允许平板完全休眠", UserProfileStore.isSleepInNight(this)) {
            UserProfileStore.setSleepInNight(this, it)
        })
        miscCard.addView(makeDivider())
        miscCard.addView(makePrefRow("删除所有未读提示", "清除自主学习与任务清单的未读红点提示") {
            Toast.makeText(this, "已清除全部未读提示", Toast.LENGTH_SHORT).show()
        })
        llLevel2.addView(miscCard)
    }

    // ==================== Level 2: 关于与软件更新 ====================
    private fun buildAboutDetail() {
        llLevel2.addView(makeCategoryTitle("软件更新（GitHub Releases）"))
        val card = makeCard()
        val curVer = UpdateManager.getCurrentVersionName(this)
        card.addView(TextView(this).apply {
            text = "少年派学习平板复刻版 · 当前版本：v$curVer"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#212121"))
        })
        card.addView(makeSwitchRow("启动时自动检查 GitHub 新版本", "检测到新版后自动弹出更新下载窗", UpdateManager.isAutoCheckEnabled(this)) {
            UpdateManager.setAutoCheckEnabled(this, it)
        })
        card.addView(TextView(this).apply {
            text = "更新源仓库（owner/repo）"
            textSize = 12.5f
            setTextColor(Color.parseColor("#607D8B"))
            setPadding(0, dp(8), 0, dp(2))
        })
        val etRepo = EditText(this).apply {
            setText(UpdateManager.getGithubRepo(this@SettingsActivity))
            hint = UpdateManager.DEFAULT_GITHUB_REPO
            setSingleLine(true)
        }
        card.addView(etRepo)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        val pb = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(12) }
            visibility = View.GONE
        }
        val tvStatus = TextView(this).apply {
            text = "提示：固定正式签名版本支持应用内直接免卸载覆盖升级"
            textSize = 12.5f
            setTextColor(Color.parseColor("#607D8B"))
            setPadding(0, dp(8), 0, 0)
        }
        lateinit var btnCheck: Button
        btnCheck = makePrimaryButton("检查并安装更新") {
            val repo = etRepo.text.toString().trim().ifBlank { UpdateManager.DEFAULT_GITHUB_REPO }
            UpdateManager.setGithubRepo(this, repo)
            btnCheck.isEnabled = false
            pb.visibility = View.VISIBLE
            tvStatus.text = "正在从 GitHub（$repo）检查最新版本..."
            Thread {
                val result = UpdateManager.checkLatestReleaseSync(this)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    btnCheck.isEnabled = true
                    pb.visibility = View.GONE
                    when (result) {
                        is UpdateCheckResult.UpdateAvailable -> {
                            tvStatus.text = "发现新版本 ${result.release.tagName}（当前 v${result.currentVersion}）"
                            UpdateUiHelper.showUpdateDialog(this, result.currentVersion, result.release)
                        }
                        is UpdateCheckResult.UpToDate -> {
                            tvStatus.text = "当前已是最新版本（v${result.currentVersion}，GitHub 最新：${result.latestTag}）"
                            Toast.makeText(this, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                        }
                        is UpdateCheckResult.Error -> {
                            tvStatus.text = result.message
                            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.start()
        }
        actionRow.addView(btnCheck)
        actionRow.addView(pb)
        card.addView(actionRow)
        card.addView(tvStatus)
        card.addView(makeDivider().apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(12) })
        card.addView(makePrefRow("查看版本更新记录", "v0.1.0 → v0.1.4 完整演进日志") {
            AlertDialog.Builder(this)
                .setTitle("版本更新记录")
                .setMessage(
                    "• v0.1.4：纯书桌全屏拉伸与相对坐标、1+2 级原版架构设置（壁纸/头像/用户名）、任务详情内嵌 PDF 预览与左侧目录、福昕 PDF 页码/顶栏/连续滚动精修\n\n" +
                        "• v0.1.3：原版蓝框全局悬浮快捷条、真实本地收藏夹与 6 种纸张多页草稿本\n\n" +
                        "• v0.1.2：福昕风格全功能 PDF 批注阅读器（PDFBox 划词高亮/搜索/目录/导出）\n\n" +
                        "• v0.1.1：固定正式签名与 v0.1.n+1 自动发布\n\n" +
                        "• v0.1.0：原生客户端与本地教师端首版"
                )
                .setPositiveButton("确定", null)
                .show()
        })
        llLevel2.addView(card)
    }

    // ==================== Pickers & Dialog Helpers ====================
    private fun pickImage(reqCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        runCatching {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent.createChooser(intent, "选择图片"), reqCode)
        }.onFailure {
            Toast.makeText(this, "未检测到相册应用", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_PICK_AVATAR -> {
                if (UserProfileStore.saveCustomAvatarFromUri(this, uri)) {
                    Toast.makeText(this, "用户头像已更新", Toast.LENGTH_SHORT).show()
                    renderLevel1Headers()
                    renderLevel2Detail("user")
                } else {
                    Toast.makeText(this, "头像读取失败", Toast.LENGTH_SHORT).show()
                }
            }
            REQ_PICK_BG -> {
                if (UserProfileStore.saveCustomDesktopBgFromUri(this, uri)) {
                    Toast.makeText(this, "桌面背景已更新（返回桌面立即生效）", Toast.LENGTH_SHORT).show()
                    renderLevel2Detail("ui")
                } else {
                    Toast.makeText(this, "壁纸读取失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAvatarPresetDialog() {
        val items = arrayOf("原版默认头像", "深海蓝字母头像", "青绿字母头像", "活力橙字母头像", "梦幻紫字母头像")
        val cur = UserProfileStore.getAvatarPreset(this).coerceIn(0, 4)
        AlertDialog.Builder(this)
            .setTitle("选择内置头像")
            .setSingleChoiceItems(items, cur) { dlg, which ->
                UserProfileStore.setAvatarPreset(this, which)
                dlg.dismiss()
                renderLevel1Headers()
                renderLevel2Detail("user")
            }
            .show()
    }

    private fun showBgPresetDialog() {
        val presets = UserProfileStore.BG_PRESETS.filter { it.first != "CUSTOM" }
        val labels = presets.map { it.second }.toTypedArray()
        val curType = UserProfileStore.getDesktopBgType(this)
        val curIdx = presets.indexOfFirst { it.first == curType }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("选择桌面背景")
            .setSingleChoiceItems(labels, curIdx) { dlg, which ->
                UserProfileStore.setDesktopBgType(this, presets[which].first)
                dlg.dismiss()
                Toast.makeText(this, "已切换为「${presets[which].second}」", Toast.LENGTH_SHORT).show()
                renderLevel2Detail("ui")
            }
            .show()
    }

    private fun showDeskIconsMultiSelectDialog() {
        val visible = UserProfileStore.getVisibleDeskIcons(this).toMutableSet()
        val labels = UserProfileStore.ALL_DESK_ICON_LABELS.toTypedArray()
        val checked = BooleanArray(UserProfileStore.ALL_DESK_ICON_KEYS.size) { i ->
            visible.contains(UserProfileStore.ALL_DESK_ICON_KEYS[i])
        }
        AlertDialog.Builder(this)
            .setTitle("选择显示在桌面的图标")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val k = UserProfileStore.ALL_DESK_ICON_KEYS[which]
                if (isChecked) visible.add(k) else visible.remove(k)
            }
            .setPositiveButton("保存") { _, _ ->
                UserProfileStore.setVisibleDeskIcons(this, visible)
                renderLevel2Detail("ui")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun promptEditValue(title: String, current: String, hint: String, onSave: (String) -> Unit) {
        val et = EditText(this).apply {
            setText(current)
            this.hint = hint
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("确定") { _, _ -> onSave(et.text.toString()) }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== UI Builders ====================
    private fun makeCategoryTitle(title: String): TextView = TextView(this).apply {
        text = title
        textSize = 13.5f
        setTypeface(null, Typeface.BOLD)
        setTextColor(UserProfileStore.getThemePrimaryColor(this@SettingsActivity))
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#E6EBF2"))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }
    }

    private fun makePrefRow(title: String, summary: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            val col = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 15f
                setTextColor(Color.parseColor("#212121"))
            })
            col.addView(TextView(this@SettingsActivity).apply {
                text = summary
                textSize = 12.5f
                setTextColor(Color.parseColor("#757575"))
                setPadding(0, dp(2), 0, 0)
            })
            addView(col)
            addView(TextView(this@SettingsActivity).apply {
                text = "›"
                textSize = 20f
                setTextColor(Color.parseColor("#B0BEC5"))
            })
            setOnClickListener { onClick() }
        }
    }

    private fun makeInfoRow(title: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 14.5f
                setTextColor(Color.parseColor("#212121"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = value
                textSize = 14f
                setTextColor(UserProfileStore.getThemePrimaryColor(this@SettingsActivity))
                setTypeface(null, Typeface.BOLD)
            })
        }
    }

    private fun makeSwitchRow(title: String, summary: String, checked: Boolean, onChanged: (Boolean) -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            val col = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(10) }
            }
            col.addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 15f
                setTextColor(Color.parseColor("#212121"))
            })
            col.addView(TextView(this@SettingsActivity).apply {
                text = summary
                textSize = 12f
                setTextColor(Color.parseColor("#757575"))
            })
            val sw = Switch(this@SettingsActivity).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
            }
            addView(col)
            addView(sw)
        }
    }

    private fun makePrimaryButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 13f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(UserProfileStore.getThemePrimaryColor(this@SettingsActivity))
            cornerRadius = dp(6).toFloat()
        }
        setPadding(dp(14), dp(6), dp(14), dp(6))
        minHeight = dp(36)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener { onClick() }
    }

    private fun makeOutlineButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 13f
        val primary = UserProfileStore.getThemePrimaryColor(this@SettingsActivity)
        setTextColor(primary)
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), primary)
            cornerRadius = dp(6).toFloat()
        }
        setPadding(dp(14), dp(6), dp(14), dp(6))
        minHeight = dp(36)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener { onClick() }
    }

    private fun makeDivider(): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#EEEEEE"))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun dirBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirBytes(it) } ?: 0L
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
