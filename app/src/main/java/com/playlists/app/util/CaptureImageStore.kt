package com.playlists.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import java.io.File

/** Temp JPEG for [MediaStore.ACTION_IMAGE_CAPTURE]. */
object CaptureImageStore {
    private const val DIR = "captures"
    private const val FILE_NAME = "pending_scan.jpg"

    fun pendingFile(context: Context): File =
        File(File(context.cacheDir, DIR).apply { mkdirs() }, FILE_NAME)

    fun pendingUri(context: Context): Uri {
        val file = pendingFile(context)
        if (!file.exists()) file.createNewFile()
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun prepareNewCapture(context: Context): Uri {
        val file = pendingFile(context)
        if (file.exists()) file.delete()
        file.createNewFile()
        return pendingUri(context)
    }

    fun isUsableCapture(file: File): Boolean = file.exists() && file.length() > 0L

    fun hasCameraApp(context: Context): Boolean =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .resolveActivity(context.packageManager) != null
}

/** System camera with a FileProvider [Uri], including write grants for camera apps. */
class CaptureImageContract : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent {
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input)
            addFlags(flags)
            clipData = android.content.ClipData.newUri(context.contentResolver, "image", input)
        }
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { resolve ->
                context.grantUriPermission(resolve.activityInfo.packageName, input, flags)
            }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}
