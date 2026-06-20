package com.playlists.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.playlists.app.PlaylistsApp
import com.playlists.app.databinding.ActivityImportSongBinding
import com.playlists.app.util.PendingImport
import com.playlists.app.util.ShareImporter
import kotlinx.coroutines.launch

class ImportSongActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImportSongBinding
    private lateinit var pending: PendingImport

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportSongBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_PENDING, PendingImport::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_PENDING) as? PendingImport
        } ?: run {
            finish()
            return
        }

        binding.titleInput.setText(pending.suggestedTitle)
        binding.save.setOnClickListener { saveAndFinish() }
        binding.cancel.setOnClickListener { finish() }
    }

    private fun saveAndFinish() {
        lifecycleScope.launch {
            ShareImporter.saveSong(
                repository = PlaylistsApp.from(application).songRepository,
                pending = pending,
                title = binding.titleInput.text?.toString().orEmpty(),
                keySignature = binding.keyInput.text?.toString().orEmpty(),
                notes = binding.notesInput.text?.toString().orEmpty(),
            )
            setResult(RESULT_OK)
            finish()
        }
    }

    companion object {
        private const val EXTRA_PENDING = "pending"

        fun intent(context: Context, pending: PendingImport): Intent =
            Intent(context, ImportSongActivity::class.java).putExtra(EXTRA_PENDING, pending)
    }
}
