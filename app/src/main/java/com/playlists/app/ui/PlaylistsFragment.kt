package com.playlists.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.playlists.app.PlaylistsApp
import com.playlists.app.R
import com.playlists.app.databinding.FragmentPlaylistsBinding
import com.playlists.app.ui.screens.PlaylistDetailActivity
import com.playlists.app.ui.screens.QuickstartActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaylistsFragment : Fragment() {
    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PlaylistAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PlaylistAdapter { playlist ->
            startActivity(PlaylistDetailActivity.intent(requireContext(), playlist.id))
        }
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        binding.fabNew.setOnClickListener { promptNewPlaylist() }
        binding.quickstart.setOnClickListener {
            startActivity(Intent(requireContext(), QuickstartActivity::class.java))
        }

        val repo = PlaylistsApp.from(requireActivity().application).playlistRepository
        viewLifecycleOwner.lifecycleScope.launch {
            repo.observeAll().collectLatest { playlists ->
                adapter.submitList(playlists)
                binding.empty.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun promptNewPlaylist() {
        val input = layoutInflater.inflate(R.layout.dialog_text_input, null) as TextInputEditText
        input.hint = getString(R.string.playlist_name_hint)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val id = PlaylistsApp.from(requireActivity().application)
                            .playlistRepository.create(name)
                        startActivity(PlaylistDetailActivity.intent(requireContext(), id))
                    }
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
