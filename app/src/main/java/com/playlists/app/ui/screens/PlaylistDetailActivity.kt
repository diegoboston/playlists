package com.playlists.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.playlists.app.PlaylistsApp
import com.playlists.app.R
import com.playlists.app.data.PlaylistSongWithDetails
import com.playlists.app.databinding.ActivityPlaylistDetailBinding
import com.playlists.app.ui.PlaylistSongAdapter
import com.playlists.app.ui.ReorderTouchHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaylistDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlaylistDetailBinding
    private var playlistId: Long = -1
    private lateinit var adapter: PlaylistSongAdapter
    private lateinit var reorderHelper: ReorderTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)
        if (playlistId < 0) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        reorderHelper = ReorderTouchHelper(
            recyclerView = binding.list,
            getKey = { _ -> "" },
            onOrderChanged = { keys ->
                val songIds = keys.mapNotNull { it.toLongOrNull() }
                lifecycleScope.launch {
                    PlaylistsApp.from(application).playlistRepository.reorder(playlistId, songIds)
                }
            },
            onItemMoved = { from, to -> adapter.notifyItemMoved(from, to) },
        )

        adapter = PlaylistSongAdapter(reorderHelper) { entry ->
            lifecycleScope.launch {
                PlaylistsApp.from(application).playlistRepository.removeSong(entry.id)
            }
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.addSong.setOnClickListener { showAddSongDialog() }
        binding.play.setOnClickListener {
            startActivity(PlaylistPlaybackActivity.intent(this, playlistId))
        }
        binding.duplicate.setOnClickListener { duplicatePlaylist() }
        binding.rename.setOnClickListener { promptRename() }

        val repo = PlaylistsApp.from(application).playlistRepository
        lifecycleScope.launch {
            repo.getById(playlistId)?.let { playlist ->
                supportActionBar?.title = playlist.name
            }
        }
        lifecycleScope.launch {
            repo.observeSongs(playlistId).collectLatest { entries ->
                adapter.submitList(entries)
                reorderHelper.keys = entries.map { it.songId.toString() }
                binding.empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showAddSongDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_song, null)
        val searchInput = dialogView.findViewById<TextInputEditText>(R.id.searchInput)
        val resultsList = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.resultsList)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_song)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        val searchAdapter = com.playlists.app.ui.SongAdapter { song ->
            lifecycleScope.launch {
                PlaylistsApp.from(application).playlistRepository.addSong(playlistId, song.id)
                dialog.dismiss()
            }
        }
        resultsList.layoutManager = LinearLayoutManager(this)
        resultsList.adapter = searchAdapter

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                lifecycleScope.launch {
                    val results = PlaylistsApp.from(application).songRepository.search(s?.toString().orEmpty())
                    searchAdapter.submitList(results)
                }
            }
        })

        dialog.show()
        lifecycleScope.launch {
            val all = PlaylistsApp.from(application).songRepository.getAll()
            searchAdapter.submitList(all)
        }
    }

    private fun duplicatePlaylist() {
        lifecycleScope.launch {
            val newId = PlaylistsApp.from(application).playlistRepository.duplicate(playlistId)
            if (newId != null) {
                startActivity(intent(this@PlaylistDetailActivity, newId))
            }
        }
    }

    private fun promptRename() {
        val input = layoutInflater.inflate(R.layout.dialog_text_input, null) as TextInputEditText
        input.setText(supportActionBar?.title)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_playlist)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        PlaylistsApp.from(application).playlistRepository.rename(playlistId, name)
                        supportActionBar?.title = name
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val EXTRA_PLAYLIST_ID = "playlist_id"

        fun intent(context: Context, playlistId: Long): Intent =
            Intent(context, PlaylistDetailActivity::class.java).putExtra(EXTRA_PLAYLIST_ID, playlistId)
    }
}
