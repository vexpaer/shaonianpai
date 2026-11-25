package com.shaonianpai.tablet.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.widget.ImageView
import com.shaonianpai.tablet.R
import java.io.File
import java.io.FileOutputStream

object UserProfileStore {
    private const val PREF = "shaonianpai_prefs"

    val ALL_DESK_ICON_KEYS = listOf("rewards", "clock", "schedule", "notebook", "study", "light", "bookbag")
    val ALL_DESK_ICON_LABELS = listOf("奖杯（数字教材）", "闹钟（课程提醒）", "日历（课程表）", "笔记本（试题本）", "书本（自主学习）", "台灯（系统设置）", "公文包（资源库）")

    val BG_PRESETS = listOf(
        "DEFAULT" to "原版书桌背景（默认）",
        "PRESET_WARM" to "护眼暖米色渐变背景",
        "PRESET_BLUE" to "清新天空蓝渐变背景",
        "PRESET_NIGHT" to "深邃夜间护眼背景",
        "CUSTOM" to "自定义相册图片"
    )

    val THEME_OPTIONS = listOf(
        "AppTheme" to "默认深海蓝 (#1976D2)",
        "AppThemeBlue" to "天空蓝 (#0288D1)",
        "AppThemePurple" to "梦幻紫 (#7B1FA2)"
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun profileDir(ctx: Context) = File(ctx.filesDir, "profile").apply { mkdirs() }

    fun avatarFile(ctx: Context): File = File(profileDir(ctx), "user_avatar.png")
    fun customBgFile(ctx: Context): File = File(profileDir(ctx), "desktop_bg.jpg")

    fun getUserName(ctx: Context): String = prefs(ctx).getString("user_name", "张明同学") ?: "张明同学"
    fun setUserName(ctx: Context, name: String) {
        prefs(ctx).edit().putString("user_name", name.trim().ifBlank { "张明同学" }).apply()
    }

    fun getStudentId(ctx: Context): String = prefs(ctx).getString("student_id", "20210903") ?: "20210903"
    fun setStudentId(ctx: Context, id: String) {
        prefs(ctx).edit().putString("student_id", id.trim().ifBlank { "20210903" }).apply()
    }

    fun getClassName(ctx: Context): String = prefs(ctx).getString("class_name", "九年级 3 班") ?: "九年级 3 班"
    fun setClassName(ctx: Context, cls: String) {
        prefs(ctx).edit().putString("class_name", cls.trim().ifBlank { "九年级 3 班" }).apply()
    }

    fun getAvatarPreset(ctx: Context): Int = prefs(ctx).getInt("avatar_preset", 0)
    fun setAvatarPreset(ctx: Context, preset: Int) {
        prefs(ctx).edit().putInt("avatar_preset", preset).apply()
        if (preset >= 0) runCatching { avatarFile(ctx).delete() }
    }

    fun getDesktopBgType(ctx: Context): String = prefs(ctx).getString("desktop_bg_type", "DEFAULT") ?: "DEFAULT"
    fun setDesktopBgType(ctx: Context, type: String) {
        prefs(ctx).edit().putString("desktop_bg_type", type).apply()
    }

    fun getThemeKey(ctx: Context): String = prefs(ctx).getString("MainTheme", "AppTheme") ?: "AppTheme"
    fun setThemeKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString("MainTheme", key).apply()
    }

    fun getThemePrimaryColor(ctx: Context): Int = when (getThemeKey(ctx)) {
        "AppThemeBlue" -> Color.parseColor("#0288D1")
        "AppThemePurple" -> Color.parseColor("#7B1FA2")
        else -> Color.parseColor("#1976D2")
    }

    fun getVisibleDeskIcons(ctx: Context): Set<String> {
        val raw = prefs(ctx).getStringSet("ShowIcons", null)
        return raw?.toSet() ?: ALL_DESK_ICON_KEYS.toSet()
    }

    fun setVisibleDeskIcons(ctx: Context, keys: Set<String>) {
        prefs(ctx).edit().putStringSet("ShowIcons", keys).apply()
    }

    fun getFinePenWidth(ctx: Context): Float = prefs(ctx).getFloat("Mode1PenWidth", 3f)
    fun setFinePenWidth(ctx: Context, w: Float) {
        prefs(ctx).edit().putFloat("Mode1PenWidth", w.coerceIn(1f, 12f)).apply()
    }

    fun getThickPenWidth(ctx: Context): Float = prefs(ctx).getFloat("Mode2PenWidth", 10f)
    fun setThickPenWidth(ctx: Context, w: Float) {
        prefs(ctx).edit().putFloat("Mode2PenWidth", w.coerceIn(4f, 22f)).apply()
    }

    fun isRememberPassword(ctx: Context): Boolean = prefs(ctx).getBoolean("RememberPassword", true)
    fun setRememberPassword(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("RememberPassword", v).apply()
    }

    fun isAutoLogin(ctx: Context): Boolean = prefs(ctx).getBoolean("AutoLogin", false)
    fun setAutoLogin(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("AutoLogin", v).apply()
    }

    fun isSleepInNight(ctx: Context): Boolean = prefs(ctx).getBoolean("sleepInNight", true)
    fun setSleepInNight(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("sleepInNight", v).apply()
    }

    fun saveCustomAvatarFromUri(ctx: Context, uri: Uri): Boolean {
        return try {
            val bmp = decodeUriScaled(ctx, uri, 360) ?: return false
            val square = centerCropCircleBitmap(bmp)
            FileOutputStream(avatarFile(ctx)).use { fos ->
                square.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }
            prefs(ctx).edit().putInt("avatar_preset", -1).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun saveCustomDesktopBgFromUri(ctx: Context, uri: Uri): Boolean {
        return try {
            val bmp = decodeUriScaled(ctx, uri, 1600) ?: return false
            FileOutputStream(customBgFile(ctx)).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            setDesktopBgType(ctx, "CUSTOM")
            true
        } catch (_: Exception) {
            false
        }
    }

    fun applyAvatarTo(ctx: Context, iv: ImageView) {
        val f = avatarFile(ctx)
        if (getAvatarPreset(ctx) == -1 && f.exists()) {
            val bmp = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
            if (bmp != null) {
                iv.setImageBitmap(bmp)
                return
            }
        }
        val preset = getAvatarPreset(ctx)
        if (preset in 1..4) {
            iv.setImageBitmap(makePresetAvatarBitmap(getUserName(ctx), preset))
        } else {
            iv.setImageResource(R.drawable.no_pic)
        }
    }

    fun applyDesktopBgTo(ctx: Context, iv: ImageView) {
        iv.scaleType = ImageView.ScaleType.FIT_XY
        when (getDesktopBgType(ctx)) {
            "CUSTOM" -> {
                val f = customBgFile(ctx)
                val bmp = if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() else null
                if (bmp != null) {
                    iv.setImageBitmap(bmp)
                } else {
                    iv.setImageResource(R.drawable.background_desktop)
                }
            }
            "PRESET_WARM" -> iv.setImageBitmap(makeGradientBg(Color.parseColor("#FFF8E7"), Color.parseColor("#EADBC8")))
            "PRESET_BLUE" -> iv.setImageBitmap(makeGradientBg(Color.parseColor("#E3F2FD"), Color.parseColor("#90CAF9")))
            "PRESET_NIGHT" -> iv.setImageBitmap(makeGradientBg(Color.parseColor("#263238"), Color.parseColor("#111927")))
            else -> iv.setImageResource(R.drawable.background_desktop)
        }
    }

    private fun makeGradientBg(topColor: Int, botColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 480f, topColor, botColor, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, 640f, 480f, p)
        // Subtle desk horizon band so desktop items still feel grounded
        val desk = Paint().apply { color = Color.parseColor("#22000000") }
        c.drawRect(0f, 300f, 640f, 480f, desk)
        return bmp
    }

    fun makePresetAvatarBitmap(name: String, preset: Int): Bitmap {
        val colors = intArrayOf(
            Color.parseColor("#1976D2"),
            Color.parseColor("#00897B"),
            Color.parseColor("#F57C00"),
            Color.parseColor("#7B1FA2")
        )
        val col = colors[(preset - 1).coerceIn(0, colors.lastIndex)]
        val sz = 200
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        c.drawCircle(sz / 2f, sz / 2f, sz / 2f, bg)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#AAFFFFFF")
            strokeWidth = 6f
        }
        c.drawCircle(sz / 2f, sz / 2f, sz / 2f - 5f, ring)
        val ch = name.trim().firstOrNull()?.toString() ?: "学"
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 84f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        tp.getTextBounds(ch, 0, ch.length, bounds)
        c.drawText(ch, sz / 2f, sz / 2f - bounds.exactCenterY(), tp)
        return bmp
    }

    private fun centerCropCircleBitmap(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        val out = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = android.graphics.Path().apply {
            addCircle(120f, 120f, 120f, android.graphics.Path.Direction.CW)
        }
        c.clipPath(path)
        c.drawBitmap(cropped, null, Rect(0, 0, 240, 240), p)
        return out
    }

    private fun decodeUriScaled(ctx: Context, uri: Uri, maxDim: Int): Bitmap? {
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val largest = maxOf(raw.width, raw.height)
        if (largest <= maxDim) return raw
        val ratio = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(raw, (raw.width * ratio).toInt().coerceAtLeast(1), (raw.height * ratio).toInt().coerceAtLeast(1), true)
    }
}
