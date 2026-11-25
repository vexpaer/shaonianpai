package com.shaonianpai.tablet.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.CollectedItem
import com.shaonianpai.tablet.data.QuestionBookStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuestionBookActivity : AppCompatActivity() {

    private data class WrongQuestion(
        val chapter: String,
        val tag: String,
        val stem: String,
        val myAnswer: String,
        val correctAnswer: String,
        val analysis: String
    )

    private sealed class DisplayEntry {
        data class Collected(val item: CollectedItem) : DisplayEntry()
        data class Sample(val q: WrongQuestion) : DisplayEntry()
    }

    private val sampleQuestions = listOf(
        WrongQuestion(
            "第三单元 · 古诗文",
            "文言文实词 · 易错",
            "1. 下列加点字注音和解释完全正确的一项是（ ）\nA. 属予作文以记之（zhǔ，同“嘱”，嘱托）\nB. 岸芷汀兰（dīng，小洲）\nC. 浩浩汤汤（tāng，水势浩大）",
            "B",
            "A",
            "解析：“汀”应读 tīng；“汤汤”在描写水势时读 shāng。"
        ),
        WrongQuestion(
            "第三单元 · 古诗文",
            "古诗文默写",
            "2. 《岳阳楼记》中动静结合描写洞庭湖月夜美景的句子是：________________，________________。",
            "浮光跃金，静影沉璧（写错“璧”字）",
            "浮光跃金，静影沉璧",
            "解析：“璧”为玉字底，指圆形平玉，不可写作“壁”。"
        ),
        WrongQuestion(
            "第一单元 · 现代诗歌",
            "修辞赏析",
            "3. 《沁园春·雪》中“山舞银蛇，原驰蜡象”运用了什么修辞手法，表达了怎样的气魄？",
            "仅答比喻，漏答化静为动",
            "比喻、对偶、化静为动",
            "解析：以动态的“舞”“驰”写静态群山高原，展现雄浑壮阔之势。"
        ),
        WrongQuestion(
            "第二十二章 · 二次函数",
            "闭区间最值",
            "4. 已知抛物线 y = x² - 4x + 3，当 0 ≤ x ≤ 3 时，求函数 y 的取值范围。",
            "-1 ≤ y ≤ 0",
            "-1 ≤ y ≤ 3",
            "解析：对称轴 x = 2 在区间内，最小值在 x=2 处取 -1；端点 x=0 处 y=3 大于 x=3 处 y=0。"
        )
    )

    private lateinit var lv: ListView
    private lateinit var rv: RecyclerView
    private var selectedCategory = 0
    private var collectedItems: MutableList<CollectedItem> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_questionbook)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val splitContainer = findViewById<LinearLayout>(R.id.splitContainer)
        val splitDivider = findViewById<android.view.View>(R.id.splitDivider)
        lv = findViewById(R.id.listViewSelector)
        rv = findViewById(R.id.recyclerViewItems)

        if (resources.configuration.screenWidthDp < 600) {
            val d = resources.displayMetrics.density
            splitContainer.orientation = LinearLayout.VERTICAL
            lv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (145 * d).toInt())
            splitDivider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (4 * d).toInt())
            rv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        rv.layoutManager = LinearLayoutManager(this)
        if (intent.getStringExtra("initial_tab") == "favorites") {
            selectedCategory = 1
        }
        reloadData()
    }

    override fun onResume() {
        super.onResume()
        reloadData()
    }

    private fun reloadData() {
        collectedItems = QuestionBookStore.loadAll(this)
        val favCount = collectedItems.count { it.bookName != "草稿本存档" }
        val scratchCount = collectedItems.count { it.bookName == "草稿本存档" }
        val total = collectedItems.size + sampleQuestions.size

        val categories = listOf(
            "全部收藏与错题 ($total)",
            "我的收藏夹 ($favCount)",
            "草稿本存档 ($scratchCount)",
            "第三单元 · 古诗文 (2)",
            "第一单元 · 现代诗歌 (1)",
            "第二十二章 · 二次函数 (1)"
        )
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_activated_1, categories)
        lv.choiceMode = ListView.CHOICE_MODE_SINGLE
        lv.setItemChecked(selectedCategory.coerceIn(0, categories.lastIndex), true)
        lv.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = position
            applyFilter()
        }
        applyFilter()
    }

    private fun applyFilter() {
        val list: List<DisplayEntry> = when (selectedCategory) {
            1 -> collectedItems.filter { it.bookName != "草稿本存档" }.map { DisplayEntry.Collected(it) }
            2 -> collectedItems.filter { it.bookName == "草稿本存档" }.map { DisplayEntry.Collected(it) }
            3 -> sampleQuestions.filter { it.chapter.contains("古诗文") }.map { DisplayEntry.Sample(it) }
            4 -> sampleQuestions.filter { it.chapter.contains("诗歌") }.map { DisplayEntry.Sample(it) }
            5 -> sampleQuestions.filter { it.chapter.contains("二次函数") }.map { DisplayEntry.Sample(it) }
            else -> collectedItems.map { DisplayEntry.Collected(it) } + sampleQuestions.map { DisplayEntry.Sample(it) }
        }
        rv.adapter = EntryAdapter(list)
    }

    private fun showFullImage(path: String, title: String) {
        val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return
        val iv = ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#212121"))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(iv)
            .setPositiveButton("关闭", null)
            .show()
    }

    private inner class EntryAdapter(private val list: List<DisplayEntry>) :
        RecyclerView.Adapter<EntryAdapter.VH>() {

        inner class VH(val card: CardView, val box: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val d = resources.displayMetrics.density
            val card = CardView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (12 * d).toInt()) }
                radius = 8 * d
                cardElevation = 2 * d
                setCardBackgroundColor(Color.WHITE)
            }
            val box = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            }
            card.addView(box)
            return VH(card, box)
        }

        override fun getItemCount(): Int = list.size.coerceAtLeast(1)

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.box.removeAllViews()
            if (list.isEmpty()) {
                holder.box.addView(TextView(this@QuestionBookActivity).apply {
                    text = "此分类暂无收藏内容。\n点击悬浮按钮展开「截屏 / 拍照 / 草稿本 / 速记」即可随时存入！"
                    textSize = 14f
                    setTextColor(Color.parseColor("#757575"))
                    gravity = Gravity.CENTER
                    setPadding(12, 32, 12, 32)
                })
                return
            }
            val d = resources.displayMetrics.density
            when (val entry = list[position]) {
                is DisplayEntry.Collected -> {
                    val item = entry.item
                    val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.createdAt))
                    val headerRow = LinearLayout(this@QuestionBookActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    headerRow.addView(TextView(this@QuestionBookActivity).apply {
                        text = "${item.bookName} · ${item.subject} · [${item.tags.joinToString("/")}]  $timeStr"
                        textSize = 12.5f
                        setTextColor(Color.parseColor("#1976D2"))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    headerRow.addView(TextView(this@QuestionBookActivity).apply {
                        text = "删除"
                        textSize = 12f
                        setTextColor(Color.parseColor("#D32F2F"))
                        setPadding((8 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
                        setOnClickListener {
                            QuestionBookStore.deleteItem(this@QuestionBookActivity, item.id)
                            reloadData()
                        }
                    })
                    holder.box.addView(headerRow)

                    holder.box.addView(TextView(this@QuestionBookActivity).apply {
                        text = item.title
                        textSize = 15.5f
                        setTextColor(Color.parseColor("#212121"))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setPadding(0, (6 * d).toInt(), 0, (4 * d).toInt())
                    })

                    if (item.imagePath.isNotBlank() && File(item.imagePath).exists()) {
                        val bmp = runCatching { BitmapFactory.decodeFile(item.imagePath) }.getOrNull()
                        if (bmp != null) {
                            holder.box.addView(ImageView(this@QuestionBookActivity).apply {
                                setImageBitmap(bmp)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setBackgroundColor(Color.parseColor("#F5F5F5"))
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (190 * d).toInt()).apply {
                                    topMargin = (4 * d).toInt()
                                    bottomMargin = (6 * d).toInt()
                                }
                                setOnClickListener { showFullImage(item.imagePath, item.title) }
                            })
                        }
                    }

                    if (item.description.isNotBlank()) {
                        holder.box.addView(TextView(this@QuestionBookActivity).apply {
                            text = item.description
                            textSize = 14f
                            setTextColor(Color.parseColor("#455A64"))
                            setPadding(0, (2 * d).toInt(), 0, (4 * d).toInt())
                        })
                    }
                }
                is DisplayEntry.Sample -> {
                    val q = entry.q
                    fun addTv(text: String, sizeSp: Float, colorHex: String, bold: Boolean = false) {
                        holder.box.addView(TextView(this@QuestionBookActivity).apply {
                            this.text = text
                            textSize = sizeSp
                            setTextColor(Color.parseColor(colorHex))
                            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                            setPadding(0, 3, 0, 5)
                        })
                    }
                    addTv("${q.chapter}  ·  [${q.tag}]", 13f, "#1976D2", true)
                    addTv(q.stem, 15f, "#212121", false)
                    addTv("我的作答：${q.myAnswer}    |    正确答案：${q.correctAnswer}", 14f, "#C62828", true)
                    addTv(q.analysis, 13.5f, "#455A64", false)
                }
            }
        }
    }
}
