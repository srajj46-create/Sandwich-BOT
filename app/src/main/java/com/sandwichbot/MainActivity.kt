package com.sandwichbot

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private val reqCapture = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("cfg", MODE_PRIVATE)
        Config.leadMs = sp.getInt("lead", 110)
        Config.targetPm = sp.getInt("target", 500)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Sandwich Tower Bot\n\n1) Accessibility me 'Sandwich Bot' ON karo\n2) Start dabao, screen capture allow karo\n3) Game kholo aur play dabao"
        })
        root.addView(Button(this).apply {
            text = "1) Accessibility Settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        addSeek(root, "Lead (ms)", 0, 300, Config.leadMs) {
            Config.leadMs = it; sp.edit().putInt("lead", it).apply()
        }
        addSeek(root, "Target x (permille)", 300, 700, Config.targetPm) {
            Config.targetPm = it; sp.edit().putInt("target", it).apply()
        }
        root.addView(Button(this).apply {
            text = "2) START"
            setOnClickListener {
                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                @Suppress("DEPRECATION")
                startActivityForResult(mpm.createScreenCaptureIntent(), reqCapture)
            }
        })
        root.addView(Button(this).apply {
            text = "STOP"
            setOnClickListener {
                startService(Intent(this@MainActivity, CaptureService::class.java).setAction("STOP"))
            }
        })
        setContentView(root)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqCapture && resultCode == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, CaptureService::class.java)
                    .putExtra("code", resultCode)
                    .putExtra("data", data)
            )
        }
    }

    private fun addSeek(root: LinearLayout, title: String, min: Int, max: Int, init: Int, onChange: (Int) -> Unit) {
        val tv = TextView(this).apply { text = "$title: $init" }
        val sb = SeekBar(this).apply {
            this.min = min; this.max = max; progress = init
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { tv.text = "$title: $p"; onChange(p) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(tv); root.addView(sb)
    }
}
