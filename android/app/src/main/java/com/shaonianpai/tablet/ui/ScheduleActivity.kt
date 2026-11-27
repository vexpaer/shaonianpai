package com.shaonianpai.tablet.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.DeliveryTask
import com.shaonianpai.tablet.data.Repository
import java.util.Calendar

class ScheduleActivity : AppCompatActivity() {

    private lateinit var tvMiniMonthTitle: TextView
    private lateinit var llMiniWeekHeader: LinearLayout
    private lateinit var llMiniCalGrid: LinearLayout
    private lateinit var llDayTasks: LinearLayout
    private lateinit var tvScheduleDateTitle: TextView
    private lateinit var tvScheduleWeekHint: TextView
    private lateinit var llScheduleGridContainer: LinearLayout

    // Default selected date = today (calendar tracks user's selected day & week)
    private val selectedCal: Calendar = Calendar.getInstance()
    private val displayedMonthCal: Calendar = Calendar.getInstance()
    private lateinit var fallbackTasks: List<DeliveryTask>

    // Period 1..8 across Mon..Sun (columns 0..6, periods 0..7)
    private val weekLessons = listOf(
        listOf("语文", "数学", "英语", "数学", "语文", "", ""),
        listOf("数学", "语文", "数学", "语文", "英语", "", ""),
        listOf("英语", "物理", "语文", "英语", "数学", "", ""),
        listOf("物理", "化学", "历史", "物理", "化学", "", ""),
        listOf("历史", "英语", "化学", "数学", "自习", "", ""),
        listOf("化学", "体育", "物理", "自习", "班会", "", ""),
        listOf("", "", "", "", "", "", ""),
        listOf("", "", "", "", "", "", "")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheduleview)
        FloatingQuickBar.attachIfNeeded(this)

        fallbackTasks = Repository.builtInFallbackTasks()
        normalizeToMidnight(selectedCal)
        displayedMonthCal.timeInMillis = selectedCal.timeInMillis
        displayedMonthCal.set(Calendar.DAY_OF_MONTH, 1)

        findViewById<ImageButton>(R.id.btnScheduleBack).setOnClickListener { finish() }
        tvMiniMonthTitle = findViewById(R.id.tvMiniMonthTitle)
        llMiniWeekHeader = findViewById(R.id.llMiniWeekHeader)
        llMiniCalGrid = findViewById(R.id.llMiniCalGrid)
        llDayTasks = findViewById(R.id.llDayTasks)
        tvScheduleDateTitle = findViewById(R.id.tvScheduleDateTitle)
        tvScheduleWeekHint = findViewById(R.id.tvScheduleWeekHint)
        llScheduleGridContainer = findViewById(R.id.llScheduleGridContainer)

        // Responsive stacking on narrow portrait screens
        if (resources.configuration.screenWidthDp < 600) {
            val d = resources.displayMetrics.density
            val container = findViewById<LinearLayout>(R.id.scheduleContainer)
            val leftPanel = findViewById<View>(R.id.scheduleLeftPanel)
            val divider = findViewById<View>(R.id.scheduleDivider)
            val rightPanel = findViewById<View>(R.id.scheduleRightPanel)
            container.orientation = LinearLayout.VERTICAL
            leftPanel.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            divider.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * d).toInt()
            )
            rightPanel.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        buildMiniWeekHeader()

        findViewById<View>(R.id.btnPrevWeek).setOnClickListener {
            selectedCal.add(Calendar.DAY_OF_MONTH, -7)
            syncDisplayedMonthToSelected()
            renderAll()
        }
        findViewById<View>(R.id.btnNextWeek).setOnClickListener {
            selectedCal.add(Calendar.DAY_OF_MONTH, 7)
            syncDisplayedMonthToSelected()
            renderAll()
        }

        renderAll()
    }

    private fun buildMiniWeekHeader() {
        llMiniWeekHeader.removeAllViews()
        val labels = listOf("一", "二", "三", "四", "五", "六", "日")
        for (lbl in labels) {
            llMiniWeekHeader.addView(TextView(this).apply {
                text = lbl
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            })
        }
    }

    private fun syncDisplayedMonthToSelected() {
        displayedMonthCal.set(Calendar.YEAR, selectedCal.get(Calendar.YEAR))
        displayedMonthCal.set(Calendar.MONTH, selectedCal.get(Calendar.MONTH))
        displayedMonthCal.set(Calendar.DAY_OF_MONTH, 1)
    }

    private fun renderAll() {
        renderMiniCalendar()
        renderDayTasks()
        renderRightScheduleGrid()
    }

    private fun renderMiniCalendar() {
        val yr = displayedMonthCal.get(Calendar.YEAR)
        val mo = displayedMonthCal.get(Calendar.MONTH) + 1
        tvMiniMonthTitle.text = "${yr}年${mo}月"

        val selYear = selectedCal.get(Calendar.YEAR)
        val selMonth = selectedCal.get(Calendar.MONTH)
        val selDay = selectedCal.get(Calendar.DAY_OF_MONTH)

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        // Find Monday of selected week for subtle gray row highlight
        val weekStart = mondayOfWeek(selectedCal)
        val weekEnd = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }

        val cal = (displayedMonthCal.clone() as Calendar)
        val dowMon0 = mondayIndex(cal) // 0=Mon .. 6=Sun
        cal.add(Calendar.DAY_OF_MONTH, -dowMon0)

        llMiniCalGrid.removeAllViews()
        val rowHeight = dp(if (resources.configuration.screenWidthDp < 600) 28 else 36)

        for (row in 0 until 6) {
            val rowStart = cal.timeInMillis
            val rowEnd = rowStart + 6L * 86_400_000L
            val isSelectedWeekRow = weekStart.timeInMillis in rowStart..rowEnd ||
                weekEnd.timeInMillis in rowStart..rowEnd

            val weekRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)
                setBackgroundColor(if (isSelectedWeekRow) Color.parseColor("#E6E6E6") else Color.TRANSPARENT)
            }

            for (col in 0 until 7) {
                val cellCal = (cal.clone() as Calendar)
                normalizeToMidnight(cellCal)
                val inCurMonth = cellCal.get(Calendar.YEAR) == displayedMonthCal.get(Calendar.YEAR) &&
                    cellCal.get(Calendar.MONTH) == displayedMonthCal.get(Calendar.MONTH)
                val isSelectedDay = cellCal.get(Calendar.YEAR) == selYear &&
                    cellCal.get(Calendar.MONTH) == selMonth &&
                    cellCal.get(Calendar.DAY_OF_MONTH) == selDay

                val tv = TextView(this).apply {
                    text = cellCal.get(Calendar.DAY_OF_MONTH).toString()
                    gravity = Gravity.CENTER
                    textSize = 11f
                    setTextColor(
                        when {
                            isSelectedDay -> Color.parseColor("#111111")
                            inCurMonth -> Color.parseColor("#222222")
                            else -> Color.parseColor("#B0B0B0")
                        }
                    )
                    if (isSelectedDay) {
                        setTypeface(null, Typeface.BOLD)
                        background = GradientDrawable().apply {
                            setColor(Color.WHITE)
                            setStroke(dp(2), Color.parseColor("#212121"))
                        }
                    } else {
                        background = null
                    }
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        setMargins(dp(1), dp(2), dp(1), dp(2))
                    }
                    setOnClickListener {
                        selectedCal.timeInMillis = cellCal.timeInMillis
                        syncDisplayedMonthToSelected()
                        renderAll()
                    }
                }
                weekRow.addView(tv)
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            llMiniCalGrid.addView(weekRow)
        }
    }

    private fun renderDayTasks() {
        llDayTasks.removeAllViews()
        val dowIdx = mondayIndex(selectedCal) // 0=Mon..6=Sun
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        // Show subject tasks linked to that weekday; Sat/Sun show empty state matching original
        val subjectsToday = weekLessons.mapNotNull { it[dowIdx].takeIf { s -> s.isNotBlank() } }.distinct()
        val tasksForDay = if (dowIdx <= 4) {
            fallbackTasks.filter { t -> subjectsToday.any { s -> s == t.subject || s.contains(t.subject) } }
        } else {
            emptyList()
        }

        if (tasksForDay.isEmpty()) {
            llDayTasks.addView(TextView(this).apply {
                text = "当前没有任务需要完成"
                textSize = 15f
                setTextColor(Color.parseColor("#212121"))
                setPadding(dp(2), dp(8), dp(2), dp(8))
            })
            return
        }

        for (task in tasksForDay) {
            llDayTasks.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
                setOnClickListener {
                    startActivity(Intent(this@ScheduleActivity, TaskDetailActivity::class.java).putExtra("task", task))
                }
                addView(TextView(this@ScheduleActivity).apply {
                    text = "${task.subject} · ${task.title}"
                    textSize = 13.5f
                    setTextColor(Color.parseColor("#212121"))
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(TextView(this@ScheduleActivity).apply {
                    text = "${task.teacherName} · ${task.assets.size}个学习资料"
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#757575"))
                    setPadding(0, dp(2), 0, 0)
                })
            })
            llDayTasks.addView(View(this).apply {
                setBackgroundColor(Color.parseColor("#EEEEEE"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            })
        }
    }

    private fun renderRightScheduleGrid() {
        val yr = selectedCal.get(Calendar.YEAR)
        val mo = selectedCal.get(Calendar.MONTH) + 1
        val day = selectedCal.get(Calendar.DAY_OF_MONTH)
        val weekOfTerm = ((selectedCal.get(Calendar.WEEK_OF_YEAR) - 1) % 20) + 1
        tvScheduleDateTitle.text = "${yr}年${mo}月${day}日"
        tvScheduleWeekHint.text = "第${weekOfTerm}周 · 点击课程查看资料"

        val activeDayCol = mondayIndex(selectedCal) // 0=Mon..6=Sun
        val dayNames = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val borderColor = Color.parseColor("#C2C2C2")
        val normalHeaderBg = Color.parseColor("#EAEAEA")
        val normalCellBg = Color.parseColor("#E0E0E0")
        val activeColBg = Color.parseColor("#3CB4E5")
        val gutterWidth = dp(24)

        llScheduleGridContainer.removeAllViews()

        // Top border
        llScheduleGridContainer.addView(View(this).apply {
            setBackgroundColor(borderColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        })

        // Header row: empty gutter + 星期一..星期日
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32))
        }
        headerRow.addView(View(this).apply {
            setBackgroundColor(borderColor)
            layoutParams = LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT)
        })
        headerRow.addView(View(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(gutterWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        })
        for (col in 0 until 7) {
            val selected = col == activeDayCol
            headerRow.addView(View(this).apply {
                setBackgroundColor(borderColor)
                layoutParams = LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT)
            })
            headerRow.addView(TextView(this).apply {
                text = dayNames[col]
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(if (selected) Color.parseColor("#111111") else Color.parseColor("#424242"))
                if (selected) setTypeface(null, Typeface.BOLD)
                setBackgroundColor(if (selected) Color.WHITE else normalHeaderBg)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    val mon = mondayOfWeek(selectedCal)
                    mon.add(Calendar.DAY_OF_MONTH, col)
                    selectedCal.timeInMillis = mon.timeInMillis
                    syncDisplayedMonthToSelected()
                    renderAll()
                }
            })
        }
        headerRow.addView(View(this).apply {
            setBackgroundColor(borderColor)
            layoutParams = LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT)
        })
        llScheduleGridContainer.addView(headerRow)

        llScheduleGridContainer.addView(View(this).apply {
            setBackgroundColor(borderColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        })

        // 8 period rows dividing vertical height cleanly without scrolling
        for (period in 0 until 8) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            rowLayout.addView(View(this).apply {
                setBackgroundColor(borderColor)
                layoutParams = LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT)
            })
            // Period number top-aligned inside narrow gutter
            rowLayout.addView(TextView(this).apply {
                text = (period + 1).toString()
                gravity = Gravity.TOP or Gravity.END
                textSize = 13f
                setPadding(0, dp(2), dp(4), 0)
                setTextColor(Color.parseColor("#555555"))
                setBackgroundColor(normalHeaderBg)
                layoutParams = LinearLayout.LayoutParams(gutterWidth, ViewGroup.LayoutParams.MATCH_PARENT)
            })

            for (col in 0 until 7) {
                val selected = col == activeDayCol
                val subject = weekLessons[period][col]
                rowLayout.addView(View(this).apply {
                    setBackgroundColor(borderColor)
                    layoutParams = LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT)
                })
                rowLayout.addView(TextView(this).apply {
                    text = subject
                    gravity = Gravity.CENTER
                    textSize = 13f
                    setTextColor(if (selected) Color.WHITE else Color.parseColor("#37474F"))
                    if (selected && subject.isNotEmpty()) setTypeface(null, Typeface.BOLD)
                    setBackgroundColor(if (selected) activeColBg else normalCellBg)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    setOnClickListener {
                        val mon = mondayOfWeek(selectedCal)
                        mon.add(Calendar.DAY_OF_MONTH, col)
                        selectedCal.timeInMillis = mon.timeInMillis
                        syncDisplayedMonthToSelected()
                        renderAll()
                        if (subject.isNotEmpty()) {
                            val matched = fallbackTasks.firstOrNull { subject.contains(it.subject) }
                            if (matched != null) {
                                startActivity(Intent(this@ScheduleActivity, TaskDetailActivity::class.java).putExtra("task", matched))
                            }
                        }
                    }
                })
            }
            rowLayout.addView(View(this).apply {
                setBackgroundColor(borderColor)
                layoutParams = LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT)
            })
            llScheduleGridContainer.addView(rowLayout)

            llScheduleGridContainer.addView(View(this).apply {
                setBackgroundColor(borderColor)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            })
        }
    }

    private fun mondayIndex(cal: Calendar): Int = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        else -> 6
    }

    private fun mondayOfWeek(cal: Calendar): Calendar {
        return (cal.clone() as Calendar).apply {
            normalizeToMidnight(this)
            add(Calendar.DAY_OF_MONTH, -mondayIndex(this))
        }
    }

    private fun normalizeToMidnight(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }
}
