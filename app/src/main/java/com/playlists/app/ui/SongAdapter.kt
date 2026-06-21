package com.playlists.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.playlists.app.data.FileType
import com.playlists.app.data.Song
import com.playlists.app.databinding.ItemSongBinding
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SongAdapter(
    private val scope: CoroutineScope,
    private val onClick: (Song) -> Unit,
    private val showDelete: Boolean = false,
    private val onDelete: ((Song) -> Unit)? = null,
    private val reorderHelper: ReorderTouchHelper? = null,
) : ListAdapter<Song, SongAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(a: Song, b: Song) = a.id == b.id
        override fun areContentsTheSame(a: Song, b: Song) = a == b
    }

    inner class VH(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.title.text = song.title
            binding.subtitle.text = SongDisplay.subtitle(song)
            binding.typeBadge.text = SongDisplay.typeBadge(song)
            binding.content.setOnClickListener { onClick(song) }
            binding.delete.visibility = if (showDelete) View.VISIBLE else View.GONE
            binding.delete.setOnClickListener { onDelete?.invoke(song) }
            reorderHelper?.attachToViewHolder(this)

            binding.root.tag = song.id
            scope.launch {
                val pages = withContext(Dispatchers.IO) {
                    PdfHelper.pageCount(File(song.filePath), FileType.valueOf(song.fileType))
                }
                if (binding.root.tag != song.id) return@launch
                binding.subtitle.text = SongDisplay.subtitle(song, pages)
                binding.typeBadge.text = SongDisplay.typeBadge(song, pages)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
