package com.shaonianpai.tablet.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
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

class SelfLearnActivity : AppCompatActivity() {

    private val subjects = listOf("语文", "数学", "英语", "物理", "化学", "历史", "全部学科")
    private var currentSubject = "语文"
    private var isGridMode = false
    private val collapsedGroups = mutableSetOf<String>()
    private var allTasks: List<DeliveryTask> = emptyList()

    private lateinit var drawer: DrawerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var recycler: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvNoData: TextView

    sealed class RowItem {
        data class GroupHeader(val date: String, val count: Int, val collapsed: Boolean) : RowItem()
        data class TaskRow(val task: DeliveryTask) : RowItem()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selflearnview)

        currentSubject = intent.getStringExtra("subject") ?: "语文"
        isGridMode = intent.getBooleanExtra("grid_mode", false)

        drawer = findViewById(R.id.drawer)
        toolbar = findViewById(R.id.toolbar)
        recycler = findViewById(R.id.LayoutContent)
        swipeRefresh = findViewById(R.id.swipeRefreshLayout)
        tvNoData = findViewById(R.id.tv_nodata)

        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_list)
        toolbar.setNavigationOnClickListener { drawer.openDrawer(GravityCompat.START) }
        updateTitle()

        val lvSubjects = findViewById<ListView>(R.id.rv_subjects)
        lvSubjects.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, subjects)
        lvSubjects.setOnItemClickListener { _, _, position, _ ->
            currentSubject = subjects[position]
            updateTitle()
            drawer.closeDrawer(GravityCompat.START)
            renderRows()
        }

        swipeRefresh.setOnRefreshListener { loadTasks() }
        loadTasks()

        if (intent.getBooleanExtra("open_drawer", false)) {
            drawer.post { drawer.openDrawer(GravityCompat.START) }
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
        val filtered = if (currentSubject == "全部学科") {
            allTasks
        } else {
            allTasks.filter { it.subject == currentSubject }
        }

        if (filtered.isEmpty()) {
            tvNoData.visibility = View.VISIBLE
            recycler.adapter = null
            return
        }
        tvNoData.visibility = View.GONE

        val grouped = linkedMapOf<String, MutableList<DeliveryTask>>()
        for (t in filtered) {
            grouped.getOrPut(t.groupDate) { mutableListOf() }.add(t)
        }
        val rows = mutableListOf<RowItem>()
        for ((date, list) in grouped) {
            val collapsed = collapsedGroups.contains(date)
            rows.add(RowItem.GroupHeader(date, list.size, collapsed))
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
                        v.findViewById<View>(R.id.progress).parent?.let { p ->
                            if (p is View) {
                                p.layoutParams = (p.layoutParams as ViewGroup.MarginLayoutParams).apply {
                                    width = (50 * d).toInt()
                                    height = (50 * d).toInt()
                                    marginEnd = (10 * d).toInt()
                                }
                            }
                        }
                        v.findViewById<View>(R.id.progress).layoutParams =
                            v.findViewById<View>(R.id.progress).layoutParams.apply {
                                width = (50 * d).toInt()
                                height = (50 * d).toInt()
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
                    vh.tvName.text = item.date
                    vh.tvCount.text = "共计${item.count}项"
                    vh.tvExpand.text = if (item.collapsed) "展开" else "折叠"
                    vh.itemView.setOnClickListener {
                        if (collapsedGroups.contains(item.date)) collapsedGroups.remove(item.date)
                        else collapsedGroups.add(item.date)
                        renderRows()
                    }
                }
                is RowItem.TaskRow -> {
                    val t = item.task
                    val thumb = Repository.buildTaskThumbnail(this@SelfLearnActivity, t.assets)
                    if (holder is ListVH) {
                        holder.ivThumb.setImageBitmap(thumb)
                        holder.tvTitle.text = t.title
                        holder.ivUnread.visibility = if (t.unreadResourceCount > 0) View.VISIBLE else View.GONE
                        holder.tvStart.text = t.startDate
                        holder.tvEnd.text = t.endDate
                        holder.tvMeta.text = "${t.taskType}  |  未读 ${t.unreadResourceCount} 项  ·  未作答 ${t.unansweredCount} 题  ·  ${t.teacherName}"
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
        val tvStart: TextView = v.findViewById(R.id.tv_start_date)
        val tvEnd: TextView = v.findViewById(R.id.tv_end_date)
        val tvMeta: TextView = v.findViewById(R.id.tv_meta)
        val tvDesc: TextView = v.findViewById(R.id.tv_description)
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
