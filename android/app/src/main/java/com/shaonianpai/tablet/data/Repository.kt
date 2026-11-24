package com.shaonianpai.tablet.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.shaonianpai.tablet.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URL

data class AssetItem(
    val assetId: String,
    val title: String,
    val mime: String,
    val bytes: Long,
    val url: String,
    val localSampleName: String = ""
) : Serializable

data class DeliveryTask(
    val deliveryId: String,
    val revision: Int,
    val subject: String,
    val taskType: String,
    val title: String,
    val teacherName: String,
    val startDate: String,
    val endDate: String,
    val groupDate: String,
    val description: String,
    val unreadResourceCount: Int,
    val unansweredCount: Int,
    val progressPercent: Int,
    val sourceName: String,
    val assets: List<AssetItem>
) : Serializable

object Repository {
    private const val PREF_NAME = "shaonianpai_prefs"
    private const val KEY_SERVER = "server_url"
    const val DEFAULT_SERVER = "http://10.0.2.2:8765"

    fun getServerUrl(ctx: Context): String =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SERVER, DEFAULT_SERVER) ?: DEFAULT_SERVER

    fun setServerUrl(ctx: Context, url: String) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SERVER, url.trimEnd('/')).apply()
    }

    fun isFavorite(ctx: Context, assetId: String): Boolean =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean("fav_$assetId", false)

    fun toggleFavorite(ctx: Context, assetId: String): Boolean {
        val next = !isFavorite(ctx, assetId)
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("fav_$assetId", next).apply()
        return next
    }

    fun getSavedPdfPage(ctx: Context, assetId: String): Int =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt("pdf_page_$assetId", 0)

    fun savePdfPage(ctx: Context, assetId: String, page: Int) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt("pdf_page_$assetId", page).apply()
    }

    fun fetchDeliveriesSync(ctx: Context): Pair<List<DeliveryTask>, Boolean> {
        val baseUrl = getServerUrl(ctx)
        try {
            val conn = (URL("$baseUrl/me/deliveries?cursor=0").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 3500
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val root = JSONObject(text)
                val arr = root.getJSONArray("items")
                val list = mutableListOf<DeliveryTask>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val aArr = o.optJSONArray("assets") ?: JSONArray()
                    val assets = mutableListOf<AssetItem>()
                    for (j in 0 until aArr.length()) {
                        val a = aArr.getJSONObject(j)
                        assets.add(
                            AssetItem(
                                assetId = a.getString("assetId"),
                                title = a.getString("title"),
                                mime = a.optString("mime", "application/octet-stream"),
                                bytes = a.optLong("bytes", 0L),
                                url = a.optString("url", "")
                            )
                        )
                    }
                    list.add(
                        DeliveryTask(
                            deliveryId = o.getString("deliveryId"),
                            revision = o.optInt("revision", 1),
                            subject = o.optString("subject", "语文"),
                            taskType = o.optString("taskType", "教师推送资料"),
                            title = o.getString("title"),
                            teacherName = o.optString("teacherName", "陈老师"),
                            startDate = o.optString("startDate", ""),
                            endDate = o.optString("endDate", ""),
                            groupDate = o.optString("groupDate", "2021年09月30日"),
                            description = o.optString("description", ""),
                            unreadResourceCount = o.optInt("unreadResourceCount", 0),
                            unansweredCount = o.optInt("unansweredCount", 0),
                            progressPercent = o.optInt("progressPercent", 0),
                            sourceName = o.optString("sourceName", "本校资源"),
                            assets = assets
                        )
                    )
                }
                if (list.isNotEmpty()) return Pair(list, true)
            }
        } catch (_: Exception) {
        }
        return Pair(builtInFallbackTasks(), false)
    }

    /** Downloads asset from server into cache atomically, or falls back to bundled sample assets. */
    fun resolveAssetFileSync(ctx: Context, asset: AssetItem): File? {
        val cacheDir = File(ctx.cacheDir, "assets").apply { mkdirs() }
        val ext = when {
            asset.mime.contains("pdf") -> ".pdf"
            asset.mime.contains("audio") -> ".mp3"
            asset.mime.contains("video") -> ".mp4"
            asset.mime.contains("image") -> ".png"
            else -> ".bin"
        }
        val dest = File(cacheDir, "${asset.assetId}$ext")
        if (dest.exists() && dest.length() > 0) return dest

        // Try HTTP download from backend first
        if (asset.url.isNotBlank()) {
            try {
                val fullUrl = if (asset.url.startsWith("http")) asset.url else getServerUrl(ctx) + asset.url
                val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 6000
                }
                if (conn.responseCode == 200) {
                    val tmp = File(cacheDir, ".tmp_${asset.assetId}")
                    conn.inputStream.use { inp ->
                        FileOutputStream(tmp).use { out -> inp.copyTo(out) }
                    }
                    tmp.renameTo(dest)
                    return dest
                }
            } catch (_: Exception) {
            }
        }

        // Fallback to bundled assets/samples/
        val sampleName = asset.localSampleName.ifBlank {
            when {
                asset.title.contains("岳阳楼记") && asset.mime.contains("pdf") -> "岳阳楼记_课堂讲义.pdf"
                asset.title.contains("古诗文") -> "古诗文默写与基础练习.pdf"
                asset.title.contains("二次函数") && asset.mime.contains("pdf") -> "二次函数图像与性质专题.pdf"
                asset.title.contains("Unit3") && asset.mime.contains("pdf") -> "Unit3_Reading_Guide.pdf"
                asset.mime.contains("audio") -> "岳阳楼记_名家范读.mp3"
                asset.mime.contains("video") -> "课堂朗诵示范视频.mp4"
                asset.mime.contains("image") -> "岳阳楼记_课堂板书.png"
                else -> "岳阳楼记_课堂讲义.pdf"
            }
        }
        return try {
            ctx.assets.open("samples/$sampleName").use { inp ->
                FileOutputStream(dest).use { out -> inp.copyTo(out) }
            }
            dest
        } catch (_: Exception) {
            null
        }
    }

    fun builtInFallbackTasks(): List<DeliveryTask> = listOf(
        DeliveryTask(
            deliveryId = "local-yuwen-01",
            revision = 1,
            subject = "语文",
            taskType = "教师推送资料",
            title = "复刻测试 01 · 《岳阳楼记》精读课资料",
            teacherName = "陈老师",
            startDate = "2021-09-30 08:30",
            endDate = "2021-10-08 22:00",
            groupDate = "2021年09月30日",
            description = "包含精读讲义 PDF、名家范读 MP3、课堂朗诵示范视频 MP4 与板书图，请依次学习并完成默写。",
            unreadResourceCount = 1,
            unansweredCount = 0,
            progressPercent = 75,
            sourceName = "本校资源",
            assets = listOf(
                AssetItem("a_yyl_pdf", "《岳阳楼记》课堂讲义.pdf", "application/pdf", 452841, "", "岳阳楼记_课堂讲义.pdf"),
                AssetItem("a_yyl_mp3", "《岳阳楼记》名家范读.mp3", "audio/mpeg", 24467, "", "岳阳楼记_名家范读.mp3"),
                AssetItem("a_yyl_mp4", "课堂朗诵示范视频.mp4", "video/mp4", 118816, "", "课堂朗诵示范视频.mp4"),
                AssetItem("a_yyl_png", "《岳阳楼记》课堂板书.png", "image/png", 109853, "", "岳阳楼记_课堂板书.png")
            )
        ),
        DeliveryTask(
            deliveryId = "local-yuwen-02",
            revision = 1,
            subject = "语文",
            taskType = "课后巩固练习",
            title = "第三单元古诗文默写与基础字词巩固",
            teacherName = "陈老师",
            startDate = "2021-09-30 16:00",
            endDate = "2021-10-07 20:00",
            groupDate = "2021年09月30日",
            description = "完成《岳阳楼记》《醉翁亭记》《湖心亭看雪》名句默写练习，对照第二页参考答案订正。",
            unreadResourceCount = 0,
            unansweredCount = 0,
            progressPercent = 100,
            sourceName = "本校资源",
            assets = listOf(
                AssetItem("a_gsw_pdf", "古诗文默写与基础练习.pdf", "application/pdf", 193961, "", "古诗文默写与基础练习.pdf")
            )
        ),
        DeliveryTask(
            deliveryId = "local-yuwen-03",
            revision = 1,
            subject = "语文",
            taskType = "预习导学",
            title = "《醉翁亭记》课前预习与朗读音频",
            teacherName = "陈老师",
            startDate = "2021-09-29 17:30",
            endDate = "2021-10-03 21:00",
            groupDate = "2021年09月29日",
            description = "提前通读全文，结合音频纠正断句节奏。",
            unreadResourceCount = 2,
            unansweredCount = 1,
            progressPercent = 0,
            sourceName = "本校资源",
            assets = listOf(
                AssetItem("a_yyl_mp3", "《岳阳楼记》名家范读.mp3", "audio/mpeg", 24467, "", "岳阳楼记_名家范读.mp3"),
                AssetItem("a_gsw_pdf", "古诗文默写与基础练习.pdf", "application/pdf", 193961, "", "古诗文默写与基础练习.pdf")
            )
        ),
        DeliveryTask(
            deliveryId = "local-math-01",
            revision = 1,
            subject = "数学",
            taskType = "专题微课",
            title = "第二十二章 · 二次函数图像与性质专题",
            teacherName = "李老师",
            startDate = "2021-09-30 14:00",
            endDate = "2021-10-09 22:00",
            groupDate = "2021年09月30日",
            description = "掌握顶点式配方、对称轴及闭区间最值求法，附动态演示视频。",
            unreadResourceCount = 1,
            unansweredCount = 2,
            progressPercent = 50,
            sourceName = "本校资源",
            assets = listOf(
                AssetItem("a_math_pdf", "二次函数图像与性质专题.pdf", "application/pdf", 222230, "", "二次函数图像与性质专题.pdf"),
                AssetItem("a_math_mp4", "二次函数动态演示.mp4", "video/mp4", 118816, "", "二次函数动态演示.mp4")
            )
        ),
        DeliveryTask(
            deliveryId = "local-eng-01",
            revision = 1,
            subject = "英语",
            taskType = "听说读写任务",
            title = "Unit 3 Could you please tell me where the restrooms are?",
            teacherName = "王老师",
            startDate = "2021-09-30 10:00",
            endDate = "2021-10-08 21:00",
            groupDate = "2021年09月30日",
            description = "阅读导学案并跟读配套听力音频，注意宾语从句的陈述语序。",
            unreadResourceCount = 0,
            unansweredCount = 1,
            progressPercent = 60,
            sourceName = "睿易云",
            assets = listOf(
                AssetItem("a_eng_pdf", "Unit3_Reading_Guide.pdf", "application/pdf", 137622, "", "Unit3_Reading_Guide.pdf"),
                AssetItem("a_eng_mp3", "Unit3_听力训练.mp3", "audio/mpeg", 24467, "", "Unit3_听力训练.mp3")
            )
        )
    )

    fun iconResForMime(mime: String): Int = when {
        mime.contains("pdf") -> R.drawable.ic_pdf
        mime.contains("audio") -> R.drawable.ic_audio
        mime.contains("video") -> R.drawable.ic_video
        mime.contains("image") -> R.drawable.ic_picture
        else -> R.drawable.ic_word
    }

    fun typeLabelForMime(mime: String): String = when {
        mime.contains("pdf") -> "PDF文档"
        mime.contains("audio") -> "音频资料"
        mime.contains("video") -> "视频示范"
        mime.contains("image") -> "板书图片"
        else -> "文档资料"
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    /** Generates authentic 2x2 icon collage thumbnail (like reference.jpg) for a task. */
    fun buildTaskThumbnail(ctx: Context, assets: List<AssetItem>): Bitmap {
        val size = 220
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#F2F4F7"))
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E0E4EA")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(1f, 1f, size - 1f, size - 1f, borderPaint)

        if (assets.isEmpty()) return bmp
        if (assets.size == 1) {
            val icon = BitmapFactory.decodeResource(ctx.resources, iconResForMime(assets[0].mime))
            if (icon != null) {
                canvas.drawBitmap(icon, null, Rect(44, 44, size - 44, size - 44), null)
            }
            return bmp
        }

        val half = size / 2
        val pad = 16
        val slots = listOf(
            Rect(pad, pad, half - pad / 2, half - pad / 2),
            Rect(half + pad / 2, pad, size - pad, half - pad / 2),
            Rect(pad, half + pad / 2, half - pad / 2, size - pad),
            Rect(half + pad / 2, half + pad / 2, size - pad, size - pad)
        )
        for (i in 0 until minOf(4, assets.size)) {
            val icon = BitmapFactory.decodeResource(ctx.resources, iconResForMime(assets[i].mime))
            if (icon != null) {
                canvas.drawBitmap(icon, null, slots[i], null)
            }
        }
        return bmp
    }
}
