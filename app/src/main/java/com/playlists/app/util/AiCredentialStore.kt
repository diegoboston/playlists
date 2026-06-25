package com.playlists.app.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object AiCredentialStore {
    private const val PREFS = "ai_credentials"
    private const val KEY_OPENAI = "openai_api_key"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getOpenAiApiKey(context: Context): String? =
        prefs(context).getString(KEY_OPENAI, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setOpenAiApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_OPENAI, key.trim()).apply()
    }

    fun hasOpenAiApiKey(context: Context): Boolean = getOpenAiApiKey(context) != null
}
