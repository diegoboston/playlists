package com.playlists.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.playlists.app.PlaylistsApp
import com.playlists.app.data.FileType
import com.playlists.app.databinding.ActivitySongViewBinding
import com.playlists.app.ui.PdfHelper
import com.playlists.app.ui.PdfPagerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SongViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySongViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySongViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val songId = intent.getLongExtra(EXTRA_SONG_ID, -1L)
        if (songId < 0) {
            finish()
            return
        }

        lifecycleScope.launch {
            val song = PlaylistsApp.from(application).songRepository.getById(songId) ?: run {
                finish()
                return@launch
            }
            title = song.title
            val file = File(song.filePath)
            if (!file.exists()) {
                finish()
                return@launch
            }
            when (FileType.valueOf(song.fileType)) {
                FileType.IMAGE -> showImage(file)
                FileType.PDF -> showPdf(file)
            }
        }
    }

    private fun showImage(file: File) {
        binding.imageView.visibility = View.VISIBLE
        binding.pdfPager.visibility = View.GONE
        binding.pageIndicator.visibility = View.GONE
        binding.imageView.load(file)
    }

    private fun showPdf(file: File) {
        binding.imageView.visibility = View.GONE
        binding.pdfPager.visibility = View.VISIBLE
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { PdfHelper.pageCount(file) }
            if (count <= 0) {
                finish()
                return@launch
            }
            binding.pdfPager.adapter = PdfPagerAdapter(this@SongViewActivity, file, count, zoomEnabled = true)
            binding.pageIndicator.visibility = if (count > 1) View.VISIBLE else View.GONE
            binding.pdfPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.pageIndicator.text = "${position + 1} / $count"
                }
            })
            binding.pageIndicator.text = "1 / $count"
        }
    }

    companion object {
        private const val EXTRA_SONG_ID = "song_id"

        fun intent(context: Context, songId: Long): Intent =
            Intent(context, SongViewActivity::class.java).putExtra(EXTRA_SONG_ID, songId)
    }
}
