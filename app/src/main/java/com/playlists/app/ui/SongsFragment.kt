package com.playlists.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.playlists.app.PlaylistsApp
import com.playlists.app.R
import com.playlists.app.databinding.FragmentSongsBinding
import com.playlists.app.ui.screens.SongViewActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SongsFragment : Fragment() {
    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SongAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            onClick = { song ->
                startActivity(SongViewActivity.intent(requireContext(), song.id))
            },
            showDelete = true,
            onDelete = { song -> confirmDelete(song) },
        )
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        val repo = PlaylistsApp.from(requireActivity().application).songRepository
        viewLifecycleOwner.lifecycleScope.launch {
            repo.observeAll().collectLatest { songs ->
                adapter.submitList(songs)
                binding.empty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun confirmDelete(song: com.playlists.app.data.Song) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_song)
            .setMessage(getString(R.string.delete_song_confirm, song.title))
            .setPositiveButton(R.string.delete_song) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    PlaylistsApp.from(requireActivity().application).songRepository.delete(song.id)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
