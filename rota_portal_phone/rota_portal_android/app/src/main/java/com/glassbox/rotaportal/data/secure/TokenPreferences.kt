package com.glassbox.rotaportal.data.secure

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.nio.charset.StandardCharsets

internal val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "rota_portal_token_store",
)

internal object TokenPreferenceKeys {
    val encryptedToken = stringPreferencesKey("encrypted_opsgenie_token")
}

internal val tokenAssociatedData: ByteArray =
    "rota_portal_token_store".toByteArray(StandardCharsets.UTF_8)
