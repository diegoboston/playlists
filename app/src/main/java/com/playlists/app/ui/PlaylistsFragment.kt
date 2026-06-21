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
import com.playlists.app.data.Playlist
import com.playlists.app.databinding.FragmentPlaylistsBinding
import com.playlists.app.ui.PlaylistColorPicker
import com.playlists.app.ui.screens.PlaylistDetailActivity
import com.playlists.app.ui.screens.QuickstartActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaylistsFragment : Fragment() {
    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PlaylistAdapter
    private lateinit var reorderHelper: ReorderTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        reorderHelper = ReorderTouchHelper(
            recyclerView = binding.list,
            onOrderChanged = { keys ->
                val ids = keys.mapNotNull { it.toLongOrNull() }
                viewLifecycleOwner.lifecycleScope.launch {
                    PlaylistsApp.from(requireActivity().application).playlistRepository.reorder(ids)
                }
            },
            onItemMoved = { from, to -> adapter.notifyItemMoved(from, to) },
        )

        adapter = PlaylistAdapter(
            reorderHelper = reorderHelper,
            onClick = { playlist ->
                startActivity(PlaylistDetailActivity.intent(requireContext(), playlist.id))
            },
            onEdit = { playlist -> promptRename(playlist) },
            onColor = { playlist -> showColorPicker(playlist) },
        )
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
                reorderHelper.keys = playlists.map { it.id.toString() }
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

    private fun promptRename(playlist: Playlist) {
        val input = layoutInflater.inflate(R.layout.dialog_text_input, null) as TextInputEditText
        input.setText(playlist.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rename_playlist)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        PlaylistsApp.from(requireActivity().application)
                            .playlistRepository.rename(playlist.id, name)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showColorPicker(playlist: Playlist) {
        PlaylistColorPicker.show(requireContext(), playlist.colorArgb) { argb ->
            viewLifecycleOwner.lifecycleScope.launch {
                PlaylistsApp.from(requireActivity().application)
                    .playlistRepository.setColor(playlist.id, argb)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
