package com.example.poketmon_on_app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.poketmon_on_app.R
import com.example.poketmon_on_app.pet.PokemonData
import com.example.poketmon_on_app.pet.PokemonRepository
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class PokemonListFragment : Fragment() {

    private lateinit var adapter: PokemonAdapter
    private lateinit var repository: PokemonRepository

    var onPokemonSelected: ((PokemonData) -> Unit)? = null

    private var selectedGen: Int? = null
    private var selectedType: String? = null
    private var searchQuery: String = ""

    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pokemon_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = PokemonRepository(requireContext())

        adapter = PokemonAdapter(repository) { pokemon ->
            adapter.setSelected(pokemon.id)
            onPokemonSelected?.invoke(pokemon)
        }

        val grid = view.findViewById<RecyclerView>(R.id.pokemonGrid)
        grid.layoutManager = GridLayoutManager(requireContext(), 5)
        grid.adapter = adapter

        setupGenChips(view)
        setupTypeChips(view)
        setupSearch(view)

        applyFilter()
    }

    private fun setupGenChips(view: View) {
        val chipGroup = view.findViewById<ChipGroup>(R.id.genChipGroup)

        // "전체" chip
        val allChip = Chip(requireContext()).apply {
            text = "전체"
            isCheckable = true
            isChecked = true
        }
        chipGroup.addView(allChip)

        for (gen in repository.getGenerations()) {
            val chip = Chip(requireContext()).apply {
                text = "${gen}세대"
                isCheckable = true
                tag = gen
            }
            chipGroup.addView(chip)
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty() || checkedIds.contains(allChip.id)) {
                selectedGen = null
                allChip.isChecked = true
            } else {
                val checkedChip = chipGroup.findViewById<Chip>(checkedIds[0])
                selectedGen = checkedChip?.tag as? Int
            }
            applyFilter()
        }
    }

    private fun setupTypeChips(view: View) {
        val chipGroup = view.findViewById<ChipGroup>(R.id.typeChipGroup)

        for (type in repository.getAllTypes()) {
            val chip = Chip(requireContext()).apply {
                text = type
                isCheckable = true
                tag = type
            }
            chipGroup.addView(chip)
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) {
                selectedType = null
            } else {
                val checkedChip = chipGroup.findViewById<Chip>(checkedIds[0])
                selectedType = checkedChip?.tag as? String
            }
            applyFilter()
        }
    }

    private fun setupSearch(view: View) {
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
                debounceRunnable = Runnable {
                    searchQuery = s?.toString() ?: ""
                    applyFilter()
                }
                debounceHandler.postDelayed(debounceRunnable!!, 300L)
            }
        })
    }

    private fun applyFilter() {
        val filtered = repository.filter(gen = selectedGen, type = selectedType, query = searchQuery)
        adapter.submitList(filtered)
    }

    fun setSelected(pokemonId: Int) {
        if (::adapter.isInitialized) {
            adapter.setSelected(pokemonId)
        }
    }
}
