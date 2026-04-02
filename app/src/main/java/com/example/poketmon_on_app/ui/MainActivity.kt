package com.example.poketmon_on_app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.poketmon_on_app.R
import com.example.poketmon_on_app.pet.PetPreferences
import com.example.poketmon_on_app.pet.PetState
import com.example.poketmon_on_app.pet.PokemonData
import com.example.poketmon_on_app.pet.PokemonRepository
import com.example.poketmon_on_app.service.PetOverlayService
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private var serviceRunning = false
    private var selectedPokemon: PokemonData? = null
    private var currentPetState: PetState = PetState.IDLE

    private lateinit var repository: PokemonRepository
    private lateinit var preferences: PetPreferences
    private var pokemonListFragment: PokemonListFragment? = null

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateUI()
            if (Settings.canDrawOverlays(this)) {
                startPetService()
            }
        }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stateName = intent.getStringExtra(PetOverlayService.EXTRA_STATE) ?: return
            try {
                currentPetState = PetState.valueOf(stateName)
                updateStateUI()
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = PokemonRepository(this)
        preferences = PetPreferences(this)

        setupViewPager()
        setupSegmentedControl()

        val savedId = preferences.selectedPokemonId
        selectPokemon(repository.getAll().find { it.id == savedId } ?: repository.getAll().firstOrNull())

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
        serviceRunning = preferences.isServiceRunning
        registerReceiver(
            stateReceiver,
            IntentFilter(PetOverlayService.BROADCAST_STATE),
            RECEIVER_NOT_EXPORTED
        )
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
    }

    private fun setupViewPager() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val tabTitles = listOf("포켓몬", "설정")

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> PokemonListFragment().also { frag ->
                        pokemonListFragment = frag
                        frag.onPokemonSelected = { pokemon -> selectPokemon(pokemon) }
                    }
                    1 -> SettingsFragment()
                    else -> PokemonListFragment()
                }
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private fun setupSegmentedControl() {
        val btnSleep = findViewById<MaterialButton>(R.id.btnSleep)
        val btnWalk = findViewById<MaterialButton>(R.id.btnWalk)

        btnSleep.setOnClickListener {
            if (currentPetState == PetState.SLEEP) {
                sendCommand("wake")
            } else {
                sendCommand("sleep")
            }
        }

        btnWalk.setOnClickListener {
            if (currentPetState == PetState.WALK || currentPetState == PetState.RUN) {
                sendCommand("walk")
            } else {
                sendCommand("walk")
            }
        }
    }

    private fun sendCommand(command: String) {
        if (!serviceRunning) return
        val intent = Intent(this, PetOverlayService::class.java).apply {
            action = PetOverlayService.ACTION_COMMAND
            putExtra(PetOverlayService.EXTRA_COMMAND, command)
        }
        startForegroundService(intent)
    }

    private fun updateStateUI() {
        val statusText = findViewById<TextView>(R.id.statusText)
        val btnSleep = findViewById<MaterialButton>(R.id.btnSleep)

        val stateLabel = when (currentPetState) {
            PetState.IDLE -> "대기 중"
            PetState.WALK -> "걷는 중"
            PetState.RUN -> "뛰는 중"
            PetState.SLEEP -> "자는 중"
            PetState.REACTION -> "반응 중"
            PetState.DRAGGED -> "드래그 중"
        }

        if (serviceRunning) {
            statusText.text = "상태: $stateLabel"
        }

        btnSleep.text = if (currentPetState == PetState.SLEEP) "깨우기" else "재우기"
    }

    private fun selectPokemon(pokemon: PokemonData?) {
        pokemon ?: return
        selectedPokemon = pokemon
        preferences.selectedPokemonId = pokemon.id

        findViewById<TextView>(R.id.pokemonName).text = pokemon.name
        findViewById<TextView>(R.id.pokemonId).text =
            "${pokemon.displayId} · ${pokemon.types.joinToString("/")}"

        val previewImage = findViewById<ImageView>(R.id.previewImage)
        kotlin.concurrent.thread {
            val thumbnail = repository.getThumbnail(pokemon.id)
            previewImage.post {
                previewImage.setImageBitmap(thumbnail)
            }
        }

        pokemonListFragment?.setSelected(pokemon.id)

        if (serviceRunning) {
            val intent = Intent(this, PetOverlayService::class.java).apply {
                action = PetOverlayService.ACTION_CHANGE_POKEMON
                putExtra(PetOverlayService.EXTRA_POKEMON_ID, pokemon.id)
            }
            startForegroundService(intent)
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
        val segmented = findViewById<MaterialButtonToggleGroup>(R.id.segmentedControl)

        val hasPermission = Settings.canDrawOverlays(this)

        if (serviceRunning) {
            updateStateUI()
            btn.text = "펫 중지하기"
            btn.setBackgroundColor(0xFFF44336.toInt())
            segmented.visibility = View.VISIBLE
        } else if (hasPermission) {
            statusText.text = "준비 완료! 펫을 시작하세요."
            btn.text = "펫 시작하기"
            btn.setBackgroundColor(0xFF4CAF50.toInt())
            segmented.visibility = View.GONE
        } else {
            statusText.text = "오버레이 권한이 필요합니다"
            btn.text = "권한 허용 & 시작"
            btn.setBackgroundColor(0xFF4CAF50.toInt())
            segmented.visibility = View.GONE
        }
    }
}
