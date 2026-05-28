@file:Suppress("DEPRECATION")

package com.glassbox.rotaportal.data.secure

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import kotlinx.coroutines.flow.first

/**
 * One-time migration from the deprecated EncryptedSharedPreferences store.
 * Uses security-crypto 1.0.x APIs (still available, not deprecated on that line).
 */
internal object LegacyEncryptedPrefsMigrator {
    private const val LEGACY_PREF_FILE = "rota_portal_secure_prefs"
    private const val LEGACY_KEY = "opsgenie_api_token"

    suspend fun migrateIfNeeded(
        context: Context,
        dataStore: DataStore<Preferences>,
        aead: com.google.crypto.tink.Aead,
    ) {
        if (dataStore.data.first()[TokenPreferenceKeys.encryptedToken] != null) {
            return
        }
        if (!legacyPrefsExist(context)) {
            return
        }

        val legacyToken = readLegacyToken(context) ?: return
        val encrypted = SecureAead.encrypt(aead, legacyToken, tokenAssociatedData)
        dataStore.edit { preferences ->
            preferences[TokenPreferenceKeys.encryptedToken] = encrypted
        }
        context.deleteSharedPreferences(LEGACY_PREF_FILE)
    }

    private fun legacyPrefsExist(context: Context): Boolean {
        val legacyFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/$LEGACY_PREF_FILE.xml",
        )
        return legacyFile.exists()
    }

    private fun readLegacyToken(context: Context): String? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val legacyPrefs = EncryptedSharedPreferences.create(
                context,
                LEGACY_PREF_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            legacyPrefs.getString(LEGACY_KEY, null)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
