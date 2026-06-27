package com.playlists.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIcon(val prefValue: String, private val aliasClass: String) {
    Default("default", "ui.MainActivityDefault"),
    Alt("alt", "ui.MainActivityAltIcon"),
    ;

    fun componentName(context: Context): ComponentName =
        ComponentName(context.packageName, "${context.packageName}.$aliasClass")

    companion object {
        fun fromPrefValue(value: String?): AppIcon =
            entries.firstOrNull { it.prefValue == value } ?: Default
    }
}

object AppIconManager {
    private const val PREFS = "playlists_prefs"
    private const val KEY_APP_ICON = "app_icon"

    fun getSelected(context: Context): AppIcon {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppIcon.fromPrefValue(prefs.getString(KEY_APP_ICON, null))
    }

    fun setSelected(context: Context, icon: AppIcon) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_ICON, icon.prefValue)
            .apply()
        apply(context, icon)
    }

    fun applySaved(context: Context) {
        apply(context, getSelected(context))
    }

    private fun apply(context: Context, selected: AppIcon) {
        val pm = context.packageManager
        for (icon in AppIcon.entries) {
            val enabled = icon == selected
            pm.setComponentEnabledSetting(
                icon.componentName(context),
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
