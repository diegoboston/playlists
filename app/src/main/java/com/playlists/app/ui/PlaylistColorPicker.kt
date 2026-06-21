package com.playlists.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.playlists.app.R

object PlaylistColorPicker {
    fun show(
        context: Context,
        selectedArgb: Int?,
        onPick: (Int?) -> Unit,
    ): AlertDialog {
        var dialog: AlertDialog? = null
        val density = context.resources.displayMetrics.density
        val size = (44 * density).toInt()
        val margin = (4 * density).toInt()
        val grid = GridLayout(context).apply {
            columnCount = 5
            PlaylistAccentColors.palette.forEach { argb ->
                addView(colorSwatch(context, argb, size, margin, argb == selectedArgb) {
                    onPick(argb)
                    dialog?.dismiss()
                })
            }
            addView(clearSwatch(context, size, margin, selectedArgb == null) {
                onPick(null)
                dialog?.dismiss()
            })
        }
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.playlist_color_title)
            .setView(grid)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        return dialog!!
    }

    fun circleDrawable(context: Context, fillColor: Int?, strokeOnly: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (fillColor != null && !strokeOnly) {
                setColor(fillColor)
                setStroke(2, ContextCompat.getColor(context, R.color.playlist_color_bubble_border_on))
            } else {
                setColor(ContextCompat.getColor(context, android.R.color.transparent))
                setStroke(2, themeColor(context, com.google.android.material.R.attr.colorOutline))
            }
        }

    private fun colorSwatch(
        context: Context,
        argb: Int,
        size: Int,
        margin: Int,
        selected: Boolean,
        onPick: () -> Unit,
    ): View {
        val params = GridLayout.LayoutParams().apply {
            width = size
            height = size
            setMargins(margin, margin, margin, margin)
        }
        return View(context).apply {
            layoutParams = params
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(argb)
                if (selected) {
                    setStroke(
                        (3 * context.resources.displayMetrics.density).toInt(),
                        ContextCompat.getColor(context, R.color.primary),
                    )
                }
            }
            setOnClickListener { onPick() }
        }
    }

    private fun clearSwatch(
        context: Context,
        size: Int,
        margin: Int,
        selected: Boolean,
        onPick: () -> Unit,
    ): View {
        val params = GridLayout.LayoutParams().apply {
            width = size
            height = size
            setMargins(margin, margin, margin, margin)
        }
        return LinearLayout(context).apply {
            layoutParams = params
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, android.R.color.transparent))
                setStroke(
                    (2 * context.resources.displayMetrics.density).toInt(),
                    if (selected) {
                        ContextCompat.getColor(context, R.color.primary)
                    } else {
                        themeColor(context, com.google.android.material.R.attr.colorOutline)
                    },
                )
            }
            setOnClickListener { onPick() }
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (size * 0.55f).toInt(),
                    (2 * context.resources.displayMetrics.density).toInt(),
                ).apply { gravity = Gravity.CENTER }
                setBackgroundColor(themeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant))
                rotation = 45f
            })
        }
    }

    private fun themeColor(context: Context, attr: Int): Int =
        MaterialColors.getColor(context, attr, "PlaylistColorPicker")
}
