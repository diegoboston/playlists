package com.playlists.app.ui.screens

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.playlists.app.PlaylistsApp
import com.playlists.app.R
import com.playlists.app.databinding.ActivityQuickstartBinding
import com.playlists.app.util.QuickstartMatcher
import kotlinx.coroutines.launch

class QuickstartActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuickstartBinding
    private var matchedIds: List<Long> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickstartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.match.setOnClickListener { runMatch() }
        binding.create.setOnClickListener { createPlaylist() }
    }

    private fun runMatch() {
        val text = binding.input.text?.toString().orEmpty()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        lifecycleScope.launch {
            val archive = PlaylistsApp.from(application).songRepository.getAll()
            val results = QuickstartMatcher.matchLines(lines, archive)
            matchedIds = QuickstartMatcher.matchedSongIds(results)

            val summary = buildString {
                results.forEach { result ->
                    append("• ")
                    append(result.line)
                    append(" → ")
                    append(result.song?.title ?: "(no match)")
                    append('\n')
                }
            }
            binding.results.text = summary
            binding.create.visibility = if (matchedIds.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun createPlaylist() {
        if (matchedIds.isEmpty()) return
        val input = layoutInflater.inflate(R.layout.dialog_text_input, null) as TextInputEditText
        input.setText(getString(R.string.quickstart_default_name))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val repo = PlaylistsApp.from(application).playlistRepository
                    val id = repo.create(name)
                    repo.setSongs(id, matchedIds)
                    startActivity(PlaylistDetailActivity.intent(this@QuickstartActivity, id))
                    finish()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
