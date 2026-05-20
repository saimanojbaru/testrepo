package com.moldable.app.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureStore {
    private const val PREFS = "moldable_secure"
    private const val KEY_API = "anthropic_api_key"

    private fun prefs(ctx: Context): SharedPreferences {
        val master = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            PREFS,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString(KEY_API, key).apply()
    }

    fun getApiKey(ctx: Context): String? = prefs(ctx).getString(KEY_API, null)

    fun clearApiKey(ctx: Context) {
        prefs(ctx).edit().remove(KEY_API).apply()
    }
}
