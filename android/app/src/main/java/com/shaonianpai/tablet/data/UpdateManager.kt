package com.shaonianpai.tablet.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val changelog: String,
    val htmlUrl: String,
    val apkName: String,
    val apkDownloadUrl: String,
    val apkBytes: Long
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val currentVersion: String, val release: ReleaseInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String, val latestTag: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object UpdateManager {
    private const val PREF_NAME = "shaonianpai_prefs"
    private const val KEY_AUTO_UPDATE = "auto_check_update"
    private const val KEY_GITHUB_REPO = "github_repo"
    const val DEFAULT_GITHUB_REPO = "vexpaer/shaonianpai"

    fun getCurrentVersionName(ctx: Context): String = try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        info.versionName ?: "0.1.0"
    } catch (_: Exception) {
        "0.1.0"
    }

    fun isAutoCheckEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPDATE, true)

    fun setAutoCheckEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    fun getGithubRepo(ctx: Context): String =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GITHUB_REPO, DEFAULT_GITHUB_REPO) ?: DEFAULT_GITHUB_REPO

    fun setGithubRepo(ctx: Context, repo: String) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_GITHUB_REPO, repo.trim().trim('/')).apply()
    }

    /** Compare dotted semver strings (e.g. "0.2.0" > "0.1.0", ignoring leading 'v' or suffixes). */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val r = parseVersionParts(remoteVersion)
        val c = parseVersionParts(currentVersion)
        val len = maxOf(r.size, c.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun parseVersionParts(raw: String): List<Int> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        return cleaned.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
    }

    fun checkLatestReleaseSync(ctx: Context): UpdateCheckResult {
        val current = getCurrentVersionName(ctx)
        val repo = getGithubRepo(ctx)
        val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
        return try {
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ShaonianpaiTablet/$current")
            }
            val code = conn.responseCode
            if (code == 404) {
                return UpdateCheckResult.UpToDate(current, "v$current")
            }
            if (code != 200) {
                return UpdateCheckResult.Error("GitHub API 返回状态码 $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "v$current")
            val version = tag.removePrefix("v").removePrefix("V")
            val title = json.optString("name", tag).ifBlank { tag }
            val notes = json.optString("body", "").ifBlank { "包含界面优化与功能更新。" }
            val htmlUrl = json.optString("html_url", "https://github.com/$repo/releases")

            var apkName = ""
            var apkUrl = ""
            var apkBytes = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkName = name
                        apkUrl = a.optString("browser_download_url", "")
                        apkBytes = a.optLong("size", 0L)
                        break
                    }
                }
            }

            if (isNewerVersion(version, current)) {
                UpdateCheckResult.UpdateAvailable(
                    currentVersion = current,
                    release = ReleaseInfo(
                        tagName = tag,
                        versionName = version,
                        releaseTitle = title,
                        changelog = notes,
                        htmlUrl = htmlUrl,
                        apkName = apkName,
                        apkDownloadUrl = apkUrl,
                        apkBytes = apkBytes
                    )
                )
            } else {
                UpdateCheckResult.UpToDate(current, tag)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("检查更新失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun downloadApkSync(
        ctx: Context,
        release: ReleaseInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File {
        require(release.apkDownloadUrl.isNotBlank()) { "该 Release 未附带 APK 安装包" }
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val fileName = release.apkName.ifBlank { "shaonianpai-${release.tagName}.apk" }
        val target = File(dir, fileName)
        val tmp = File(dir, "$fileName.part")

        var urlStr = release.apkDownloadUrl
        var redirects = 0
        var conn: HttpURLConnection
        while (true) {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 20000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "ShaonianpaiTablet/${getCurrentVersionName(ctx)}")
            }
            val code = conn.responseCode
            if (code in 301..308) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                urlStr = loc
                redirects++
                if (redirects > 8) throw IllegalStateException("下载重定向次数过多")
            } else {
                break
            }
        }
        if (conn.responseCode != 200) {
            throw IllegalStateException("下载失败，HTTP ${conn.responseCode}")
        }
        val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else release.apkBytes
        var downloaded = 0L
        conn.inputStream.use { inp ->
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(16 * 1024)
                var read: Int
                while (inp.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
            }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        return target
    }

    fun canInstallPackages(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildInstallPermissionIntent(ctx: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun installApk(ctx: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(intent)
    }
}
