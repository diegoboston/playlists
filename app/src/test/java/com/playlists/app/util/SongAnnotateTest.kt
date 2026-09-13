package com.playlists.app.util

import android.content.Intent
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongAnnotateTest {
    @Test
    fun stampChanged_falseWhenUnchanged() {
        val file = File.createTempFile("annotate-stamp", ".pdf")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val before = SongAnnotate.stampOf(file)
            assertFalse(SongAnnotate.stampChanged(file, before))
        } finally {
            file.delete()
        }
    }

    @Test
    fun stampChanged_trueWhenLengthOrModifiedChanges() {
        val file = File.createTempFile("annotate-stamp-change", ".pdf")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val before = SongAnnotate.stampOf(file)
            file.writeBytes(byteArrayOf(9, 9, 9, 9, 9))
            file.setLastModified(before.lastModified + 1_000)
            assertTrue(SongAnnotate.stampChanged(file, before))
        } finally {
            file.delete()
        }
    }

    @Test
    fun editorFlags_openAsOwnDocumentTask() {
        val flags = SongAnnotate.EDITOR_FLAGS
        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_DOCUMENT != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK != 0)
        assertTrue(flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertTrue(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
