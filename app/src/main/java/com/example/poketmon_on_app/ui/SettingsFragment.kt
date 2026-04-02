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

    private fun bindSeekBar(
        view: View,
        seekBarId: Int,
        labelId: Int,
        currentValue: Int,
        offset: Int,
        formatLabel: (Int) -> String,
        onChanged: (Int) -> Unit
    ) {
        val seekBar = view.findViewById<SeekBar>(seekBarId)
        val label = view.findViewById<TextView>(labelId)
        seekBar.progress = currentValue - offset
        label.text = formatLabel(currentValue)

        seekBar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = progress + offset
                label.text = formatLabel(value)
                if (fromUser) {
                    onChanged(value)
                    sendSettings()
                }
            }
        })
    }

    private fun setupScale(view: View) {
        bindSeekBar(view, R.id.seekScale, R.id.valScale,
            currentValue = preferences.scale, offset = 50,
            formatLabel = { "${it}%" },
            onChanged = { preferences.scale = it })
    }

    private fun setupOpacity(view: View) {
        bindSeekBar(view, R.id.seekOpacity, R.id.valOpacity,
            currentValue = preferences.opacity, offset = 30,
            formatLabel = { "${it}%" },
            onChanged = { preferences.opacity = it })
    }

    private fun setupSpeed(view: View) {
        bindSeekBar(view, R.id.seekSpeed, R.id.valSpeed,
            currentValue = preferences.moveSpeedLevel, offset = 1,
            formatLabel = { "$it" },
            onChanged = { preferences.moveSpeedLevel = it })
    }

    private fun setupFrequency(view: View) {
        bindSeekBar(view, R.id.seekFreq, R.id.valFreq,
            currentValue = preferences.activityLevel, offset = 1,
            formatLabel = { "$it" },
            onChanged = { preferences.activityLevel = it })
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
        bindSeekBar(view, R.id.seekSleep, R.id.valSleep,
            currentValue = preferences.sleepTimeout, offset = 1,
            formatLabel = { "${it}분" },
            onChanged = { preferences.sleepTimeout = it })
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
