package com.playlists.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.databinding.ItemPlaylistSongBinding

class PlaylistSongAdapter(
    private val reorderHelper: ReorderTouchHelper,
    private val onRemove: (PlaylistSongWithDetails) -> Unit,
) : ListAdapter<PlaylistSongWithDetails, PlaylistSongAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<PlaylistSongWithDetails>() {
        override fun areItemsTheSame(a: PlaylistSongWithDetails, b: PlaylistSongWithDetails) =
            a.id == b.id

        override fun areContentsTheSame(a: PlaylistSongWithDetails, b: PlaylistSongWithDetails) =
            a == b
    }

    init {
        reorderHelper.getKey = { pos ->
            if (pos in 0 until itemCount) getItem(pos).songId.toString() else ""
        }
    }

    inner class VH(private val binding: ItemPlaylistSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: PlaylistSongWithDetails) {
            binding.title.text = entry.title
            binding.subtitle.text =
                "Key: ${SongDisplay.preview(entry.keySignature)} · ${SongDisplay.preview(entry.notes)}"
            binding.remove.setOnClickListener { onRemove(entry) }
            reorderHelper.attachToViewHolder(this)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPlaylistSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
