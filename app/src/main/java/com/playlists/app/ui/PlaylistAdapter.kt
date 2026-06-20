package com.playlists.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.playlists.app.data.Playlist
import com.playlists.app.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private val onClick: (Playlist) -> Unit,
) : ListAdapter<Playlist, PlaylistAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(a: Playlist, b: Playlist) = a.id == b.id
        override fun areContentsTheSame(a: Playlist, b: Playlist) = a == b
    }

    inner class VH(private val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(playlist: Playlist) {
            binding.name.text = playlist.name
            binding.root.setOnClickListener { onClick(playlist) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
