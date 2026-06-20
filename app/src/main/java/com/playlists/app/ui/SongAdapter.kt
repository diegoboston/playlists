package com.playlists.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.playlists.app.data.Song
import com.playlists.app.databinding.ItemSongBinding

class SongAdapter(
    private val onClick: (Song) -> Unit,
) : ListAdapter<Song, SongAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(a: Song, b: Song) = a.id == b.id
        override fun areContentsTheSame(a: Song, b: Song) = a == b
    }

    inner class VH(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.title.text = song.title
            binding.subtitle.text = SongDisplay.subtitle(song)
            binding.typeBadge.text = song.fileType
            binding.root.setOnClickListener { onClick(song) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
