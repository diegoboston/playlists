package com.playlists.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.playlists.app.PlaylistsApp
import com.playlists.app.R
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.databinding.ActivityPlaylistPlaybackBinding
import kotlinx.coroutines.launch

class PlaylistPlaybackActivity : AppCompatActivity(), SongPlaybackFragment.PageListener {
    private lateinit var binding: ActivityPlaylistPlaybackBinding
    private var songs: List<PlaylistSongWithDetails> = emptyList()
    private var currentPdfPage: Int? = null
    private var currentPdfPageCount: Int? = null

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
            binding.pager.adapter = PlaybackPagerAdapter(this@PlaylistPlaybackActivity, songs)
            binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentPdfPage = null
                    currentPdfPageCount = null
                    updateIndicator(position)
                }
            })
            updateIndicator(0)
        }
    }

    override fun onPdfPageChanged(songIndex: Int, page: Int, pageCount: Int) {
        if (binding.pager.currentItem != songIndex) return
        currentPdfPage = page
        currentPdfPageCount = pageCount
        updateIndicator(songIndex)
    }

    private fun updateIndicator(songPosition: Int) {
        if (songs.isEmpty()) return
        val song = songs[songPosition]
        val base = getString(
            R.string.playback_song_indicator,
            songPosition + 1,
            songs.size,
            song.title,
        )
        val pageCount = currentPdfPageCount
        val page = currentPdfPage
        binding.songInfo.text = if (pageCount != null && pageCount > 1 && page != null) {
            "$base · ${getString(R.string.playback_page_indicator, page + 1, pageCount)}"
        } else {
            base
        }
    }

    companion object {
        private const val EXTRA_PLAYLIST_ID = "playlist_id"

        fun intent(context: Context, playlistId: Long): Intent =
            Intent(context, PlaylistPlaybackActivity::class.java).putExtra(EXTRA_PLAYLIST_ID, playlistId)
    }
}
