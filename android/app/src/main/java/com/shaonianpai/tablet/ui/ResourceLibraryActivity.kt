package com.shaonianpai.tablet.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.AssetItem
import com.shaonianpai.tablet.data.Repository
import com.shaonianpai.tablet.data.UserProfileStore

/**
 * Matches original 21_学习_搜索页.png & 22_我的资源库_入口结果.png (Route A):
 * - Top blue header: back arrow + "学习" + top-right search icon
 * - Second row in blue header: "试题搜索结果" & "资源搜索结果" tabs with crisp white underline indicator
 * - Directly displays rich offline sample resources & folders so browsing works immediately without empty screens!
 */
class ResourceLibraryActivity : AppCompatActivity() {

    sealed class LibEntry {
        data class Folder(val name: String, val count: Int) : LibEntry()
        data class FileEntry(val asset: AssetItem) : LibEntry()
        data class QuestionEntry(val title: String, val meta: String, val body: String) : LibEntry()
    }

    private lateinit var rv: RecyclerView
    private lateinit var searchBar: LinearLayout
    private lateinit var indQuestions: View
    private lateinit var indResources: View

    private var activeTab = 1 // 1 = 资源搜索结果 (default so resources show immediately), 0 = 试题搜索结果
    private var queryText = ""
    private var resourceEntries: List<LibEntry> = emptyList()
    private val sampleQuestions = listOf(
        LibEntry.QuestionEntry(
            "文言文实词注音辨析（《岳阳楼记》）",
            "语文 · 第三单元古诗文",
            "下列加点字注音和解释完全正确的一项是（ ）  参考答案：A（属 zhǔ 同“嘱”）"
        ),
        LibEntry.QuestionEntry(
            "《岳阳楼记》动静结合名句默写",
            "语文 · 古诗文默写",
            "描写洞庭湖月夜美景的动静结合句：浮光跃金，静影沉璧。"
        ),
        LibEntry.QuestionEntry(
            "二次函数闭区间最值求解",
            "数学 · 第二十二章二次函数",
            "已知 y = x² - 4x + 3，当 0 ≤ x ≤ 3 时，值域为 [-1, 3]。"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val primary = UserProfileStore.getThemePrimaryColor(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Top blue bar + Tab strip matching 21_学习_搜索页.png & 22_我的资源库_入口结果.png
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(primary)
            elevation = dp(4).toFloat()
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
            setPadding(dp(4), 0, dp(8), 0)
        }
        topRow.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back_white_24dp)
            background = null
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { finish() }
        })
        topRow.addView(TextView(this).apply {
            text = "学习"
            textSize = 18f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
        })
        topRow.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            background = null
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener {
                searchBar.visibility = if (searchBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        })
        header.addView(topRow)

        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
        }
        val tabQ = makeTab("试题搜索结果", false) { switchTab(0) }
        val tabR = makeTab("资源搜索结果", true) { switchTab(1) }
        indQuestions = tabQ.second
        indResources = tabR.second
        tabRow.addView(tabQ.first, LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.MATCH_PARENT))
        tabRow.addView(tabR.first, LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.MATCH_PARENT))
        header.addView(tabRow)
        root.addView(header)

        // Collapsible search input
        searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#F5F7FA"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
        }
        val etSearch = EditText(this).apply {
            hint = "请输入搜索关键词（如：岳阳楼记、二次函数）"
            textSize = 14f
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    queryText = s?.toString()?.trim().orEmpty()
                    renderList()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        searchBar.addView(etSearch)
        root.addView(searchBar)

        rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ResourceLibraryActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(rv)
        setContentView(root)

        Thread {
            val (tasks, _) = Repository.fetchDeliveriesSync(this)
            val uniqueAssets = tasks.flatMap { it.assets }.distinctBy { it.assetId }
            val entries = mutableListOf<LibEntry>(
                LibEntry.Folder("语文课堂资料", 5),
                LibEntry.Folder("数学专题讲义", 2),
                LibEntry.Folder("英语听力合集", 2)
            )
            entries.addAll(uniqueAssets.map { LibEntry.FileEntry(it) })
            runOnUiThread {
                resourceEntries = entries
                renderList()
            }
        }.start()
    }

    private fun makeTab(label: String, active: Boolean, onClick: () -> Unit): Pair<FrameLayout, View> {
        val d = resources.displayMetrics.density
        val box = FrameLayout(this)
        box.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val ind = View(this).apply {
            setBackgroundColor(Color.WHITE)
            visibility = if (active) View.VISIBLE else View.INVISIBLE
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (2.5f * d).toInt()).apply {
                gravity = Gravity.BOTTOM
            }
        }
        box.addView(ind)
        box.setOnClickListener { onClick() }
        return box to ind
    }

    private fun switchTab(idx: Int) {
        activeTab = idx
        indQuestions.visibility = if (idx == 0) View.VISIBLE else View.INVISIBLE
        indResources.visibility = if (idx == 1) View.VISIBLE else View.INVISIBLE
        renderList()
    }

    private fun renderList() {
        val raw = if (activeTab == 0) sampleQuestions else resourceEntries
        val filtered = if (queryText.isEmpty()) raw else raw.filter {
            when (it) {
                is LibEntry.Folder -> it.name.contains(queryText, ignoreCase = true)
                is LibEntry.FileEntry -> it.asset.title.contains(queryText, ignoreCase = true)
                is LibEntry.QuestionEntry -> it.title.contains(queryText, ignoreCase = true) ||
                    it.body.contains(queryText, ignoreCase = true)
            }
        }
        rv.adapter = LibAdapter(filtered)
    }

    private inner class LibAdapter(private val list: List<LibEntry>) :
        RecyclerView.Adapter<LibAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivType: ImageView = v.findViewById(R.id.ImageViewFileType)
            val tvTitle: TextView = v.findViewById(R.id.TextTitle)
            val tvSize: TextView = v.findViewById(R.id.tvSize)
            val ivFav: ImageView = v.findViewById(R.id.ImageViewFav)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.listitem_resourcelist, parent, false))

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            when (val e = list[position]) {
                is LibEntry.Folder -> {
                    holder.ivType.setImageResource(R.drawable.ic_folder)
                    holder.tvTitle.text = e.name
                    holder.tvSize.text = "${e.count} 项"
                    holder.ivFav.visibility = View.INVISIBLE
                    holder.itemView.setOnClickListener {
                        Toast.makeText(this@ResourceLibraryActivity, "目录：${e.name}", Toast.LENGTH_SHORT).show()
                    }
                }
                is LibEntry.FileEntry -> {
                    val a = e.asset
                    holder.ivType.setImageResource(Repository.iconResForMime(a.mime))
                    holder.tvTitle.text = a.title
                    holder.tvSize.text = Repository.formatBytes(a.bytes)
                    holder.ivFav.visibility = View.VISIBLE
                    holder.itemView.setOnClickListener {
                        when {
                            a.mime.contains("pdf") -> startActivity(
                                Intent(this@ResourceLibraryActivity, DocumentViewActivity::class.java).putExtra("asset", a)
                            )
                            a.mime.contains("video") -> startActivity(
                                Intent(this@ResourceLibraryActivity, VideoPlayerActivity::class.java).putExtra("asset", a)
                            )
                            else -> Toast.makeText(this@ResourceLibraryActivity, "预览：${a.title}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                is LibEntry.QuestionEntry -> {
                    holder.ivType.setImageResource(R.drawable.qb_btn_notebook)
                    holder.tvTitle.text = e.title
                    holder.tvSize.text = "${e.meta} · ${e.body}"
                    holder.ivFav.visibility = View.INVISIBLE
                    holder.itemView.setOnClickListener {
                        startActivity(Intent(this@ResourceLibraryActivity, QuestionBookActivity::class.java))
                    }
                }
            }
        }
    }
}
