package com.shaonianpai.tablet.ui

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.shaonianpai.tablet.data.ReleaseInfo
import com.shaonianpai.tablet.data.Repository
import com.shaonianpai.tablet.data.UpdateManager

object UpdateUiHelper {

    fun showUpdateDialog(activity: Activity, currentVersion: String, release: ReleaseInfo) {
        if (activity.isFinishing || activity.isDestroyed) return
        val sizeText = if (release.apkBytes > 0) Repository.formatBytes(release.apkBytes) else "未知大小"
        val message = buildString {
            append("发现新版本：${release.tagName}（当前 v$currentVersion）\n")
            if (release.apkName.isNotBlank()) {
                append("安装包：${release.apkName}（$sizeText）\n")
            }
            append("\n更新说明：\n")
            append(release.changelog)
        }
        AlertDialog.Builder(activity)
            .setTitle("发现新版本 ${release.tagName}")
            .setMessage(message)
            .setNegativeButton("稍后再说", null)
            .setPositiveButton("下载并安装") { _, _ ->
                if (release.apkDownloadUrl.isBlank()) {
                    Toast.makeText(activity, "该版本未附带 APK，请前往 GitHub 页面查看", Toast.LENGTH_LONG).show()
                } else {
                    startDownloadAndInstall(activity, release)
                }
            }
            .show()
    }

    fun startDownloadAndInstall(activity: Activity, release: ReleaseInfo) {
        val d = activity.resources.displayMetrics.density
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * d).toInt(), (16 * d).toInt(), (22 * d).toInt(), (12 * d).toInt())
        }
        val tvProgress = TextView(activity).apply {
            text = "正在连接 GitHub 下载 ${release.tagName}..."
            textSize = 14f
        }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * d).toInt() }
        }
        container.addView(tvProgress)
        container.addView(bar)

        val dlg = AlertDialog.Builder(activity)
            .setTitle("正在下载更新")
            .setView(container)
            .setCancelable(false)
            .create()
        dlg.show()

        Thread {
            try {
                val apkFile = UpdateManager.downloadApkSync(activity, release) { done, total ->
                    activity.runOnUiThread {
                        if (total > 0) {
                            bar.isIndeterminate = false
                            val pct = ((done * 100L) / total).toInt().coerceIn(0, 100)
                            bar.progress = pct
                            tvProgress.text =
                                "已下载 ${Repository.formatBytes(done)} / ${Repository.formatBytes(total)}（$pct%）"
                        } else {
                            tvProgress.text = "已下载 ${Repository.formatBytes(done)}..."
                        }
                    }
                }
                activity.runOnUiThread {
                    dlg.dismiss()
                    if (!UpdateManager.canInstallPackages(activity)) {
                        AlertDialog.Builder(activity)
                            .setTitle("需要允许安装未知应用")
                            .setMessage("安装包已下载完成，请先在系统设置中允许「少年派」安装应用，返回后再次点击安装。")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("去授权") { _, _ ->
                                activity.startActivity(UpdateManager.buildInstallPermissionIntent(activity))
                            }
                            .show()
                    } else {
                        UpdateManager.installApk(activity, apkFile)
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dlg.dismiss()
                    Toast.makeText(activity, "下载更新失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
