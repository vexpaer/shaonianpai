package com.shaonianpai.tablet.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shaonianpai.tablet.R
import com.shaonianpai.tablet.data.UpdateManager
import com.shaonianpai.tablet.data.UserProfileStore

/**
 * Matches original 01_登录页.png:
 * - Fullscreen stretched background_main.png (polygon cloud + bottom-right 睿易 RUIYI logo)
 * - Circular avatar + white shadowed username/account + blue-underlined password input
 * - Blue rounded 登录 button + bottom-left blue 切换账号 + bottom-right version
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var ivAvatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var etPassword: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login2)

        ivAvatar = findViewById(R.id.imageViewVoice)
        tvName = findViewById(R.id.textViewRealName)
        etPassword = findViewById(R.id.editTextPassword)

        // Circular clip matching 01_登录页.png
        ivAvatar.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        ivAvatar.clipToOutline = true

        refreshAccountUi()

        findViewById<TextView>(R.id.textViewVersion).text =
            "v${UpdateManager.getCurrentVersionName(this)}"

        findViewById<Button>(R.id.buttonLogin).setOnClickListener {
            if (etPassword.text.isNullOrEmpty()) {
                Toast.makeText(this, "请输入密码（演示环境任意非空即可）", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, DesktopActivity::class.java))
            finish()
        }

        findViewById<TextView>(R.id.textViewSwitchAccount).setOnClickListener {
            showSwitchAccountDialog()
        }
    }

    private fun refreshAccountUi() {
        UserProfileStore.applyAvatarTo(this, ivAvatar)
        tvName.text = UserProfileStore.getUserName(this)
    }

    private fun showSwitchAccountDialog() {
        val d = resources.displayMetrics.density
        val pad = (18 * d).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val etName = EditText(this).apply {
            hint = "姓名（如：李明）"
            setText(UserProfileStore.getUserName(this@LoginActivity))
            setSingleLine(true)
        }
        val etId = EditText(this).apply {
            hint = "用户名 / 学号（如：373601）"
            setText(UserProfileStore.getStudentId(this@LoginActivity))
            setSingleLine(true)
        }
        val etClass = EditText(this).apply {
            hint = "班级（如：高二(3)班）"
            setText(UserProfileStore.getClassName(this@LoginActivity))
            setSingleLine(true)
        }
        box.addView(etName)
        box.addView(etId)
        box.addView(etClass)

        AlertDialog.Builder(this)
            .setTitle("切换 / 配置学生账号")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                UserProfileStore.setUserName(this, etName.text.toString())
                UserProfileStore.setStudentId(this, etId.text.toString())
                UserProfileStore.setClassName(this, etClass.text.toString())
                refreshAccountUi()
                Toast.makeText(this, "已切换账号：${UserProfileStore.getUserName(this)}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
