package com.playlists.app.util

import java.io.File
import org.junit.Assert.assertEquals
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
    fun applyWorkingCopy_overwritesWhenStampChanges() {
        val dest = File.createTempFile("annotate-dest", ".pdf")
        val working = File.createTempFile("annotate-work", ".pdf")
        try {
            dest.writeBytes(byteArrayOf(1, 1, 1))
            working.writeBytes(byteArrayOf(1, 1, 1))
            val before = SongAnnotate.stampOf(working)
            working.writeBytes(byteArrayOf(9, 9, 9, 9))
            working.setLastModified(before.lastModified + 1_000)
            assertTrue(SongAnnotate.applyWorkingCopy(working, dest, before))
            assertEquals(listOf<Byte>(9, 9, 9, 9), dest.readBytes().toList())
        } finally {
            dest.delete()
            working.delete()
        }
    }

    @Test
    fun applyWorkingCopy_skipsWhenEditorDidNotWrite() {
        val dest = File.createTempFile("annotate-dest-skip", ".pdf")
        val working = File.createTempFile("annotate-work-skip", ".pdf")
        try {
            dest.writeBytes(byteArrayOf(5, 5))
            working.writeBytes(byteArrayOf(7, 7))
            val before = SongAnnotate.stampOf(working)
            assertFalse(SongAnnotate.applyWorkingCopy(working, dest, before))
            assertEquals(listOf<Byte>(5, 5), dest.readBytes().toList())
        } finally {
            dest.delete()
            working.delete()
        }
    }
}
