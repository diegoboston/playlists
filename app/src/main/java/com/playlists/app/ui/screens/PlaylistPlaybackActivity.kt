package com.playlists.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
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
            binding.pager.adapter = PlaybackAdapter(this@PlaylistPlaybackActivity, songs)
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
        private val activity: AppCompatActivity,
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
            when (FileType.valueOf(song.fileType)) {
                FileType.IMAGE -> {
                    val image = ImageView(holder.container.context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        load(file)
                    }
                    holder.container.addView(image)
                }
                FileType.PDF -> {
                    val pager = ViewPager2(holder.container.context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    }
                    holder.container.addView(pager)
                    activity.lifecycleScope.launch {
                        val count = withContext(Dispatchers.IO) { PdfHelper.pageCount(file) }
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
                                activity.lifecycleScope.launch {
                                    val bitmap = withContext(Dispatchers.IO) {
                                        val width = activity.resources.displayMetrics.widthPixels
                                        PdfHelper.renderPage(file, page, width)
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
