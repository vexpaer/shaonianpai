package com.shaonianpai.tablet.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.shaonianpai.tablet.data.QuestionBookStore
import java.io.File

/**
 * Lightweight transparent helper Activity launched by the Floating Quick Bar's Camera button.
 * Supports taking a photo via system Camera OR picking an image from gallery, then opens AddToQuestionBookDialog.
 */
class PhotoCaptureProxyActivity : AppCompatActivity() {

    private var tempPhotoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val options = arrayOf("拍照收藏（打开相机拍题/拍板书）", "从相册选择图片存入收藏夹")
        AlertDialog.Builder(this)
            .setTitle("拍照收藏")
            .setItems(options) { _, which ->
                if (which == 0) launchCamera() else launchGallery()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "当前设备未检测到相机应用，转为相册选图", Toast.LENGTH_SHORT).show()
            launchGallery()
            return
        }
        try {
            val f = File(QuestionBookStore.mediaDir(this), "camera_${System.currentTimeMillis()}.jpg")
            tempPhotoFile = f
            val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", f)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_CAMERA)
        } catch (e: Exception) {
            Toast.makeText(this, "启动相机失败：${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun launchGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent.createChooser(intent, "选择图片存入收藏夹"), REQ_GALLERY)
        } catch (_: Exception) {
            Toast.makeText(this, "未找到可用相册", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            tempPhotoFile?.delete()
            finish()
            return
        }
        var bmp: Bitmap? = null
        var path = ""
        if (requestCode == REQ_CAMERA) {
            val f = tempPhotoFile
            if (f != null && f.exists() && f.length() > 0) {
                bmp = decodeScaledBitmap(f.absolutePath)
                path = f.absolutePath
            } else {
                @Suppress("DEPRECATION")
                bmp = data?.extras?.get("data") as? Bitmap
            }
        } else if (requestCode == REQ_GALLERY) {
            val uri = data?.data
            if (uri != null) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { inp ->
                        bmp = BitmapFactory.decodeStream(inp)
                    }
                }
            }
        }

        if (bmp == null) {
            Toast.makeText(this, "未获取到图片", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        AddToQuestionBookDialog.show(
            activity = this,
            sourceType = "CAMERA",
            defaultTitle = "拍照收藏",
            defaultBook = "我的收藏夹",
            defaultTag = "拍照",
            bitmap = bmp,
            existingImagePath = path,
            onDismiss = { finish() }
        )
    }

    private fun decodeScaledBitmap(path: String): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / sample > 1440 || opts.outHeight / sample > 1440) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private const val REQ_CAMERA = 301
        private const val REQ_GALLERY = 302
    }
}
