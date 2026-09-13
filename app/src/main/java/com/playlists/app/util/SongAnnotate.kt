package com.playlists.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.playlists.app.data.FileType
import com.playlists.app.data.Song
import com.playlists.app.render.ImagePagesPdf
import java.io.File

data class FileStamp(val length: Long, val lastModified: Long)

object SongAnnotate {
    const val XODO_PACKAGE = "com.xodo.pdf.reader"
    const val MIME_PDF = "application/pdf"

    const val EDITOR_FLAGS =
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK

    fun playStoreUri(): Uri =
        Uri.parse("https://play.google.com/store/apps/details?id=$XODO_PACKAGE")

    fun stampOf(file: File): FileStamp =
        FileStamp(file.length(), file.lastModified())

    fun stampChanged(file: File, before: FileStamp): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return file.length() != before.length || file.lastModified() != before.lastModified
    }

    fun songUri(context: Context, file: File): Uri? {
        if (!file.isFile || file.length() <= 0L) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun editPdfIntent(uri: Uri, targetPackage: String?): Intent =
        Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, MIME_PDF)
            addFlags(EDITOR_FLAGS)
            targetPackage?.let { setPackage(it) }
        }

    fun editorIntent(context: Context, uri: Uri, targetPackage: String?): Intent {
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        val intent = editPdfIntent(uri, targetPackage).apply {
            clipData = android.content.ClipData.newUri(context.contentResolver, "pdf", uri)
        }
        val targets = if (targetPackage != null) {
            listOf(targetPackage)
        } else {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }
        }
        targets.forEach { pkg ->
            context.grantUriPermission(pkg, uri, flags)
        }
        return if (targetPackage == null) {
            Intent.createChooser(intent, context.getString(com.playlists.app.R.string.annotate))
        } else {
            intent
        }
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
