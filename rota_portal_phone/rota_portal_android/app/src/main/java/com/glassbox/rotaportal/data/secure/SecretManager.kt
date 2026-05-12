package com.glassbox.rotaportal.data.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.glassbox.rotaportal.shared.TokenStore

class SecretManager(context: Context) : TokenStore {
    private val appContext = context.applicationContext

    private val sharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun saveToken(token: String) {
        val normalizedToken = token.trim()
        require(normalizedToken.isNotEmpty()) { "Token must not be empty" }
        sharedPreferences.edit()
            .putString(KEY_OPSGENIE_TOKEN, normalizedToken)
            .apply()
    }

    override fun getToken(): String? {
        return sharedPreferences.getString(KEY_OPSGENIE_TOKEN, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    override fun clearToken() {
        sharedPreferences.edit()
            .remove(KEY_OPSGENIE_TOKEN)
            .apply()
    }

    companion object {
        private const val PREF_FILE = "rota_portal_secure_prefs"
        private const val KEY_OPSGENIE_TOKEN = "opsgenie_api_token"
    }
}
