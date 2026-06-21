package com.playlists.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.playlists.app.R
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

    inner class VH(private val binding: ItemPlaylistSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: PlaylistSongWithDetails) {
            binding.title.text = entry.title
            binding.subtitle.text = SongDisplay.playlistLine(entry.keySignature, entry.notes)
            binding.subtitle.visibility =
                if (binding.subtitle.text.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            binding.remove.setOnClickListener { onRemove(entry) }
            reorderHelper.attachToViewHolder(this)

            val deletedColor = ContextCompat.getColor(binding.root.context, R.color.deleted_song)
            if (entry.isDeleted) {
                binding.title.setTextColor(deletedColor)
                binding.subtitle.setTextColor(deletedColor)
            } else {
                binding.title.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
                binding.subtitle.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
        }

        private fun themeColor(attr: Int): Int =
            MaterialColors.getColor(binding.root.context, attr, "PlaylistSongAdapter")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPlaylistSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
