package com.playlists.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File {
        stop()
        val file = File(context.cacheDir, "chart-assistant-${System.currentTimeMillis()}.m4a")
        outputFile = file
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        return file
    }

    fun stop(): File? {
        val file = outputFile
        runCatching {
            recorder?.apply {
                stop()
                release()
            }
        }
        recorder = null
        outputFile = null
        return file?.takeIf { it.exists() && it.length() > 0 }
    }

    fun isRecording(): Boolean = recorder != null
}
