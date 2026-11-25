package com.shaonianpai.tablet.data

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class CollectedItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val bookName: String,     // 我的收藏夹 / 错题本 / 课堂笔记 / 草稿本存档
    val subject: String,      // 语文 / 数学 / 英语 / 物理 / 化学 / 通用
    val tags: List<String>,
    val description: String,
    val sourceType: String,   // SCREENSHOT / CAMERA / SCRATCHPAD / NOTE
    val imagePath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

object QuestionBookStore {
    private const val FILE_NAME = "questionbook_items.json"

    val BOOKS = listOf("我的收藏夹", "错题本", "课堂笔记", "草稿本存档")
    val SUBJECTS = listOf("语文", "数学", "英语", "物理", "化学", "通用")
    val PRESET_TAGS = listOf("重点", "易错", "必背", "截屏", "拍照", "草稿", "课堂板书", "待复习")

    private fun jsonFile(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    fun mediaDir(ctx: Context): File = File(ctx.filesDir, "questionbook_media").apply { mkdirs() }

    fun saveBitmap(ctx: Context, bitmap: Bitmap, prefix: String): String {
        val out = File(mediaDir(ctx), "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(out).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, fos)
        }
        return out.absolutePath
    }

    fun loadAll(ctx: Context): MutableList<CollectedItem> {
        val f = jsonFile(ctx)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            val out = mutableListOf<CollectedItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tags = mutableListOf<String>()
                val tArr = o.optJSONArray("tags") ?: JSONArray()
                for (j in 0 until tArr.length()) tags.add(tArr.getString(j))
                out.add(
                    CollectedItem(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        title = o.optString("title", "未命名收藏"),
                        bookName = o.optString("bookName", "我的收藏夹"),
                        subject = o.optString("subject", "通用"),
                        tags = tags,
                        description = o.optString("description", ""),
                        sourceType = o.optString("sourceType", "NOTE"),
                        imagePath = o.optString("imagePath", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            out
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun writeAll(ctx: Context, items: List<CollectedItem>) {
        val arr = JSONArray()
        for (it in items) {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("title", it.title)
            o.put("bookName", it.bookName)
            o.put("subject", it.subject)
            o.put("tags", JSONArray(it.tags))
            o.put("description", it.description)
            o.put("sourceType", it.sourceType)
            o.put("imagePath", it.imagePath)
            o.put("createdAt", it.createdAt)
            arr.put(o)
        }
        jsonFile(ctx).writeText(arr.toString(2))
    }

    fun addItem(ctx: Context, item: CollectedItem) {
        val list = loadAll(ctx)
        list.add(0, item)
        writeAll(ctx, list)
    }

    fun deleteItem(ctx: Context, id: String) {
        val list = loadAll(ctx)
        val removed = list.filter { it.id == id }
        removed.forEach { if (it.imagePath.isNotBlank()) runCatching { File(it.imagePath).delete() } }
        list.removeAll { it.id == id }
        writeAll(ctx, list)
    }
}
