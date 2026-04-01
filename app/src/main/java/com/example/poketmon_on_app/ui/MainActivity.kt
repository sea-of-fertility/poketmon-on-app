package com.example.poketmon_on_app.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.poketmon_on_app.R
import com.example.poketmon_on_app.service.PetOverlayService
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private var serviceRunning = false

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateUI()
            if (Settings.canDrawOverlays(this)) {
                startPetService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadPreview()

        findViewById<MaterialButton>(R.id.btnToggleService).setOnClickListener {
            if (serviceRunning) {
                stopPetService()
            } else if (Settings.canDrawOverlays(this)) {
                startPetService()
            } else {
                requestOverlayPermission()
            }
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun loadPreview() {
        try {
            val input = assets.open("Sprites/0025/Idle-Anim.png")
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val sheet = BitmapFactory.decodeStream(input, null, opts)
            input.close()

            // Show first frame of Idle (Row 0 = Down)
            if (sheet != null) {
                val animInput = assets.open("Sprites/0025/AnimData.xml")
                animInput.close()
                // Simple: just crop first 40x56 from sheet (Pikachu Idle)
                val frameW = 40
                val frameH = 56
                if (sheet.width >= frameW && sheet.height >= frameH) {
                    val frame = android.graphics.Bitmap.createBitmap(sheet, 0, 0, frameW, frameH)
                    val preview = findViewById<ImageView>(R.id.previewImage)
                    preview.setImageBitmap(frame)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun startPetService() {
        val intent = Intent(this, PetOverlayService::class.java)
        startForegroundService(intent)
        serviceRunning = true
        updateUI()
    }

    private fun stopPetService() {
        stopService(Intent(this, PetOverlayService::class.java))
        serviceRunning = false
        updateUI()
    }

    private fun updateUI() {
        val statusText = findViewById<TextView>(R.id.statusText)
        val btn = findViewById<MaterialButton>(R.id.btnToggleService)

        val hasPermission = Settings.canDrawOverlays(this)

        if (serviceRunning) {
            statusText.text = "Pikachu가 화면에서 놀고 있습니다!"
            btn.text = "펫 중지하기"
        } else if (hasPermission) {
            statusText.text = "준비 완료! 펫을 시작하세요."
            btn.text = "펫 시작하기"
        } else {
            statusText.text = "오버레이 권한이 필요합니다"
            btn.text = "권한 허용 & 시작"
        }
    }
}
