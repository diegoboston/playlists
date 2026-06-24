package com.playlists.app.remote

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class RemotePlayService : Service() {
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                PlayRemoteController.teardownResources()
                stopForegroundSafely()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val name = intent.getStringExtra(EXTRA_PLAYLIST_NAME).orEmpty()
                val notification = RemotePlayNotification.build(this, name)
                startForeground(RemotePlayNotification.NOTIFICATION_ID, notification)
                inForeground = true
                return START_STICKY
            }
            else -> {
                // System restarted the service without our start intent — sync controller state.
                PlayRemoteController.teardownResources()
                stopForegroundSafely()
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopForegroundSafely()
        PlayRemoteController.teardownResources()
        super.onDestroy()
    }

    private fun stopForegroundSafely() {
        if (!inForeground) return
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        } finally {
            inForeground = false
        }
    }

    companion object {
        const val ACTION_START = "com.playlists.app.remote.START"
        const val ACTION_STOP = "com.playlists.app.remote.STOP"
        const val EXTRA_PLAYLIST_NAME = "playlist_name"

        fun start(context: Context, playlistName: String) {
            val intent = Intent(context, RemotePlayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PLAYLIST_NAME, playlistName)
            }
            context.startForegroundService(intent)
        }

        fun requestStop(context: Context) {
            val intent = Intent(context, RemotePlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
