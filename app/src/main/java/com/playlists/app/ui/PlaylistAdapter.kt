package com.playlists.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.playlists.app.R
import com.playlists.app.data.Playlist
import com.playlists.app.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private val reorderHelper: ReorderTouchHelper,
    private val onClick: (Playlist) -> Unit,
    private val onEdit: (Playlist) -> Unit,
    private val onColor: (Playlist) -> Unit,
) : ListAdapter<Playlist, PlaylistAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(a: Playlist, b: Playlist) = a.id == b.id
        override fun areContentsTheSame(a: Playlist, b: Playlist) = a == b
    }

    init {
        reorderHelper.getKey = { pos ->
            if (pos in 0 until itemCount) getItem(pos).id.toString() else ""
        }
    }

    inner class VH(private val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(playlist: Playlist) {
            binding.name.text = playlist.name
            binding.name.setOnClickListener { onClick(playlist) }
            binding.root.setOnClickListener { onClick(playlist) }
            binding.edit.setOnClickListener { onEdit(playlist) }
            binding.colorBubble.setOnClickListener { onColor(playlist) }

            val color = playlist.colorArgb
            if (color != null) {
                binding.accentStripe.setBackgroundColor(color)
                binding.colorBubble.background = PlaylistColorPicker.circleDrawable(binding.root.context, color)
            } else {
                binding.accentStripe.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.transparent),
                )
                binding.colorBubble.setBackgroundResource(R.drawable.playlist_color_bubble)
            }

            val card = binding.root
            if (color != null) {
                card.setCardBackgroundColor(adjustAlpha(color, 0.18f))
            } else {
                card.setCardBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurface))
            }

            reorderHelper.attachToViewHolder(this)
        }

        private fun adjustAlpha(argb: Int, alphaFraction: Float): Int {
            val alpha = (255 * alphaFraction).toInt().coerceIn(0, 255)
            return argb and 0x00FFFFFF or (alpha shl 24)
        }

        private fun themeColor(attr: Int): Int =
            MaterialColors.getColor(binding.root.context, attr, "PlaylistAdapter")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
