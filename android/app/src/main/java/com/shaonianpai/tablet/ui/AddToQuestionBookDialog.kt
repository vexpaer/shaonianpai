package com.shaonianpai.tablet.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.shaonianpai.tablet.data.CollectedItem
import com.shaonianpai.tablet.data.QuestionBookStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recreates original dialog_addtoquestionbook.xml:
 * Title, Book/Folder Spinner, Subject Spinner, Selectable Tag Chips, Description & Image Preview.
 */
object AddToQuestionBookDialog {

    fun show(
        activity: Activity,
        sourceType: String,
        defaultTitle: String,
        defaultBook: String = "我的收藏夹",
        defaultTag: String = "",
        bitmap: Bitmap? = null,
        existingImagePath: String = "",
        onDismiss: (() -> Unit)? = null,
        onSaved: ((CollectedItem) -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Top blue banner matching original dialog_addtoquestionbook header
        val header = TextView(activity).apply {
            text = when (sourceType) {
                "SCREENSHOT" -> "截屏存入收藏夹 / 试题本"
                "CAMERA" -> "拍照存入收藏夹 / 试题本"
                "SCRATCHPAD" -> "草稿本页面存入收藏夹"
                else -> "快速添加到收藏夹 / 试题本"
            }
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#1976D2"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        root.addView(header)

        val scroll = ScrollView(activity)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Image preview if available
        val previewBmp = bitmap ?: existingImagePath.takeIf { it.isNotBlank() && File(it).exists() }?.let {
            runCatching { BitmapFactory.decodeFile(it) }.getOrNull()
        }
        if (previewBmp != null) {
            val iv = ImageView(activity).apply {
                setImageBitmap(previewBmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                setPadding(dp(4), dp(4), dp(4), dp(4))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)).apply {
                    bottomMargin = dp(10)
                }
            }
            body.addView(iv)
        }

        // Title input
        body.addView(makeLabel(activity, "标题："))
        val timeHint = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
        val etTitle = EditText(activity).apply {
            setText(defaultTitle.ifBlank { "学习记录 $timeHint" })
            setSingleLine(true)
            textSize = 14f
        }
        body.addView(etTitle)

        // Book & Subject spinners row
        val rowSpinners = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        val colBook = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        }
        colBook.addView(makeLabel(activity, "收藏夹 / 试题本："))
        val spBook = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, QuestionBookStore.BOOKS)
            val idx = QuestionBookStore.BOOKS.indexOf(defaultBook)
            if (idx >= 0) setSelection(idx)
        }
        colBook.addView(spBook)

        val colSub = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        colSub.addView(makeLabel(activity, "科目："))
        val spSubject = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, QuestionBookStore.SUBJECTS)
        }
        colSub.addView(spSubject)

        rowSpinners.addView(colBook)
        rowSpinners.addView(colSub)
        body.addView(rowSpinners)

        // Tag chips
        body.addView(makeLabel(activity, "标签（可多选）：").apply {
            setPadding(0, dp(10), 0, dp(4))
        })
        val selectedTags = mutableSetOf<String>()
        if (defaultTag.isNotBlank()) selectedTags.add(defaultTag)

        val tagScroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
        val tagRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        tagScroll.addView(tagRow)

        fun renderTagChips() {
            tagRow.removeAllViews()
            for (tag in QuestionBookStore.PRESET_TAGS) {
                val active = selectedTags.contains(tag)
                val tv = TextView(activity).apply {
                    text = tag
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    setTextColor(if (active) Color.WHITE else Color.parseColor("#1976D2"))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        if (active) setColor(Color.parseColor("#1976D2")) else {
                            setColor(Color.parseColor("#EEF5FC"))
                            setStroke(dp(1), Color.parseColor("#90CAF9"))
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dp(6)
                    }
                    setOnClickListener {
                        if (active) selectedTags.remove(tag) else selectedTags.add(tag)
                        renderTagChips()
                    }
                }
                tagRow.addView(tv)
            }
        }
        renderTagChips()
        body.addView(tagScroll)

        // Description
        body.addView(makeLabel(activity, "描述 / 错题笔记：").apply {
            setPadding(0, dp(10), 0, dp(4))
        })
        val etDesc = EditText(activity).apply {
            hint = "可补充解题思路、重点总结或课堂备注..."
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            textSize = 14f
        }
        body.addView(etDesc)

        AlertDialog.Builder(activity)
            .setView(root)
            .setPositiveButton("存入收藏夹") { _, _ ->
                val savedPath = when {
                    existingImagePath.isNotBlank() && File(existingImagePath).exists() -> existingImagePath
                    bitmap != null -> QuestionBookStore.saveBitmap(activity, bitmap, sourceType.lowercase())
                    else -> ""
                }
                val item = CollectedItem(
                    title = etTitle.text.toString().trim().ifBlank { "学习收藏 $timeHint" },
                    bookName = spBook.selectedItem?.toString() ?: "我的收藏夹",
                    subject = spSubject.selectedItem?.toString() ?: "通用",
                    tags = selectedTags.toList().ifEmpty { listOf("重点") },
                    description = etDesc.text.toString().trim(),
                    sourceType = sourceType,
                    imagePath = savedPath
                )
                QuestionBookStore.addItem(activity, item)
                Toast.makeText(activity, "已存入「${item.bookName}」", Toast.LENGTH_SHORT).show()
                onSaved?.invoke(item)
            }
            .setNegativeButton("取消", null)
            .setOnDismissListener { onDismiss?.invoke() }
            .show()
    }

    private fun makeLabel(activity: Activity, text: String): TextView = TextView(activity).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#455A64"))
        setTypeface(null, android.graphics.Typeface.BOLD)
    }
}
