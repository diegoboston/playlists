package com.playlists.app.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.playlists.app.databinding.ActivityShareReceiverBinding
import com.playlists.app.util.ShareImporter
import kotlinx.coroutines.launch

class ShareReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityShareReceiverBinding.inflate(layoutInflater).root)

        lifecycleScope.launch {
            val pending = ShareImporter.parseIntent(this@ShareReceiverActivity, intent)
            if (pending == null) {
                finish()
                return@launch
            }
            startActivityForResult(ImportSongActivity.intent(this@ShareReceiverActivity, pending), REQ_IMPORT)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_IMPORT) {
            startActivity(Intent(this, com.playlists.app.ui.MainActivity::class.java))
            finish()
        }
    }

    companion object {
        private const val REQ_IMPORT = 1001
    }
}
