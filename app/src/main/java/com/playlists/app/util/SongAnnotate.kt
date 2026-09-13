package com.playlists.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import com.playlists.app.data.FileType
import com.playlists.app.data.Song
import com.playlists.app.render.ImagePagesPdf
import com.playlists.app.ui.PdfHelper
import java.io.File

data class FileStamp(val length: Long, val lastModified: Long)

object SongAnnotate {
    const val XODO_PACKAGE = "com.xodo.pdf.reader"
    const val MIME_PDF = "application/pdf"

    private const val DIR = "annotate"
    private const val FILE_NAME = "pending.pdf"

    fun playStoreUri(): Uri =
        Uri.parse("https://play.google.com/store/apps/details?id=$XODO_PACKAGE")

    fun pendingFile(context: Context): File =
        File(File(context.cacheDir, DIR).apply { mkdirs() }, FILE_NAME)

    fun pendingUri(context: Context): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pendingFile(context),
        )

    fun stampOf(file: File): FileStamp =
        FileStamp(file.length(), file.lastModified())

    fun stampChanged(file: File, before: FileStamp): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return file.length() != before.length || file.lastModified() != before.lastModified
    }

    fun prepareWorkingCopy(context: Context, sourcePdf: File): Pair<Uri, FileStamp>? {
        if (!sourcePdf.isFile || sourcePdf.length() <= 0L) return null
        val working = pendingFile(context)
        if (working.exists()) working.delete()
        sourcePdf.copyTo(working, overwrite = true)
        return pendingUri(context) to stampOf(working)
    }

    fun applyWorkingCopy(working: File, dest: File, before: FileStamp): Boolean {
        if (!stampChanged(working, before)) return false
        dest.parentFile?.mkdirs()
        working.copyTo(dest, overwrite = true)
        PdfHelper.invalidate(dest)
        return true
    }

    fun isXodoInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(XODO_PACKAGE, 0)
            true
        }.getOrDefault(false)

    fun hasPdfEditor(context: Context): Boolean =
        Intent(Intent.ACTION_EDIT)
            .setType(MIME_PDF)
            .resolveActivity(context.packageManager) != null

    fun convertImageToCanonicalPdf(song: Song, sharedPathCount: Int): Song? {
        val image = SongStoragePaths.resolve(song.filePath)
        if (!image.isFile) return null
        val dest = File(
            StageManagerStorage.songsDir().apply { mkdirs() },
            SongFileNames.mediaFileName(song.title, song.id, "pdf"),
        )
        val written = ImagePagesPdf.write(listOf(image))
        if (written.absolutePath != dest.absolutePath) {
            written.copyTo(dest, overwrite = true)
            written.delete()
        }
        if (!dest.isFile || dest.length() <= 0L) return null
        val newStored = SongStoragePaths.toStoredPath(dest)
        val chartFrom = ChartDraftStore.resolveChartFile(song.filePath)
        if (chartFrom.isFile) {
            chartFrom.renameTo(ChartDraftStore.resolveChartFile(newStored))
        }
        if (sharedPathCount <= 1) {
            image.delete()
        }
        return song.copy(filePath = newStored, fileType = FileType.PDF.name)
    }
}

data class AnnotatePdfRequest(
    val uri: Uri,
    val targetPackage: String?,
)

class AnnotatePdfContract : ActivityResultContract<AnnotatePdfRequest, Unit>() {
    override fun createIntent(context: Context, input: AnnotatePdfRequest): Intent {
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(input.uri, SongAnnotate.MIME_PDF)
            addFlags(flags)
            clipData = android.content.ClipData.newUri(context.contentResolver, "pdf", input.uri)
            input.targetPackage?.let { setPackage(it) }
        }
        val targets = if (input.targetPackage != null) {
            listOf(input.targetPackage)
        } else {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }
        }
        targets.forEach { pkg ->
            context.grantUriPermission(pkg, input.uri, flags)
        }
        return if (input.targetPackage == null) {
            Intent.createChooser(intent, context.getString(com.playlists.app.R.string.annotate))
        } else {
            intent
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Unit = Unit
}
