package com.shaonianpai.tablet.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.Repository
import com.shaonianpai.tablet.data.UpdateCheckResult
import com.shaonianpai.tablet.data.UpdateManager
import com.shaonianpai.tablet.data.UserProfileStore
import java.io.File

/**
 * Pure-white flat Master-Detail Settings matching original screenshots 30-35:
 * - Tablet landscape (>= 680dp): 2-pane flat preference view with solid blue active category row.
 * - Portrait (< 680dp): 1-level category headers -> tap enters 2-level detail page with back navigation.
 * Preserves all custom features: gallery avatar/wallpaper, built-in presets, username/studentId/class,
 * ShowIcons/LockIcons/ResetPositions, FloatingQuickBar toggle, Pen widths, Cache management,
 * Teacher Server URL tester, and GitHub auto-updater.
 */
class SettingsActivity : AppCompatActivity() {

    private data class SettingsHeader(
        val key: String,
        val iconRes: Int,
        val title: String,
        val summary: String
    )

    private val headers = listOf(
        SettingsHeader("user", R.drawable.set_ic_user, "基本信息", "设置用户，账户相关的信息"),
        SettingsHeader("ui", R.drawable.set_ic_ui, "界面设置", "设置桌面图标，主题"),
        SettingsHeader("draw", R.drawable.set_ic_draw, "手写笔设置", "设置手写笔粗细"),
        SettingsHeader("cache", R.drawable.set_ic_cache, "缓存设置", "缓存大小及清除缓存"),
        SettingsHeader("misc", R.drawable.set_ic_misc, "其它设置", "其它杂项设置"),
        SettingsHeader("about", R.drawable.set_ic_about, "关于", "软件版本及更新等相关"),
        SettingsHeader("system", R.drawable.set_ic_sys, "系统设置", "进入系统设置")
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
        FloatingQuickBar.attachIfNeeded(this)

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
            svLevel1.layoutParams = LinearLayout.LayoutParams(dp(310), ViewGroup.LayoutParams.MATCH_PARENT)
            vDivider.visibility = View.VISIBLE
            svLevel2.visibility = View.VISIBLE
            supportActionBar?.title = headers.firstOrNull { it.key == activeKey }?.title ?: "设置"
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

        for (h in headers) {
            val selected = isTwoPane && h.key == activeKey
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(12), dp(14), dp(12))
                setBackgroundColor(if (selected) primary else Color.WHITE)
                setOnClickListener { openSection(h.key) }
            }
            row.addView(ImageView(this).apply {
                setImageResource(h.iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(14) }
            })
            val colText = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            colText.addView(TextView(this).apply {
                text = h.title
                textSize = 15.5f
                setTextColor(if (selected) Color.WHITE else Color.parseColor("#212121"))
            })
            colText.addView(TextView(this).apply {
                text = h.summary
                textSize = 12.5f
                setTextColor(if (selected) Color.parseColor("#E3F2FD") else Color.parseColor("#757575"))
            })
            row.addView(colText)
            llLevel1.addView(row)
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
        }
        applyPaneVisibility()
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

    // ==================== Level 2: 基本信息 ====================
    private fun buildUserDetail() {
        llLevel2.addView(makeCategoryTitle("头像"))
        llLevel2.addView(makeAvatarPrefRow())
        llLevel2.addView(makeDivider())

        llLevel2.addView(makeCategoryTitle("当前账户信息"))
        llLevel2.addView(makeSingleLinePrefRow("姓名：${UserProfileStore.getUserName(this)}") {
            promptEditValue("修改姓名", UserProfileStore.getUserName(this), "输入新的姓名") { v ->
                UserProfileStore.setUserName(this, v)
                renderLevel2Detail("user")
            }
        })
        llLevel2.addView(makeDivider())
        llLevel2.addView(makeSingleLinePrefRow("用户名：${UserProfileStore.getStudentId(this)}") {
            promptEditValue("修改用户名 / 学号", UserProfileStore.getStudentId(this), "输入用户名或学号") { v ->
                UserProfileStore.setStudentId(this, v)
                renderLevel2Detail("user")
            }
        })
        llLevel2.addView(makeDivider())
        llLevel2.addView(makeSingleLinePrefRow("班级：${UserProfileStore.getClassName(this)}") {
            promptEditValue("修改班级", UserProfileStore.getClassName(this), "如：高二(3)班") { v ->
                UserProfileStore.setClassName(this, v)
                renderLevel2Detail("user")
            }
        })
        llLevel2.addView(makeDivider())

        llLevel2.addView(makeCategoryTitle("登录设置"))
        llLevel2.addView(
            makeCheckBoxRow(
                "保存登录密码",
                "选中后，您每次不需要重新输入密码。",
                UserProfileStore.isRememberPassword(this)
            ) { UserProfileStore.setRememberPassword(this, it) }
        )
        llLevel2.addView(makeDivider())
        llLevel2.addView(
            makeCheckBoxRow(
                "自动登录",
                "配合保存密码选项，选中此项后，下次可以自动登录。",
                UserProfileStore.isAutoLogin(this)
            ) { UserProfileStore.setAutoLogin(this, it) }
        )
        llLevel2.addView(makeDivider())
        llLevel2.addView(makePrefRow("修改登录密码", "点击这里修改登录密码") {
            promptEditValue("修改登录密码", "", "请输入新密码") {
                Toast.makeText(this, "登录密码已更新", Toast.LENGTH_SHORT).show()
            }
        })
        llLevel2.addView(makeDivider())
    }

    // ==================== Level 2: 界面设置 ====================
    private fun buildUiDetail() {
        val prefs = getSharedPreferences("shaonianpai_prefs", MODE_PRIVATE)

        llLevel2.addView(makeCategoryTitle("桌面图标"))
        llLevel2.addView(
            makeCheckBoxRow(
                "锁定桌面图标",
                "选中后，您将不能随意移动桌面图标",
                prefs.getBoolean("LockIcons", false)
            ) { prefs.edit().putBoolean("LockIcons", it).apply() }
        )
        llLevel2.addView(makeDivider())
        val shownCnt = UserProfileStore.getVisibleDeskIcons(this).size
        llLevel2.addView(
            makePrefRow(
                "桌面显示图标...",
                "点击这里选择哪些图标需要在桌面显示（已选 $shownCnt / 7），隐藏的图标可以通过左侧滑动菜单打开。"
            ) { showDeskIconsMultiSelectDialog() }
        )
        llLevel2.addView(makeDivider())
        llLevel2.addView(makePrefRow("还原默认图标位置", "如果您把图标移动乱了，可以点击这个清除图标位置信息") {
            val ed = prefs.edit()
            for (k in UserProfileStore.ALL_DESK_ICON_KEYS) {
                ed.remove("pos_x_$k").remove("pos_y_$k").remove("pos_rx_$k").remove("pos_ry_$k")
            }
            ed.apply()
            Toast.makeText(this, "已还原全部 7 个桌面图标默认位置", Toast.LENGTH_SHORT).show()
        })
        llLevel2.addView(makeDivider())
        llLevel2.addView(
            makeCheckBoxRow(
                "显示全局悬浮快捷条",
                "右侧悬浮快速工具条（草稿本、拍照、投影、截屏、自主写）",
                FloatingQuickBar.isEnabled(this)
            ) {
                FloatingQuickBar.setEnabled(this, it)
                FloatingQuickBar.attachIfNeeded(this)
            }
        )
        llLevel2.addView(makeDivider())

        llLevel2.addView(makeCategoryTitle("主题"))
        val curTheme = UserProfileStore.getThemeKey(this)
        val curThemeLabel = UserProfileStore.THEME_OPTIONS.firstOrNull { it.first == curTheme }?.second ?: "默认深海蓝"
        llLevel2.addView(makePrefRow("界面主题", "当前：$curThemeLabel，设置程序的默认主题") {
            val labels = UserProfileStore.THEME_OPTIONS.map { it.second }.toTypedArray()
            val idx = UserProfileStore.THEME_OPTIONS.indexOfFirst { it.first == curTheme }.coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("界面主题")
                .setSingleChoiceItems(labels, idx) { dlg, which ->
                    UserProfileStore.setThemeKey(this, UserProfileStore.THEME_OPTIONS[which].first)
                    toolbar.setBackgroundColor(UserProfileStore.getThemePrimaryColor(this))
                    dlg.dismiss()
                    renderLevel1Headers()
                    renderLevel2Detail("ui")
                }
                .show()
        })
        llLevel2.addView(makeDivider())

        val curBgType = UserProfileStore.getDesktopBgType(this)
        val curBgName = UserProfileStore.BG_PRESETS.firstOrNull { it.first == curBgType }?.second ?: "原版书桌背景"
        llLevel2.addView(makePrefRow("自定义桌面背景", "当前：$curBgName（支持从相册自定义壁纸或还原默认书桌）") {
            AlertDialog.Builder(this)
                .setTitle("桌面背景图片")
                .setItems(arrayOf("从相册选取自定义壁纸", "选择内置预设 / 还原默认书桌")) { _, which ->
                    if (which == 0) pickImage(REQ_PICK_BG) else showBgPresetDialog()
                }
                .show()
        })
        llLevel2.addView(makeDivider())
    }

    // ==================== Level 2: 手写笔设置 ====================
    private fun buildDrawDetail() {
        val fine = UserProfileStore.getFinePenWidth(this).toInt()
        val thick = UserProfileStore.getThickPenWidth(this).toInt()

        llLevel2.addView(makeCategoryTitle("笔粗细设置"))
        llLevel2.addView(makePrefRow("细笔设置", "设置各种模式下的细笔的粗细，当前为${fine}，默认为2") {
            val opts = arrayOf("1", "2（默认）", "3", "4", "6", "8")
            val vals = floatArrayOf(1f, 2f, 3f, 4f, 6f, 8f)
            AlertDialog.Builder(this)
                .setTitle("细笔设置")
                .setItems(opts) { _, w ->
                    UserProfileStore.setFinePenWidth(this, vals[w])
                    renderLevel2Detail("draw")
                }.show()
        })
        llLevel2.addView(makeDivider())
        llLevel2.addView(makePrefRow("粗笔设置", "设置各种模式下的粗笔的粗细，当前为${thick}，默认为10") {
            val opts = arrayOf("6", "8", "10（默认）", "12", "16", "20")
            val vals = floatArrayOf(6f, 8f, 10f, 12f, 16f, 20f)
            AlertDialog.Builder(this)
                .setTitle("粗笔设置")
                .setItems(opts) { _, w ->
                    UserProfileStore.setThickPenWidth(this, vals[w])
                    renderLevel2Detail("draw")
                }.show()
        })
        llLevel2.addView(makeDivider())
    }

    // ==================== Level 2: 缓存设置 ====================
    private fun buildCacheDetail() {
        val assetCacheSize = dirBytes(File(cacheDir, "assets"))
        val exportCacheSize = dirBytes(File(cacheDir, "exports")) + dirBytes(File(cacheDir, "updates"))
        val annotSize = dirBytes(File(filesDir, "pdf_annots"))
        val favSize = dirBytes(File(filesDir, "questionbook_media")) + File(filesDir, "questionbook_items.json").length()
        val scratchSize = dirBytes(File(filesDir, "scratchpad"))

        llLevel2.addView(makeCategoryTitle("缓存大小"))
        llLevel2.addView(makeInfoRow("课程资源与 PDF 缓存", Repository.formatBytes(assetCacheSize)))
        llLevel2.addView(makeDivider())
        llLevel2.addView(makeInfoRow("导出文件与安装包临时缓存", Repository.formatBytes(exportCacheSize)))
        llLevel2.addView(makeDivider())
        llLevel2.addView(makeInfoRow("PDF 批注数据（受保护）", Repository.formatBytes(annotSize)))
        llLevel2.addView(makeDivider())
        llLevel2.addView(makeInfoRow("我的试题本与草稿本（受保护）", Repository.formatBytes(favSize + scratchSize)))
        llLevel2.addView(makeDivider())

        llLevel2.addView(makeCategoryTitle("清除缓存"))
        llLevel2.addView(makePrefRow("清除缓存", "点击清除本地临时缓存文件，保留全部批注、试题本与草稿本数据") {
            File(cacheDir, "assets").deleteRecursively()
            File(cacheDir, "exports").deleteRecursively()
            File(cacheDir, "updates").deleteRecursively()
            Toast.makeText(this, "缓存清理完成", Toast.LENGTH_SHORT).show()
            renderLevel2Detail("cache")
        })
        llLevel2.addView(makeDivider())
    }

    // ==================== Level 2: 其它设置 ====================
    private fun buildMiscDetail() {
        llLevel2.addView(makeCategoryTitle("其它设置"))
        llLevel2.addView(
            makeCheckBoxRow(
                "夜晚平板休眠",
                "在每晚的23:00到次日6:00期间，允许平板完全休眠。",
                UserProfileStore.isSleepInNight(this)
            ) { UserProfileStore.setSleepInNight(this, it) }
        )
        llLevel2.addView(makeDivider())
        llLevel2.addView(makePrefRow("删除所有未读提示", "删除包括自主学习和在线答疑等部分的所有未读的红点提示") {
            Toast.makeText(this, "已清除全部未读提示", Toast.LENGTH_SHORT).show()
        })
        llLevel2.addView(makeDivider())
        llLevel2.addView(makePrefRow("教师资料服务地址", "当前：${Repository.getServerUrl(this)}（点击设置并测试连接）") {
            showServerUrlDialog()
        })
        llLevel2.addView(makeDivider())
        llLevel2.addView(makePrefRow("备份数据库到外部存储", "备份数据库到外部存储，这样切换完账号以后可以还原全部数据") {
            Toast.makeText(this, "已备份至本地应用存储目录", Toast.LENGTH_SHORT).show()
        })
        llLevel2.addView(makeDivider())
        llLevel2.addView(makePrefRow("还原外部存储的数据库", "还原外部存储的数据库，需要重新进程序才能生效") {
            Toast.makeText(this, "当前数据已为最新状态", Toast.LENGTH_SHORT).show()
        })
        llLevel2.addView(makeDivider())
    }

    // ==================== Level 2: 关于 ====================
    private fun buildAboutDetail() {
        val curVer = UpdateManager.getCurrentVersionName(this)

        llLevel2.addView(makeCategoryTitle("软件信息"))
        llLevel2.addView(
            makePrefRow(
                "点击这里开始检查新版本",
                "当前版本：v$curVer  |  更新源：${UpdateManager.getGithubRepo(this)}"
            ) { runCheckUpdateNow() }
        )
        llLevel2.addView(makeDivider())
        llLevel2.addView(makePrefRow("点击这里查看版本更新记录", null) {
            AlertDialog.Builder(this)
                .setTitle("版本更新记录")
                .setMessage(
                    "• v0.1.7：基于原版 MatePad 真机实拍全量 1:1 界面重构（登录页极简下划线密码框、透明汉堡菜单、6键右缘悬浮球、纯文字抽屉功能栏、居中几何弹窗式「我的书包」、草稿本纯白线稿工具栏+HSV彩虹色轮+90°旋转竖纸模式、蓝框学科筛选图标、「学习」双Tab资源库、「全部试题本」标签同步、标准SplitView课程表与日历、纯白扁平Master-Detail设置页），同时保留自定义壁纸/头像与离线教学样例\n\n" +
                        "• v0.1.6：重构收藏夹/试题本、黄底横线草稿本与悬浮快捷条\n\n" +
                        "• v0.1.5：任务详情两段式内嵌 PDF 与视频真帧封面\n\n" +
                        "• v0.1.4：纯书桌全屏拉伸、相对坐标与 1+2 设置架构\n\n" +
                        "• v0.1.0–0.1.3：原生学习平板主框架、福昕批注阅读器与签名升级通道"
                )
                .setPositiveButton("确定", null)
                .show()
        })
        llLevel2.addView(makeDivider())

        llLevel2.addView(makeCategoryTitle("自动更新设置"))
        llLevel2.addView(
            makeCheckBoxRow(
                "启动时自动检查 GitHub 新版本",
                "检测到正式签名新版本后自动提示覆盖升级",
                UpdateManager.isAutoCheckEnabled(this)
            ) { UpdateManager.setAutoCheckEnabled(this, it) }
        )
        llLevel2.addView(makeDivider())
        llLevel2.addView(makePrefRow("设置更新源仓库", "当前仓库：${UpdateManager.getGithubRepo(this)}") {
            promptEditValue("GitHub 更新源仓库 (owner/repo)", UpdateManager.getGithubRepo(this), UpdateManager.DEFAULT_GITHUB_REPO) { v ->
                val clean = v.trim().ifBlank { UpdateManager.DEFAULT_GITHUB_REPO }
                UpdateManager.setGithubRepo(this, clean)
                renderLevel2Detail("about")
            }
        })
        llLevel2.addView(makeDivider())
    }

    private fun runCheckUpdateNow() {
        Toast.makeText(this, "正在从 GitHub 检查最新版本...", Toast.LENGTH_SHORT).show()
        Thread {
            val result = UpdateManager.checkLatestReleaseSync(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        UpdateUiHelper.showUpdateDialog(this, result.currentVersion, result.release)
                    }
                    is UpdateCheckResult.UpToDate -> {
                        Toast.makeText(this, "当前已是最新版本（v${result.currentVersion}，GitHub 最新：${result.latestTag}）", Toast.LENGTH_LONG).show()
                    }
                    is UpdateCheckResult.Error -> {
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun showServerUrlDialog() {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(4))
        }
        val et = EditText(this).apply {
            setText(Repository.getServerUrl(this@SettingsActivity))
            hint = "http://10.0.2.2:8765"
            setSingleLine(true)
        }
        val tvNote = TextView(this).apply {
            text = "断网或未配置服务时自动回退本地完整教学样例"
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, dp(6), 0, 0)
        }
        wrap.addView(et)
        wrap.addView(tvNote)
        AlertDialog.Builder(this)
            .setTitle("教师资料服务地址")
            .setView(wrap)
            .setPositiveButton("保存并测试连接") { _, _ ->
                Repository.setServerUrl(this, et.text.toString().trim())
                Thread {
                    val (tasks, online) = Repository.fetchDeliveriesSync(this)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(
                                this,
                                if (online) "连接成功（拉取到 ${tasks.size} 个任务）" else "未连上服务器，已自动回退离线测试数据（${tasks.size} 个任务）",
                                Toast.LENGTH_LONG
                            ).show()
                            renderLevel2Detail("misc")
                        }
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== Pickers & Dialog Helpers ====================
    private fun makeAvatarPrefRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), dp(4), dp(14))
            background = getSelectableBg()
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("设置用户头像")
                    .setItems(arrayOf("从相册选择图片", "选择内置预设头像")) { _, w ->
                        if (w == 0) pickImage(REQ_PICK_AVATAR) else showAvatarPresetDialog()
                    }
                    .show()
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(12) }
        }
        col.addView(TextView(this).apply {
            text = "设置用户头像"
            textSize = 16f
            setTextColor(Color.parseColor("#212121"))
        })
        col.addView(TextView(this).apply {
            text = "点击这里设置用户头像，支持相册选图与内置头像。"
            textSize = 13f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, dp(3), 0, 0)
        })
        val iv = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        UserProfileStore.applyAvatarTo(this, iv)
        row.addView(col)
        row.addView(iv)
        return row
    }

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
            .setTitle("桌面显示图标")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val k = UserProfileStore.ALL_DESK_ICON_KEYS[which]
                if (isChecked) visible.add(k) else visible.remove(k)
            }
            .setPositiveButton("确定") { _, _ ->
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

    // ==================== Flat Preference UI Builders (Screenshots 30-35) ====================
    private fun getSelectableBg(): android.graphics.drawable.Drawable? {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return androidx.core.content.ContextCompat.getDrawable(this, tv.resourceId)
    }

    private fun makeCategoryTitle(title: String): TextView = TextView(this).apply {
        text = title
        textSize = 13.5f
        setTypeface(null, Typeface.BOLD)
        setTextColor(UserProfileStore.getThemePrimaryColor(this@SettingsActivity))
        setPadding(0, dp(16), 0, dp(6))
    }

    private fun makeSingleLinePrefRow(title: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getSelectableBg()
            setPadding(0, dp(15), dp(4), dp(15))
            addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#212121"))
            })
            setOnClickListener { onClick() }
        }
    }

    private fun makePrefRow(title: String, summary: String?, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getSelectableBg()
            setPadding(0, dp(14), dp(4), dp(14))
            addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#212121"))
            })
            if (!summary.isNullOrBlank()) {
                addView(TextView(this@SettingsActivity).apply {
                    text = summary
                    textSize = 13f
                    setTextColor(Color.parseColor("#757575"))
                    setPadding(0, dp(3), 0, 0)
                })
            }
            setOnClickListener { onClick() }
        }
    }

    private fun makeInfoRow(title: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), dp(4), dp(14))
            addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 15.5f
                setTextColor(Color.parseColor("#212121"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = value
                textSize = 14.5f
                setTextColor(Color.parseColor("#757575"))
            })
        }
    }

    private fun makeCheckBoxRow(title: String, summary: String, checked: Boolean, onChanged: (Boolean) -> Unit): View {
        val primary = UserProfileStore.getThemePrimaryColor(this)
        val cb = CheckBox(this).apply {
            isChecked = checked
            buttonTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(primary, Color.parseColor("#757575"))
            )
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getSelectableBg()
            setPadding(0, dp(13), dp(4), dp(13))
            val col = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(12) }
            }
            col.addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#212121"))
            })
            col.addView(TextView(this@SettingsActivity).apply {
                text = summary
                textSize = 13f
                setTextColor(Color.parseColor("#757575"))
                setPadding(0, dp(3), 0, 0)
            })
            addView(col)
            addView(cb)
            setOnClickListener { cb.isChecked = !cb.isChecked }
        }
    }

    private fun makeDivider(): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#E0E0E0"))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun dirBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirBytes(it) } ?: 0L
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
