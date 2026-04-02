package com.example.poketmon_on_app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.poketmon_on_app.R
import com.example.poketmon_on_app.pet.PokemonData
import com.example.poketmon_on_app.pet.PokemonRepository
import kotlin.concurrent.thread

class PokemonAdapter(
    private val repository: PokemonRepository,
    private val onSelect: (PokemonData) -> Unit
) : RecyclerView.Adapter<PokemonAdapter.ViewHolder>() {

    private var items: List<PokemonData> = emptyList()
    private var selectedId: Int = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cellBackground: FrameLayout = view.findViewById(R.id.cellBackground)
        val spriteImage: ImageView = view.findViewById(R.id.spriteImage)
        val naText: TextView = view.findViewById(R.id.naText)
        val pokemonName: TextView = view.findViewById(R.id.pokemonName)
        val pokemonNum: TextView = view.findViewById(R.id.pokemonNum)
    }

    fun submitList(list: List<PokemonData>) {
        items = list
        notifyDataSetChanged()
    }

    fun setSelected(id: Int) {
        val oldId = selectedId
        selectedId = id
        items.indexOfFirst { it.id == oldId }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        items.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pokemon, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pokemon = items[position]
        val available = repository.isAvailable(pokemon.id)

        holder.pokemonName.text = pokemon.name
        holder.pokemonNum.text = pokemon.displayId

        // Make cell square
        holder.cellBackground.post {
            val width = holder.cellBackground.width
            if (width > 0) {
                val lp = holder.cellBackground.layoutParams
                lp.height = width
                holder.cellBackground.layoutParams = lp
            }
        }

        // Selection state
        val isSelected = pokemon.id == selectedId
        holder.cellBackground.setBackgroundResource(
            if (isSelected) R.drawable.bg_pokemon_cell_selected
            else R.drawable.bg_pokemon_cell
        )

        if (available) {
            holder.naText.visibility = View.GONE
            holder.spriteImage.visibility = View.VISIBLE
            holder.spriteImage.setImageBitmap(null)

            val pokemonId = pokemon.id
            thread {
                val thumbnail = repository.getPortrait(pokemonId)
                holder.spriteImage.post {
                    if (holder.adapterPosition != RecyclerView.NO_POSITION
                        && items.getOrNull(holder.adapterPosition)?.id == pokemonId
                    ) {
                        holder.spriteImage.setImageBitmap(thumbnail)
                    }
                }
            }

            holder.itemView.setOnClickListener {
                onSelect(pokemon)
            }
        } else {
            holder.spriteImage.visibility = View.GONE
            holder.naText.visibility = View.VISIBLE
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount() = items.size
}
