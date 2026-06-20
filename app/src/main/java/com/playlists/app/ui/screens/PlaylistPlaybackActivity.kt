package com.playlists.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.chrisbanes.photoview.PhotoView
import com.playlists.app.PlaylistsApp
import com.playlists.app.data.FileType
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.databinding.ActivityPlaylistPlaybackBinding
import com.playlists.app.ui.PdfHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PlaylistPlaybackActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlaylistPlaybackBinding
    private var songs: List<PlaylistSongWithDetails> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)
        if (playlistId < 0) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        lifecycleScope.launch {
            songs = PlaylistsApp.from(application).playlistRepository.getSongs(playlistId)
            if (songs.isEmpty()) {
                finish()
                return@launch
            }
            val playlist = PlaylistsApp.from(application).playlistRepository.getById(playlistId)
            supportActionBar?.title = playlist?.name
            binding.pager.adapter = PlaybackAdapter(songs)
            binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = updateIndicator(position)
            })
            updateIndicator(0)
        }
    }

    private fun updateIndicator(position: Int) {
        if (songs.isEmpty()) return
        val song = songs[position]
        binding.songInfo.text = "${position + 1}/${songs.size}: ${song.title}"
    }

    private class PlaybackAdapter(
        private val songs: List<PlaylistSongWithDetails>,
    ) : RecyclerView.Adapter<PlaybackAdapter.VH>() {
        inner class VH(val container: FrameLayout) : RecyclerView.ViewHolder(container)

        override fun getItemCount() = songs.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val container = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            return VH(container)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val song = songs[position]
            holder.container.removeAllViews()
            val file = File(song.filePath)
            val context = holder.container.context
            when (FileType.valueOf(song.fileType)) {
                FileType.IMAGE -> {
                    val photo = PhotoView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                        setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                    }
                    holder.container.addView(photo)
                }
                FileType.PDF -> {
                    val pager = ViewPager2(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    }
                    holder.container.addView(pager)
                    (context as? AppCompatActivity)?.lifecycleScope?.launch {
                        val count = withContext(Dispatchers.IO) {
                            PdfHelper.pageCount(context, file)
                        }
                        pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                            override fun getItemCount() = count
                            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                                object : RecyclerView.ViewHolder(
                                    ImageView(parent.context).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                        adjustViewBounds = true
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                    }
                                ) {}

                            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, page: Int) {
                                val image = holder.itemView as ImageView
                                (context as? AppCompatActivity)?.lifecycleScope?.launch {
                                    val bitmap = withContext(Dispatchers.IO) {
                                        val width = context.resources.displayMetrics.widthPixels
                                        PdfHelper.renderPage(context, file, page, width)
                                    }
                                    bitmap?.let { image.setImageBitmap(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PLAYLIST_ID = "playlist_id"

        fun intent(context: Context, playlistId: Long): Intent =
            Intent(context, PlaylistPlaybackActivity::class.java).putExtra(EXTRA_PLAYLIST_ID, playlistId)
    }
}
