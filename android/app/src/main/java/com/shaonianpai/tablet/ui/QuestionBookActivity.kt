package com.shaonianpai.tablet.ui

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.CollectedItem
import com.shaonianpai.tablet.data.QuestionBookStore
import com.shaonianpai.tablet.data.UserProfileStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Faithfully recreates original QuestionBookActivity (activity_questionbook.xml):
 * - Top bar: Book Spinner (全部/我的收藏夹/草稿本存档/语文错题本/数学错题本), List/Grid toggle,
 *   Filter & Grouping menu (按添加日期分组 / 按源类型分组 / 显示已掌握), and live keyword search.
 * - Interactive Tag chip bar (TagsContent).
 * - SplitView: Left category/date/source selector (listViewSelector) + Right RecyclerView supporting
 *   BOTH original List item cards (layout_customquestionbookitemview.xml with thumbnail + comment + edit)
 *   and Grid photo cards (layout_customquestionbookitemview_grid.xml with translucent bottom overlay).
 */
class QuestionBookActivity : AppCompatActivity() {

    private data class QbRecord(
        val id: String,
        val title: String,
        val subtitle: String,
        val bookName: String,
        val sourceType: String, // SCREENSHOT, PHOTO, SCRATCHPAD, QUESTION
        val tag: String,
        val dateStr: String,
        val body: String,
        val imagePath: String = "",
        val isBuiltInSample: Boolean = false,
        val understood: Boolean = false
    )

    private lateinit var spinnerBooks: Spinner
    private lateinit var llTags: LinearLayout
    private lateinit var lvSelector: ListView
    private lateinit var rvItems: RecyclerView
    private lateinit var tvNoData: TextView
    private lateinit var llSearchBar: View
    private lateinit var etSearch: EditText
    private lateinit var btnToggleGrid: ImageButton

    private var gridMode = false
    private var groupByDate = false // false = group by source type / chapter, true = group by date
    private var showUnderstood = true
    private var selectedBook = "全部"
    private var selectedTag = "全部"
    private var selectedLeftIndex = 0
    private var searchQuery = ""

    private var allRecords: List<QbRecord> = emptyList()
    private var leftGroups: List<Pair<String, List<QbRecord>>> = emptyList()
    private val understoodIds = mutableSetOf<String>()

    private val builtInSamples = listOf(
        QbRecord(
            id = "sample_1",
            title = "文言文实词注音辨析（《岳阳楼记》）",
            subtitle = "语文 · 第三单元古诗文",
            bookName = "语文错题本",
            sourceType = "QUESTION",
            tag = "文言文",
            dateStr = "2021-09-30",
            body = "题干：下列加点字注音和解释完全正确的一项是（ ）\nA. 属予作文以记之（zhǔ，同“嘱”）  B. 岸芷汀兰（dīng）  C. 浩浩汤汤（tāng）\n我的作答：B   |   正确答案：A\n解析：“汀”读 tīng；“汤汤”表水势浩大读 shāng。",
            isBuiltInSample = true
        ),
        QbRecord(
            id = "sample_2",
            title = "《岳阳楼记》动静结合名句默写",
            subtitle = "语文 · 第三单元古诗文",
            bookName = "语文错题本",
            sourceType = "QUESTION",
            tag = "易错",
            dateStr = "2021-09-30",
            body = "题干：描写洞庭湖月夜美景的动静结合句：________________，________________。\n我的作答：浮光跃金，静影沉璧（误写“壁”）\n正确答案：浮光跃金，静影沉璧\n解析：“璧”为玉字底，指平圆形美玉。",
            isBuiltInSample = true
        ),
        QbRecord(
            id = "sample_3",
            title = "《沁园春·雪》“山舞银蛇，原驰蜡象”赏析",
            subtitle = "语文 · 第一单元现代诗歌",
            bookName = "语文错题本",
            sourceType = "QUESTION",
            tag = "重点",
            dateStr = "2021-09-28",
            body = "题干：运用了什么修辞手法，表达了怎样的气魄？\n参考答案：比喻、对偶、化静为动，展现雄浑壮阔之势。",
            isBuiltInSample = true
        ),
        QbRecord(
            id = "sample_4",
            title = "二次函数闭区间最值求解",
            subtitle = "数学 · 第二十二章二次函数",
            bookName = "数学错题本",
            sourceType = "QUESTION",
            tag = "重点",
            dateStr = "2021-09-29",
            body = "题干：已知 y = x² - 4x + 3，当 0 ≤ x ≤ 3 时，求函数 y 的取值范围。\n我的作答：-1 ≤ y ≤ 0   |   正确答案：-1 ≤ y ≤ 3\n解析：对称轴 x = 2 在区间内取最小 -1；端点 x = 0 处 y = 3 大于 x = 3 处 y = 0。",
            isBuiltInSample = true
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_questionbook)

        findViewById<View>(R.id.qbTopBar).setBackgroundColor(UserProfileStore.getThemePrimaryColor(this))
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        spinnerBooks = findViewById(R.id.spinnerBooks)
        llTags = findViewById(R.id.llTagsContent)
        lvSelector = findViewById(R.id.listViewSelector)
        rvItems = findViewById(R.id.recyclerViewItems)
        tvNoData = findViewById(R.id.textViewNoData)
        llSearchBar = findViewById(R.id.llSearchBar)
        etSearch = findViewById(R.id.etSearchQb)
        btnToggleGrid = findViewById(R.id.btnToggleGrid)

        val splitContainer = findViewById<LinearLayout>(R.id.splitContainer)
        val splitDivider = findViewById<View>(R.id.splitDivider)
        if (resources.configuration.screenWidthDp < 600) {
            val d = resources.displayMetrics.density
            splitContainer.orientation = LinearLayout.VERTICAL
            lvSelector.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (124 * d).toInt())
            splitDivider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (2 * d).toInt())
            (rvItems.parent as View).layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // Load persisted "understood" flags
        val prefs = getSharedPreferences("shaonianpai_prefs", MODE_PRIVATE)
        understoodIds.addAll(prefs.getStringSet("qb_understood_ids", emptySet()) ?: emptySet())

        btnToggleGrid.setOnClickListener {
            gridMode = !gridMode
            btnToggleGrid.setImageResource(if (gridMode) R.drawable.ic_list_white else R.drawable.ic_grid_white)
            applyRecyclerLayoutManager()
            renderRightItems()
        }

        findViewById<ImageButton>(R.id.btnFilterMenu).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, 1, 0, if (showUnderstood) "✓ 显示全部（含已掌握）" else "   显示全部（含已掌握）")
            popup.menu.add(0, 2, 1, if (!groupByDate) "● 按来源/学科分组" else "○ 按来源/学科分组")
            popup.menu.add(0, 3, 2, if (groupByDate) "● 按添加日期分组" else "○ 按添加日期分组")
            popup.menu.add(0, 4, 3, if (gridMode) "切换为列表视图" else "切换为相册网格视图")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { showUnderstood = !showUnderstood; rebuildGroups() }
                    2 -> { groupByDate = false; selectedLeftIndex = 0; rebuildGroups() }
                    3 -> { groupByDate = true; selectedLeftIndex = 0; rebuildGroups() }
                    4 -> {
                        gridMode = !gridMode
                        btnToggleGrid.setImageResource(if (gridMode) R.drawable.ic_list_white else R.drawable.ic_grid_white)
                        applyRecyclerLayoutManager()
                        renderRightItems()
                    }
                }
                true
            }
            popup.show()
        }

        findViewById<ImageButton>(R.id.btnSearchToggle).setOnClickListener {
            val showing = llSearchBar.visibility == View.VISIBLE
            llSearchBar.visibility = if (showing) View.GONE else View.VISIBLE
            if (showing) {
                etSearch.setText("")
                searchQuery = ""
                rebuildGroups()
            } else {
                etSearch.requestFocus()
            }
        }
        findViewById<View>(R.id.btnClearSearch).setOnClickListener {
            etSearch.setText("")
            searchQuery = ""
            llSearchBar.visibility = View.GONE
            rebuildGroups()
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                rebuildGroups()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (intent.getStringExtra("initial_tab") == "favorites") {
            selectedBook = "全部"
        }

        applyRecyclerLayoutManager()
        reloadAll()
    }

    override fun onResume() {
        super.onResume()
        reloadAll()
    }

    private fun applyRecyclerLayoutManager() {
        if (gridMode) {
            val span = if (resources.configuration.screenWidthDp >= 720) 3 else 2
            rvItems.layoutManager = GridLayoutManager(this, span)
        } else {
            rvItems.layoutManager = LinearLayoutManager(this)
        }
    }

    private fun reloadAll() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val collected = QuestionBookStore.loadAll(this).map { c ->
            QbRecord(
                id = c.id,
                title = c.title,
                subtitle = "${c.bookName} · ${sourceLabel(c.sourceType)}",
                bookName = c.bookName,
                sourceType = c.sourceType,
                tag = c.tags.firstOrNull()?.takeIf { it.isNotBlank() } ?: "收藏",
                dateStr = sdf.format(Date(c.createdAt)),
                body = c.description,
                imagePath = c.imagePath,
                isBuiltInSample = false,
                understood = understoodIds.contains(c.id)
            )
        }
        allRecords = collected + builtInSamples.map { it.copy(understood = understoodIds.contains(it.id)) }

        setupBookSpinner()
        renderTagBar()
        rebuildGroups()
    }

    private fun setupBookSpinner() {
        val books = linkedSetOf("全部", "我的收藏夹", "草稿本存档", "语文错题本", "数学错题本")
        allRecords.forEach { if (it.bookName.isNotBlank()) books.add(it.bookName) }
        val bookList = books.toList()
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, bookList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.WHITE)
                v.textSize = 16f
                v.setTypeface(null, Typeface.BOLD)
                v.text = "${bookList[position]} ▾"
                return v
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBooks.adapter = adapter
        val idx = bookList.indexOf(selectedBook).coerceAtLeast(0)
        spinnerBooks.setSelection(idx, false)
        spinnerBooks.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val chosen = bookList[position]
                if (chosen != selectedBook) {
                    selectedBook = chosen
                    selectedLeftIndex = 0
                    rebuildGroups()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun renderTagBar() {
        llTags.removeAllViews()
        val tags = linkedSetOf("全部", "重点", "易错", "截屏", "拍照", "草稿", "文言文")
        allRecords.forEach { if (it.tag.isNotBlank()) tags.add(it.tag) }

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val primary = UserProfileStore.getThemePrimaryColor(this)

        llTags.addView(TextView(this).apply {
            text = "标签："
            textSize = 13f
            setTextColor(Color.parseColor("#607D8B"))
            setPadding(0, 0, dp(4), 0)
        })

        for (tag in tags) {
            val active = tag == selectedTag
            llTags.addView(TextView(this).apply {
                text = tag
                textSize = 12.5f
                setTextColor(if (active) Color.WHITE else Color.parseColor("#455A64"))
                setPadding(dp(12), dp(5), dp(12), dp(5))
                background = GradientDrawable().apply {
                    setColor(if (active) primary else Color.parseColor("#EEF2F6"))
                    cornerRadius = dp(14).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener {
                    selectedTag = tag
                    selectedLeftIndex = 0
                    renderTagBar()
                    rebuildGroups()
                }
            })
        }
    }

    private fun rebuildGroups() {
        val filtered = allRecords.filter { r ->
            val okBook = selectedBook == "全部" || r.bookName == selectedBook
            val okTag = selectedTag == "全部" || r.tag == selectedTag
            val okUnderstood = showUnderstood || !r.understood
            val okSearch = searchQuery.isEmpty() ||
                r.title.contains(searchQuery, ignoreCase = true) ||
                r.body.contains(searchQuery, ignoreCase = true) ||
                r.tag.contains(searchQuery, ignoreCase = true) ||
                r.bookName.contains(searchQuery, ignoreCase = true)
            okBook && okTag && okUnderstood && okSearch
        }

        val groups = mutableListOf<Pair<String, List<QbRecord>>>()
        groups.add("全部内容 (${filtered.size})" to filtered)

        if (groupByDate) {
            filtered.groupBy { it.dateStr }.entries
                .sortedByDescending { it.key }
                .forEach { (date, items) -> groups.add("$date (${items.size})" to items) }
        } else {
            val bySource = filtered.groupBy { sourceLabel(it.sourceType) }
            listOf("屏幕截图", "拍照收藏", "草稿本存档", "错题与试题", "速记笔记").forEach { key ->
                val list = bySource[key].orEmpty()
                if (list.isNotEmpty()) groups.add("$key (${list.size})" to list)
            }
        }

        leftGroups = groups
        selectedLeftIndex = selectedLeftIndex.coerceIn(0, (leftGroups.size - 1).coerceAtLeast(0))

        lvSelector.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_activated_1, leftGroups.map { it.first })
        lvSelector.choiceMode = ListView.CHOICE_MODE_SINGLE
        lvSelector.setItemChecked(selectedLeftIndex, true)
        lvSelector.setOnItemClickListener { _, _, position, _ ->
            selectedLeftIndex = position
            renderRightItems()
        }
        renderRightItems()
    }

    private fun renderRightItems() {
        val items = leftGroups.getOrNull(selectedLeftIndex)?.second.orEmpty()
        tvNoData.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        rvItems.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        rvItems.adapter = QbAdapter(items)
    }

    private fun sourceLabel(type: String): String = when (type) {
        "SCREENSHOT" -> "屏幕截图"
        "PHOTO", "CAMERA" -> "拍照收藏"
        "SCRATCHPAD" -> "草稿本存档"
        "QUESTION" -> "错题与试题"
        else -> "收藏笔记"
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

    private fun showCommentDialog(rec: QbRecord) {
        AlertDialog.Builder(this)
            .setTitle(rec.title)
            .setMessage(
                buildString {
                    append("分类：${rec.bookName}  |  标签：${rec.tag}  |  日期：${rec.dateStr}\n")
                    if (rec.understood) append("状态：✓ 已掌握\n")
                    append("\n")
                    append(rec.body.ifBlank { "暂无附加笔记说明" })
                }
            )
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showItemActionMenu(anchor: View, rec: QbRecord) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, if (rec.understood) "取消「已掌握」标记" else "标记「我已掌握此题」")
        if (!rec.isBuiltInSample) {
            popup.menu.add(0, 2, 1, "删除此条收藏")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    if (understoodIds.contains(rec.id)) understoodIds.remove(rec.id)
                    else understoodIds.add(rec.id)
                    getSharedPreferences("shaonianpai_prefs", MODE_PRIVATE)
                        .edit().putStringSet("qb_understood_ids", understoodIds.toSet()).apply()
                    reloadAll()
                }
                2 -> {
                    AlertDialog.Builder(this)
                        .setTitle("删除收藏")
                        .setMessage("确定要删除「${rec.title}」吗？")
                        .setPositiveButton("删除") { _, _ ->
                            QuestionBookStore.deleteItem(this, rec.id)
                            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                            reloadAll()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            true
        }
        popup.show()
    }

    private inner class QbAdapter(private val list: List<QbRecord>) :
        RecyclerView.Adapter<QbAdapter.VH>() {

        inner class VH(val root: CardView, val container: FrameLayout) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val d = resources.displayMetrics.density
            val card = CardView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (8 * d).toInt()) }
                radius = 8 * d
                cardElevation = 2 * d
                setCardBackgroundColor(Color.WHITE)
            }
            val container = FrameLayout(parent.context)
            card.addView(container)
            return VH(card, container)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val rec = list[position]
            val d = resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            holder.container.removeAllViews()

            val hasImage = rec.imagePath.isNotBlank() && File(rec.imagePath).exists()

            if (gridMode) {
                // Original layout_customquestionbookitemview_grid.xml style: square photo/card with translucent bottom bar
                val box = FrameLayout(this@QuestionBookActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190))
                    setBackgroundColor(Color.parseColor("#ECEFF1"))
                }
                if (hasImage) {
                    val bmp = runCatching { BitmapFactory.decodeFile(rec.imagePath) }.getOrNull()
                    if (bmp != null) {
                        box.addView(ImageView(this@QuestionBookActivity).apply {
                            setImageBitmap(bmp)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        })
                    }
                } else {
                    box.addView(TextView(this@QuestionBookActivity).apply {
                        text = rec.body.ifBlank { rec.title }
                        textSize = 12.5f
                        setTextColor(Color.parseColor("#37474F"))
                        setPadding(dp(12), dp(12), dp(12), dp(54))
                        maxLines = 6
                        ellipsize = TextUtils.TruncateAt.END
                    })
                }
                val bottomOverlay = LinearLayout(this@QuestionBookActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(Color.parseColor("#99000000"))
                    setPadding(dp(8), dp(6), dp(6), dp(6))
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.BOTTOM
                    }
                }
                bottomOverlay.addView(TextView(this@QuestionBookActivity).apply {
                    text = rec.title
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                bottomOverlay.addView(makeMiniBtn(R.drawable.ic_comments, Color.WHITE) { showCommentDialog(rec) })
                bottomOverlay.addView(makeMiniBtn(R.drawable.ic_edit, Color.WHITE) { showItemActionMenu(it, rec) })
                box.addView(bottomOverlay)
                box.setOnClickListener {
                    if (hasImage) showFullImage(rec.imagePath, rec.title) else showCommentDialog(rec)
                }
                holder.container.addView(box)
            } else {
                // Original layout_customquestionbookitemview.xml style: thumbnail left + Title/SubTitle/Tags/Desc + Comment & Edit right
                val row = LinearLayout(this@QuestionBookActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(12), dp(10), dp(12))
                }
                if (hasImage) {
                    val bmp = runCatching { BitmapFactory.decodeFile(rec.imagePath) }.getOrNull()
                    if (bmp != null) {
                        row.addView(ImageView(this@QuestionBookActivity).apply {
                            setImageBitmap(bmp)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            layoutParams = LinearLayout.LayoutParams(dp(88), dp(88)).apply { marginEnd = dp(12) }
                            setOnClickListener { showFullImage(rec.imagePath, rec.title) }
                        })
                    }
                }
                val col = LinearLayout(this@QuestionBookActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                col.addView(TextView(this@QuestionBookActivity).apply {
                    text = if (rec.understood) "✓ ${rec.title}" else rec.title
                    textSize = 15.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (rec.understood) Color.parseColor("#2E7D32") else Color.parseColor("#212121"))
                })
                col.addView(TextView(this@QuestionBookActivity).apply {
                    text = "${rec.subtitle} · ${rec.dateStr}"
                    textSize = 12f
                    setTextColor(Color.parseColor("#90A4AE"))
                    setPadding(0, dp(2), 0, dp(4))
                })
                // Tag chip
                val tagRow = LinearLayout(this@QuestionBookActivity).apply { orientation = LinearLayout.HORIZONTAL }
                tagRow.addView(TextView(this@QuestionBookActivity).apply {
                    text = rec.tag
                    textSize = 11f
                    setTextColor(Color.parseColor("#1976D2"))
                    setPadding(dp(8), dp(2), dp(8), dp(2))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#E3F2FD"))
                        cornerRadius = dp(10).toFloat()
                    }
                })
                col.addView(tagRow)
                if (rec.body.isNotBlank()) {
                    col.addView(TextView(this@QuestionBookActivity).apply {
                        text = rec.body
                        textSize = 13f
                        maxLines = 3
                        ellipsize = TextUtils.TruncateAt.END
                        setTextColor(Color.parseColor("#546E7A"))
                        setPadding(0, dp(6), 0, 0)
                    })
                }
                row.addView(col)
                row.addView(makeMiniBtn(R.drawable.ic_comments, null) { showCommentDialog(rec) })
                row.addView(makeMiniBtn(R.drawable.ic_edit, null) { showItemActionMenu(it, rec) })
                row.setOnClickListener {
                    if (hasImage) showFullImage(rec.imagePath, rec.title) else showCommentDialog(rec)
                }
                holder.container.addView(row)
            }
        }

        private fun makeMiniBtn(iconRes: Int, tint: Int?, onClick: (View) -> Unit): ImageButton {
            val d = resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()
            return ImageButton(this@QuestionBookActivity).apply {
                setImageResource(iconRes)
                if (tint != null) setColorFilter(tint)
                background = null
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(6), dp(6), dp(6), dp(6))
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginStart = dp(4) }
                setOnClickListener { onClick(it) }
            }
        }
    }
}
