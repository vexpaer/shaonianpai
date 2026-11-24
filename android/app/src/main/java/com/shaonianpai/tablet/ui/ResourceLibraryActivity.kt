package com.shaonianpai.tablet.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.AssetItem
import com.shaonianpai.tablet.data.Repository

class ResourceLibraryActivity : AppCompatActivity() {

    sealed class LibEntry {
        data class Folder(val name: String, val count: Int) : LibEntry()
        data class FileEntry(val asset: AssetItem) : LibEntry()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resource_library)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.recentView)
        rv.layoutManager = LinearLayoutManager(this)

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
                rv.adapter = LibAdapter(entries)
            }
        }.start()
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
            }
        }
    }
}
