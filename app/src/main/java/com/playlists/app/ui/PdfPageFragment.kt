package com.playlists.app.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.playlists.app.ui.screens.SongViewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfPagerAdapter(
    activity: SongViewActivity,
    private val file: File,
    private val pageCount: Int,
) : FragmentStateAdapter(activity) {
    override fun getItemCount() = pageCount

    override fun createFragment(position: Int): Fragment =
        PdfPageFragment.newInstance(file.absolutePath, position)
}

class PdfPageFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val image = ImageView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val path = requireArguments().getString(ARG_PATH) ?: return image
        val page = requireArguments().getInt(ARG_PAGE)
        val file = File(path)
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
                PdfHelper.renderPage(requireContext(), file, page, width)
            }
            bitmap?.let { image.setImageBitmap(it) }
        }
        return image
    }

    companion object {
        private const val ARG_PATH = "path"
        private const val ARG_PAGE = "page"

        fun newInstance(path: String, page: Int): PdfPageFragment =
            PdfPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATH, path)
                    putInt(ARG_PAGE, page)
                }
            }
    }
}
