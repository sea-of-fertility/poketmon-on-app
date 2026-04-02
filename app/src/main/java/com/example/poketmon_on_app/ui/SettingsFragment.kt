package com.example.poketmon_on_app.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.example.poketmon_on_app.R
import com.example.poketmon_on_app.pet.PetPreferences
import com.example.poketmon_on_app.service.PetOverlayService

class SettingsFragment : Fragment() {

    private lateinit var preferences: PetPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = PetPreferences(requireContext())

        setupScale(view)
        setupOpacity(view)
        setupSpeed(view)
        setupFrequency(view)
        setupHideInGame(view)
        setupSleep(view)
    }

    private fun setupScale(view: View) {
        val seekBar = view.findViewById<SeekBar>(R.id.seekScale)
        val label = view.findViewById<TextView>(R.id.valScale)
        // SeekBar range: 0~150, maps to 50~200%
        val current = preferences.scale
        seekBar.progress = current - 50
        label.text = "${current}%"

        seekBar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progress + 50
                label.text = "${value}%"
                if (fromUser) {
                    preferences.scale = value
                    sendSettings()
                }
            }
        })
    }

    private fun setupOpacity(view: View) {
        val seekBar = view.findViewById<SeekBar>(R.id.seekOpacity)
        val label = view.findViewById<TextView>(R.id.valOpacity)
        // SeekBar range: 0~70, maps to 30~100%
        val current = preferences.opacity
        seekBar.progress = current - 30
        label.text = "${current}%"

        seekBar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progress + 30
                label.text = "${value}%"
                if (fromUser) {
                    preferences.opacity = value
                    sendSettings()
                }
            }
        })
    }

    private fun setupSpeed(view: View) {
        val seekBar = view.findViewById<SeekBar>(R.id.seekSpeed)
        val label = view.findViewById<TextView>(R.id.valSpeed)
        val current = preferences.moveSpeedLevel
        seekBar.progress = current - 1
        label.text = "$current"

        seekBar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progress + 1
                label.text = "$value"
                if (fromUser) {
                    preferences.moveSpeedLevel = value
                    sendSettings()
                }
            }
        })
    }

    private fun setupFrequency(view: View) {
        val seekBar = view.findViewById<SeekBar>(R.id.seekFreq)
        val label = view.findViewById<TextView>(R.id.valFreq)
        val current = preferences.activityLevel
        seekBar.progress = current - 1
        label.text = "$current"

        seekBar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progress + 1
                label.text = "$value"
                if (fromUser) {
                    preferences.activityLevel = value
                    sendSettings()
                }
            }
        })
    }

    private fun setupHideInGame(view: View) {
        val switch = view.findViewById<SwitchMaterial>(R.id.switchHideInGame)
        switch.isChecked = preferences.hideInGame

        switch.setOnCheckedChangeListener { _, isChecked ->
            preferences.hideInGame = isChecked
            if (isChecked && !hasUsageStatsPermission()) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            sendSettings()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            requireContext().packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onResume() {
        super.onResume()
        // Refresh toggle state after returning from settings
        view?.findViewById<SwitchMaterial>(R.id.switchHideInGame)?.let { switch ->
            if (switch.isChecked && !hasUsageStatsPermission()) {
                switch.isChecked = false
                preferences.hideInGame = false
            }
        }
    }

    private fun setupSleep(view: View) {
        val seekBar = view.findViewById<SeekBar>(R.id.seekSleep)
        val label = view.findViewById<TextView>(R.id.valSleep)
        val current = preferences.sleepTimeout
        seekBar.progress = current - 1
        label.text = "${current}분"

        seekBar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progress + 1
                label.text = "${value}분"
                if (fromUser) {
                    preferences.sleepTimeout = value
                    sendSettings()
                }
            }
        })
    }

    private fun sendSettings() {
        if (!preferences.isServiceRunning) return
        val intent = Intent(requireContext(), PetOverlayService::class.java).apply {
            action = PetOverlayService.ACTION_UPDATE_SETTINGS
        }
        requireContext().startForegroundService(intent)
    }

    abstract class SimpleSeekBarListener : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }
}
