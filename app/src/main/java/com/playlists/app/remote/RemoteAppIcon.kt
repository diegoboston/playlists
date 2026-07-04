package com.playlists.app.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.playlists.app.util.AppIconManager
import java.io.ByteArrayOutputStream

object RemoteAppIcon {
    fun pngBytes(context: Context): ByteArray? {
        val icon = AppIconManager.getSelected(context)
        val drawable = context.packageManager.getActivityIcon(icon.componentName(context))
        val bitmap = drawableToBitmap(drawable) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        } finally {
            if (drawable !is BitmapDrawable || drawable.bitmap !== bitmap) {
                bitmap.recycle()
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            if (bitmap != null) return bitmap
        }
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }
}
