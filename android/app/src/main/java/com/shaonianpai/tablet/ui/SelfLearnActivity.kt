package com.shaonianpai.tablet.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.DeliveryTask
import com.shaonianpai.tablet.data.Repository
import com.shaonianpai.tablet.data.UserProfileStore

/**
 * Matches original 20_自主学习_语文.png & 20b_自主学习_科目抽屉.png:
 * - Blue toolbar with white hamburger icon, title "自主学习 - 语文", search icon + 来源 / 显示设置 / 分组设置 / 时间设置.
 * - Left white subject drawer with blue outline subject icons (math, ABC eng, atom science, book).
 * - Functional grouping (日期分组 / 类型分组 / 状态分组), list/grid display, time/source/search filters.
 */
class SelfLearnActivity : AppCompatActivity() {

    private val subjects = listOf("语文", "数学", "英语", "物理", "化学", "历史", "全部学科")
    private var currentSubject = "语文"
    private var isGridMode = false
    private var groupMode = "date" // "date", "type", "state"
    private var searchQuery = ""
    private val collapsedGroups = mutableSetOf<String>()
    private var allTasks: List<DeliveryTask> = emptyList()

    private lateinit var drawer: DrawerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var recycler: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvNoData: TextView
    private lateinit var lvSubjects: ListView
    private var searchBar: LinearLayout? = null

    sealed class RowItem {
        data class GroupHeader(val title: String, val count: Int, val collapsed: Boolean) : RowItem()
        data class TaskRow(val task: DeliveryTask) : RowItem()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selflearnview)

        currentSubject = intent.getStringExtra("subject") ?: "语文"
        isGridMode = intent.getBooleanExtra("grid_mode", false)

        drawer = findViewById(R.id.drawer)
        toolbar = findViewById(R.id.toolbar)
        toolbar.setBackgroundColor(UserProfileStore.getThemePrimaryColor(this))
        recycler = findViewById(R.id.LayoutContent)
        swipeRefresh = findViewById(R.id.swipeRefreshLayout)
        tvNoData = findViewById(R.id.tv_nodata)

        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_menu_white_24dp)
        toolbar.setNavigationOnClickListener { drawer.openDrawer(GravityCompat.START) }
        updateTitle()

        injectSearchBar()

        lvSubjects = findViewById(R.id.rv_subjects)
        lvSubjects.divider = null
        lvSubjects.adapter = SubjectDrawerAdapter()
        lvSubjects.setOnItemClickListener { _, _, position, _ ->
            currentSubject = subjects[position]
            updateTitle()
            (lvSubjects.adapter as? BaseAdapter)?.notifyDataSetChanged()
            drawer.closeDrawer(GravityCompat.START)
            renderRows()
        }

        swipeRefresh.setOnRefreshListener { loadTasks() }
        loadTasks()

        if (intent.getBooleanExtra("open_drawer", false)) {
            drawer.post { drawer.openDrawer(GravityCompat.START) }
        }
    }

    private fun injectSearchBar() {
        val parent = swipeRefresh.parent as? ViewGroup ?: return
        if (parent is android.widget.RelativeLayout) {
            val d = resources.displayMetrics.density
            val bar = LinearLayout(this).apply {
                id = View.generateViewId()
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding((12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
                visibility = View.GONE
            }
            val et = EditText(this).apply {
                hint = "搜索任务标题或知识点..."
                textSize = 13.5f
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        searchQuery = s?.toString()?.trim().orEmpty()
                        renderRows()
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
            val btnClose = TextView(this).apply {
                text = "收起"
                setTextColor(Color.parseColor("#1976D2"))
                setPadding((10 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
                setOnClickListener {
                    et.setText("")
                    searchQuery = ""
                    bar.visibility = View.GONE
                    renderRows()
                }
            }
            bar.addView(et)
            bar.addView(btnClose)
            val barLp = android.widget.RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(android.widget.RelativeLayout.BELOW, toolbar.id)
            }
            parent.addView(bar, barLp)
            (swipeRefresh.layoutParams as? android.widget.RelativeLayout.LayoutParams)?.let { slp ->
                slp.addRule(android.widget.RelativeLayout.BELOW, bar.id)
                swipeRefresh.layoutParams = slp
            }
            searchBar = bar
        }
    }

    private fun updateTitle() {
        supportActionBar?.title = "自主学习 - $currentSubject"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_selflearn, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> {
                searchBar?.let {
                    it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
                return true
            }
            R.id.action_listmode -> {
                isGridMode = false
                item.isChecked = true
                renderRows()
                return true
            }
            R.id.action_gridmode -> {
                isGridMode = true
                item.isChecked = true
                renderRows()
                return true
            }
            R.id.action_group_date -> {
                groupMode = "date"; item.isChecked = true; collapsedGroups.clear(); renderRows(); return true
            }
            R.id.action_group_type -> {
                groupMode = "type"; item.isChecked = true; collapsedGroups.clear(); renderRows(); return true
            }
            R.id.action_group_state -> {
                groupMode = "state"; item.isChecked = true; collapsedGroups.clear(); renderRows(); return true
            }
            R.id.src_all, R.id.src_school, R.id.src_cloud,
            R.id.action_last_week, R.id.action_last_month, R.id.action_last_4month, R.id.action_last_year, R.id.action_all -> {
                item.isChecked = true
                renderRows()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadTasks() {
        swipeRefresh.isRefreshing = true
        Thread {
            val (tasks, _) = Repository.fetchDeliveriesSync(this)
            runOnUiThread {
                allTasks = tasks
                swipeRefresh.isRefreshing = false
                renderRows()
            }
        }.start()
    }

    private fun renderRows() {
        val filtered = allTasks.filter { t ->
            val okSubj = currentSubject == "全部学科" || t.subject == currentSubject
            val okSearch = searchQuery.isEmpty() ||
                t.title.contains(searchQuery, ignoreCase = true) ||
                t.description.contains(searchQuery, ignoreCase = true)
            okSubj && okSearch
        }

        if (filtered.isEmpty()) {
            tvNoData.visibility = View.VISIBLE
            recycler.adapter = null
            return
        }
        tvNoData.visibility = View.GONE

        val grouped = linkedMapOf<String, MutableList<DeliveryTask>>()
        for (t in filtered) {
            val key = when (groupMode) {
                "type" -> t.taskType.ifBlank { "导学任务" }
                "state" -> if (t.progressPercent >= 100) "已完成" else "进行中"
                else -> t.groupDate
            }
            grouped.getOrPut(key) { mutableListOf() }.add(t)
        }

        val rows = mutableListOf<RowItem>()
        for ((key, list) in grouped) {
            val collapsed = collapsedGroups.contains(key)
            rows.add(RowItem.GroupHeader(key, list.size, collapsed))
            if (!collapsed) {
                for (t in list) rows.add(RowItem.TaskRow(t))
            }
        }

        val isNarrow = resources.configuration.screenWidthDp < 600
        if (isGridMode) {
            val cols = if (isNarrow) 2 else 3
            val glm = GridLayoutManager(this, cols)
            glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (rows[position] is RowItem.GroupHeader) cols else 1
            }
            recycler.layoutManager = glm
        } else {
            recycler.layoutManager = LinearLayoutManager(this)
        }

        recycler.adapter = SelfLearnAdapter(rows, isNarrow)
    }

    /** Authentic left subject drawer adapter matching 20b_自主学习_科目抽屉.png */
    private inner class SubjectDrawerAdapter : BaseAdapter() {
        override fun getCount(): Int = subjects.size
        override fun getItem(position: Int): Any = subjects[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val d = resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            val subj = subjects[position]
            val selected = subj == currentSubject

            val row = LinearLayout(this@SelfLearnActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(13), dp(16), dp(13))
                setBackgroundColor(if (selected) Color.parseColor("#E3F2FD") else Color.WHITE)
            }
            val iconRes = when (subj) {
                "数学" -> R.drawable.ic_subj_math
                "英语" -> R.drawable.ic_subj_eng
                "物理", "化学", "全部学科" -> R.drawable.ic_subj_science
                else -> R.drawable.ic_subj_book
            }
            row.addView(ImageView(this@SelfLearnActivity).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(14) }
            })
            row.addView(TextView(this@SelfLearnActivity).apply {
                text = subj
                textSize = 16f
                setTextColor(if (selected) Color.parseColor("#1976D2") else Color.parseColor("#212121"))
            })
            return row
        }
    }

    private inner class SelfLearnAdapter(
        private val rows: List<RowItem>,
        private val isNarrow: Boolean
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is RowItem.GroupHeader -> 0
            is RowItem.TaskRow -> if (isGridMode) 2 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> GroupVH(inf.inflate(R.layout.listitem_newlook_recyclerview_group, parent, false))
                2 -> GridVH(inf.inflate(R.layout.layout_customselflearnitemview2_grid, parent, false))
                else -> {
                    val v = inf.inflate(R.layout.layout_customselflearnitemview2_list, parent, false)
                    if (isNarrow) {
                        val d = parent.resources.displayMetrics.density
                        v.findViewById<ImageView>(R.id.iv_thumbnail).apply {
                            layoutParams = layoutParams.apply {
                                width = (84 * d).toInt()
                                height = (84 * d).toInt()
                            }
                            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
                        }
                    }
                    ListVH(v)
                }
            }
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = rows[position]) {
                is RowItem.GroupHeader -> {
                    val vh = holder as GroupVH
                    vh.tvName.text = item.title
                    vh.tvCount.text = "共计${item.count}项"
                    vh.tvExpand.text = if (item.collapsed) "展开" else "折叠"
                    vh.itemView.setOnClickListener {
                        if (collapsedGroups.contains(item.title)) collapsedGroups.remove(item.title)
                        else collapsedGroups.add(item.title)
                        renderRows()
                    }
                }
                is RowItem.TaskRow -> {
                    val t = item.task
                    val thumb = Repository.buildTaskThumbnail(this@SelfLearnActivity, t.assets)
                    if (holder is ListVH) {
                        val narrow = resources.configuration.screenWidthDp < 520
                        val d = resources.displayMetrics.density
                        val thumbPx = ((if (narrow) 88 else 130) * d).toInt()
                        if (holder.ivThumb.layoutParams.width != thumbPx) {
                            holder.ivThumb.layoutParams = holder.ivThumb.layoutParams.apply {
                                width = thumbPx
                                height = thumbPx
                            }
                            val pad = ((if (narrow) 6 else 10) * d).toInt()
                            holder.ivThumb.setPadding(pad, pad, pad, pad)
                        }
                        val ringPx = ((if (narrow) 52 else 64) * d).toInt()
                        if (holder.progressContainer.layoutParams.width != ringPx) {
                            holder.progressContainer.layoutParams = holder.progressContainer.layoutParams.apply {
                                width = ringPx
                                height = ringPx
                            }
                            holder.tvProgress.textSize = if (narrow) 10.5f else 12.5f
                        }

                        holder.ivThumb.setImageBitmap(thumb)
                        holder.tvTitle.text = t.title
                        holder.ivUnread.visibility = if (t.unreadResourceCount > 0) View.VISIBLE else View.GONE
                        val shortEnd = if (t.startDate.take(4) == t.endDate.take(4) && t.endDate.length > 5) {
                            t.endDate.substring(5)
                        } else t.endDate
                        holder.tvDateRange.text = if (narrow) {
                            "${t.startDate} 至 $shortEnd"
                        } else {
                            "${t.startDate}  到  ${t.endDate}"
                        }
                        holder.tvMeta.text = "${t.taskType} | 未读${t.unreadResourceCount}项 · 未作答${t.unansweredCount}题 · ${t.teacherName}"
                        holder.tvDesc.text = t.description
                        holder.progressView.progress = t.progressPercent.toFloat()
                        holder.tvProgress.text = "${t.progressPercent}%"
                        holder.itemView.setOnClickListener { openTask(t) }
                    } else if (holder is GridVH) {
                        holder.ivThumb.setImageBitmap(thumb)
                        holder.tvTitle.text = t.title
                        holder.ivUnread.visibility = if (t.unreadResourceCount > 0) View.VISIBLE else View.GONE
                        holder.tvRange.text = "${t.startDate.take(10)} 到 ${t.endDate.take(10)}"
                        holder.tvMeta.text = "${t.taskType} · ${t.assets.size} 份资料 · ${t.progressPercent}%"
                        holder.pbProgress.progress = t.progressPercent
                        holder.itemView.setOnClickListener { openTask(t) }
                    }
                }
            }
        }

        private fun openTask(task: DeliveryTask) {
            startActivity(Intent(this@SelfLearnActivity, TaskDetailActivity::class.java).apply {
                putExtra("task", task)
            })
        }
    }

    private class GroupVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.textViewGroupName)
        val tvCount: TextView = v.findViewById(R.id.textViewGroupCount)
        val tvExpand: TextView = v.findViewById(R.id.textViewGroupExpand)
    }

    private class ListVH(v: View) : RecyclerView.ViewHolder(v) {
        val ivThumb: ImageView = v.findViewById(R.id.iv_thumbnail)
        val tvTitle: TextView = v.findViewById(R.id.tv_title)
        val ivUnread: ImageView = v.findViewById(R.id.iv_unread)
        val tvDateRange: TextView = v.findViewById(R.id.tv_date_range)
        val tvMeta: TextView = v.findViewById(R.id.tv_meta)
        val tvDesc: TextView = v.findViewById(R.id.tv_description)
        val progressContainer: View = v.findViewById(R.id.progressContainer)
        val progressView: CircularProgressView = v.findViewById(R.id.progress)
        val tvProgress: TextView = v.findViewById(R.id.tv_progress)
    }

    private class GridVH(v: View) : RecyclerView.ViewHolder(v) {
        val ivThumb: ImageView = v.findViewById(R.id.iv_thumbnail)
        val tvTitle: TextView = v.findViewById(R.id.tv_title)
        val ivUnread: ImageView = v.findViewById(R.id.iv_unread)
        val tvRange: TextView = v.findViewById(R.id.tv_date_range)
        val tvMeta: TextView = v.findViewById(R.id.tv_meta)
        val pbProgress: ProgressBar = v.findViewById(R.id.pb_progress)
    }
}
