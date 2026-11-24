package com.shaonianpai.tablet.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shaonianpai.tablet.R

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login2)

        findViewById<TextView>(R.id.textViewVersion).text =
            "v${com.shaonianpai.tablet.data.UpdateManager.getCurrentVersionName(this)}"

        findViewById<Button>(R.id.buttonLogin).setOnClickListener {
            startActivity(Intent(this, DesktopActivity::class.java))
            finish()
        }
        findViewById<TextView>(R.id.textViewSwitchAccount).setOnClickListener {
            Toast.makeText(this, "当前演示账号：张三（九年级 3 班）", Toast.LENGTH_SHORT).show()
        }
    }
}
