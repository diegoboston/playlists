package com.playlists.app.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.playlists.app.data.FileType
import com.playlists.app.databinding.FragmentSongPlaybackBinding
import com.playlists.app.ui.PdfHelper
import com.playlists.app.ui.PdfPagerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SongPlaybackFragment : Fragment() {
    interface PageListener {
        fun onPdfPageChanged(songIndex: Int, page: Int, pageCount: Int)
    }

    private var _binding: FragmentSongPlaybackBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSongPlaybackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val filePath = requireArguments().getString(ARG_FILE_PATH) ?: return
        val fileType = FileType.valueOf(requireArguments().getString(ARG_FILE_TYPE) ?: return)
        val songIndex = requireArguments().getInt(ARG_SONG_INDEX)
        val file = File(filePath)
        if (!file.exists()) return

        when (fileType) {
            FileType.IMAGE -> showImage(file)
            FileType.PDF -> showPdf(file, songIndex)
        }
    }

    private fun showImage(file: File) {
        binding.imageView.visibility = View.VISIBLE
        binding.pdfHost.visibility = View.GONE
        binding.imageView.load(file)
    }

    private fun showPdf(file: File, songIndex: Int) {
        binding.imageView.visibility = View.GONE
        binding.pdfHost.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { PdfHelper.pageCount(file) }
            if (count <= 0) return@launch

            binding.pdfPager.adapter = PdfPagerAdapter(requireActivity(), file, count, zoomEnabled = true)
            reportPage(songIndex, 0, count)

            binding.pdfPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = reportPage(songIndex, position, count)
            })
        }
    }

    private fun reportPage(songIndex: Int, page: Int, pageCount: Int) {
        (activity as? PageListener)?.onPdfPageChanged(songIndex, page, pageCount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_FILE_PATH = "file_path"
        private const val ARG_FILE_TYPE = "file_type"
        private const val ARG_SONG_INDEX = "song_index"

        fun newInstance(songIndex: Int, filePath: String, fileType: String): SongPlaybackFragment =
            SongPlaybackFragment().apply {
                arguments = bundleOf(
                    ARG_SONG_INDEX to songIndex,
                    ARG_FILE_PATH to filePath,
                    ARG_FILE_TYPE to fileType,
                )
            }
    }
}

class PlaybackPagerAdapter(
    activity: PlaylistPlaybackActivity,
    private val songs: List<com.playlists.app.data.PlaylistSongWithDetails>,
) : androidx.viewpager2.adapter.FragmentStateAdapter(activity) {
    override fun getItemCount() = songs.size

    override fun createFragment(position: Int): Fragment {
        val song = songs[position]
        return SongPlaybackFragment.newInstance(position, song.filePath, song.fileType)
    }
}
