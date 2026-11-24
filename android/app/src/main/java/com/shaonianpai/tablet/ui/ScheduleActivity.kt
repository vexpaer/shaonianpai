package com.shaonianpai.tablet.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.Repository

class ScheduleActivity : AppCompatActivity() {

    private val days = listOf("节次", "周一\n09-27", "周二\n09-28", "周三\n09-29", "周四\n09-30", "周五\n10-01")
    private val grid = listOf(
        listOf("第1节\n08:00", "语文", "数学", "英语", "数学", "语文"),
        listOf("第2节\n08:55", "数学", "语文", "数学", "语文 ★", "英语"),
        listOf("第3节\n10:05", "英语", "物理", "语文", "英语", "数学"),
        listOf("第4节\n11:00", "物理", "化学", "历史", "物理", "化学"),
        listOf("第5节\n14:00", "历史", "英语", "化学", "数学 ★", "自习"),
        listOf("第6节\n14:55", "化学", "体育", "物理", "自主学习", "班会")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheduleview)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp)
        toolbar.setNavigationOnClickListener { finish() }

        val table = findViewById<TableLayout>(R.id.tableSchedule)
        val tvSelected = findViewById<TextView>(R.id.tvSelectedLesson)
        val lvRes = findViewById<ListView>(R.id.lvLessonResources)

        val tasks = Repository.builtInFallbackTasks()
        fun showMaterials(label: String, subject: String) {
            tvSelected.text = label
            val matched = tasks.filter { subject.contains(it.subject) }.ifEmpty { tasks.take(2) }
            val items = matched.flatMap { t ->
                listOf("【任务】${t.title}") + t.assets.map { "  • ${it.title}" }
            }
            lvRes.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
            lvRes.setOnItemClickListener { _, _, _, _ ->
                startActivity(Intent(this, TaskDetailActivity::class.java).putExtra("task", matched.first()))
            }
        }

        val d = resources.displayMetrics.density
        // Header row
        val headerRow = TableRow(this)
        for ((idx, day) in days.withIndex()) {
            headerRow.addView(TextView(this).apply {
                text = day
                gravity = Gravity.CENTER
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(if (idx == 4) Color.WHITE else Color.parseColor("#37474F"))
                setBackgroundColor(if (idx == 4) Color.parseColor("#1976D2") else Color.parseColor("#ECEFF1"))
                setPadding((8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt())
            })
        }
        table.addView(headerRow)

        for (rowData in grid) {
            val tr = TableRow(this)
            for ((col, cell) in rowData.withIndex()) {
                val isHighlight = cell.contains("★")
                tr.addView(TextView(this).apply {
                    text = cell
                    gravity = Gravity.CENTER
                    textSize = if (col == 0) 12f else 14f
                    setTextColor(
                        when {
                            isHighlight -> Color.parseColor("#0D47A1")
                            col == 0 -> Color.parseColor("#607D8B")
                            else -> Color.parseColor("#263238")
                        }
                    )
                    setBackgroundColor(
                        when {
                            isHighlight -> Color.parseColor("#BBDEFB")
                            col == 0 -> Color.parseColor("#F5F7FA")
                            else -> Color.WHITE
                        }
                    )
                    if (isHighlight) setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding((8 * d).toInt(), (18 * d).toInt(), (8 * d).toInt(), (18 * d).toInt())
                    layoutParams = TableRow.LayoutParams(
                        TableRow.LayoutParams.WRAP_CONTENT,
                        TableRow.LayoutParams.MATCH_PARENT
                    ).apply { setMargins(1, 1, 1, 1) }
                    if (col > 0) {
                        setOnClickListener {
                            showMaterials("${days[col].replace("\n", " ")} · ${rowData[0].substringBefore("\n")} $cell", cell)
                        }
                    }
                })
            }
            table.addView(tr)
        }
        showMaterials("周四 09-30 · 第2节 语文", "语文")
    }
}
