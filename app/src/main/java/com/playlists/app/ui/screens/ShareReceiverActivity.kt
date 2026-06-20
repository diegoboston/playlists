package com.playlists.app.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.playlists.app.databinding.ActivityShareReceiverBinding
import com.playlists.app.ui.MainActivity
import com.playlists.app.util.ShareImporter
import kotlinx.coroutines.launch

class ShareReceiverActivity : AppCompatActivity() {
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityShareReceiverBinding.inflate(layoutInflater).root)

        lifecycleScope.launch {
            val pending = ShareImporter.parseIntent(this@ShareReceiverActivity, intent)
            if (pending == null) {
                finish()
                return@launch
            }
            importLauncher.launch(ImportSongActivity.intent(this@ShareReceiverActivity, pending))
        }
    }
}
