package com.playlists.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfPagerAdapter(
    activity: FragmentActivity,
    private val file: File,
    private val pageCount: Int,
    private val zoomEnabled: Boolean = false,
) : FragmentStateAdapter(activity) {
    override fun getItemCount() = pageCount

    override fun createFragment(position: Int): Fragment =
        PdfPageFragment.newInstance(file.absolutePath, position, zoomEnabled)
}

class PdfPageFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val zoomEnabled = requireArguments().getBoolean(ARG_ZOOM, false)
        val image: View = if (zoomEnabled) {
            ZoomImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        } else {
            android.widget.ImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                adjustViewBounds = true
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
        }
        val path = requireArguments().getString(ARG_PATH) ?: return image
        val page = requireArguments().getInt(ARG_PAGE)
        val file = File(path)
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val width = resources.displayMetrics.widthPixels.coerceAtLeast(1)
                PdfHelper.renderPage(file, page, width)
            }
            when (image) {
                is ZoomImageView -> bitmap?.let { image.setImageBitmap(it) }
                is android.widget.ImageView -> bitmap?.let { image.setImageBitmap(it) }
            }
        }
        return image
    }

    companion object {
        private const val ARG_PATH = "path"
        private const val ARG_PAGE = "page"
        private const val ARG_ZOOM = "zoom"

        fun newInstance(path: String, page: Int, zoomEnabled: Boolean = false): PdfPageFragment =
            PdfPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PATH, path)
                    putInt(ARG_PAGE, page)
                    putBoolean(ARG_ZOOM, zoomEnabled)
                }
            }
    }
}
