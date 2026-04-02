package com.example.poketmon_on_app.ui

import android.content.res.ColorStateList
import android.graphics.Color
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class PokemonListFragment : Fragment() {

    private lateinit var adapter: PokemonAdapter
    private lateinit var repository: PokemonRepository

    var onPokemonSelected: ((PokemonData) -> Unit)? = null

    private var selectedGens: MutableSet<Int> = mutableSetOf()
    private var selectedTypes: MutableSet<String> = mutableSetOf()
    private var searchQuery: String = ""

    private val genColors = mapOf(
        1 to "#E3350D", 2 to "#C4A23B", 3 to "#A12B2F",
        4 to "#5A78B4", 5 to "#444444",
    )

    private val typeColors = mapOf(
        "Normal" to "#A8A878", "Fire" to "#F08030", "Water" to "#6890F0",
        "Grass" to "#78C850", "Electric" to "#F8D030", "Ice" to "#98D8D8",
        "Fighting" to "#C03028", "Poison" to "#A040A0", "Ground" to "#E0C068",
        "Flying" to "#A890F0", "Psychic" to "#F85888", "Bug" to "#A8B820",
        "Rock" to "#B8A038", "Ghost" to "#705898", "Dragon" to "#7038F8",
        "Dark" to "#705848", "Steel" to "#B8B8D0", "Fairy" to "#EE99AC",
    )

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

        setupGenFilter(view)
        setupTypeFilter(view)
        setupSearch(view)

        applyFilter()
    }

    private fun setupGenFilter(view: View) {
        val filterBtn = view.findViewById<MaterialButton>(R.id.genFilterBtn)
        filterBtn.setOnClickListener { showGenBottomSheet(view) }
    }

    private fun setupTypeFilter(view: View) {
        val filterBtn = view.findViewById<MaterialButton>(R.id.typeFilterBtn)
        filterBtn.setOnClickListener { showTypeBottomSheet(view) }
    }

    private fun <T> showFilterBottomSheet(
        rootView: View,
        layoutRes: Int,
        chipGroupId: Int,
        items: List<T>,
        currentSelection: Set<T>,
        colorMap: Map<T, String>,
        labelOf: (T) -> String,
        onApply: (MutableSet<T>) -> Unit
    ) {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = layoutInflater.inflate(layoutRes, null)
        dialog.setContentView(sheet)

        val chipGroup = sheet.findViewById<ChipGroup>(chipGroupId)
        val tempSelected = currentSelection.toMutableSet()

        for (item in items) {
            val color = Color.parseColor(colorMap[item] ?: "#888888")
            chipGroup.addView(createFilterChip(labelOf(item), item, item in tempSelected, color))
        }

        sheet.findViewById<View>(R.id.bsResetBtn).setOnClickListener {
            for (i in 0 until chipGroup.childCount) {
                (chipGroup.getChildAt(i) as? Chip)?.isChecked = false
            }
            tempSelected.clear()
        }

        chipGroup.setOnCheckedStateChangeListener { group, _ ->
            tempSelected.clear()
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? Chip ?: continue
                @Suppress("UNCHECKED_CAST")
                if (chip.isChecked) tempSelected.add(chip.tag as T)
            }
        }

        sheet.findViewById<View>(R.id.bsApplyBtn).setOnClickListener {
            onApply(tempSelected)
            updateFilterButtonStates(rootView)
            applyFilter()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun <T> createFilterChip(label: String, tag: T, checked: Boolean, color: Int): Chip {
        return Chip(requireContext()).apply {
            text = label
            isCheckable = true
            isChecked = checked
            this.tag = tag
            chipBackgroundColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(color, Color.WHITE)
            )
            setTextColor(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, color)
            ))
            chipStrokeWidth = 1.5f * resources.displayMetrics.density
            chipStrokeColor = ColorStateList.valueOf(color)
        }
    }

    private fun showGenBottomSheet(rootView: View) {
        showFilterBottomSheet(
            rootView = rootView,
            layoutRes = R.layout.bottom_sheet_gen_filter,
            chipGroupId = R.id.bsGenChipGroup,
            items = repository.getGenerations(),
            currentSelection = selectedGens,
            colorMap = genColors,
            labelOf = { "${it}세대" },
            onApply = { selectedGens = it }
        )
    }

    private fun showTypeBottomSheet(rootView: View) {
        showFilterBottomSheet(
            rootView = rootView,
            layoutRes = R.layout.bottom_sheet_type_filter,
            chipGroupId = R.id.bsTypeChipGroup,
            items = repository.getAllTypes(),
            currentSelection = selectedTypes,
            colorMap = typeColors,
            labelOf = { it },
            onApply = { selectedTypes = it }
        )
    }

    private fun updateFilterButtonStates(view: View) {
        val genBtn = view.findViewById<MaterialButton>(R.id.genFilterBtn)
        val typeBtn = view.findViewById<MaterialButton>(R.id.typeFilterBtn)
        styleFilterButton(genBtn, "세대", selectedGens.size)
        styleFilterButton(typeBtn, "속성", selectedTypes.size)
    }

    private fun styleFilterButton(button: MaterialButton, label: String, count: Int) {
        val activeColor = Color.parseColor("#4CAF50")
        val defaultStroke = ColorStateList.valueOf(Color.parseColor("#DDDDDD"))
        val defaultTextColor = Color.parseColor("#555555")

        if (count == 0) {
            button.text = label
            button.strokeColor = defaultStroke
            button.setTextColor(defaultTextColor)
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        } else {
            button.text = "$label $count"
            button.strokeColor = ColorStateList.valueOf(activeColor)
            button.setTextColor(Color.WHITE)
            button.backgroundTintList = ColorStateList.valueOf(activeColor)
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
        val filtered = repository.filter(gens = selectedGens, types = selectedTypes, query = searchQuery)
        adapter.submitList(filtered)
    }

    fun setSelected(pokemonId: Int) {
        if (::adapter.isInitialized) {
            adapter.setSelected(pokemonId)
        }
    }
}
