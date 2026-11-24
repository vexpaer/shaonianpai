package com.shaonianpai.tablet.ui

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shaonianpai.tablet.R

class QuestionBookActivity : AppCompatActivity() {

    private data class WrongQuestion(
        val chapter: String,
        val tag: String,
        val stem: String,
        val myAnswer: String,
        val correctAnswer: String,
        val analysis: String
    )

    private val chapters = listOf(
        "全部错题 (4)",
        "第三单元 · 古诗文 (2)",
        "第一单元 · 现代诗歌 (1)",
        "第二十二章 · 二次函数 (1)"
    )

    private val questions = listOf(
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_questionbook)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val splitContainer = findViewById<LinearLayout>(R.id.splitContainer)
        val splitDivider = findViewById<android.view.View>(R.id.splitDivider)
        val lv = findViewById<ListView>(R.id.listViewSelector)
        val rv = findViewById<RecyclerView>(R.id.recyclerViewItems)

        if (resources.configuration.screenWidthDp < 600) {
            val d = resources.displayMetrics.density
            splitContainer.orientation = LinearLayout.VERTICAL
            lv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (145 * d).toInt())
            splitDivider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (4 * d).toInt())
            rv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = QAdapter(questions)

        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_activated_1, chapters)
        lv.choiceMode = ListView.CHOICE_MODE_SINGLE
        lv.setItemChecked(0, true)
        lv.setOnItemClickListener { _, _, position, _ ->
            val filtered = when (position) {
                1 -> questions.filter { it.chapter.contains("古诗文") }
                2 -> questions.filter { it.chapter.contains("诗歌") }
                3 -> questions.filter { it.chapter.contains("二次函数") }
                else -> questions
            }
            rv.adapter = QAdapter(filtered)
        }
    }

    private inner class QAdapter(private val list: List<WrongQuestion>) :
        RecyclerView.Adapter<QAdapter.VH>() {

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

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val q = list[position]
            holder.box.removeAllViews()
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
