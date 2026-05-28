package com.glassbox.rotaportal.data.secure

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.glassbox.rotaportal.shared.TokenStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SecretManager(context: Context) : TokenStore {
    private val appContext = context.applicationContext
    private val dataStore = appContext.tokenDataStore
    private val aead by lazy { SecureAead.get(appContext) }
    private val cachedToken = AtomicReference<String?>(null)
    private val initialized = AtomicBoolean(false)

    override fun saveToken(token: String) {
        val normalizedToken = token.trim()
        require(normalizedToken.isNotEmpty()) { "Token must not be empty" }
        ensureLoaded()
        cachedToken.set(normalizedToken)
        runBlocking(Dispatchers.IO) {
            persistToken(normalizedToken)
        }
    }

    override fun getToken(): String? {
        ensureLoaded()
        return cachedToken.get()
    }

    override fun clearToken() {
        ensureLoaded()
        cachedToken.set(null)
        runBlocking(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences.remove(TokenPreferenceKeys.encryptedToken)
            }
        }
    }

    private fun ensureLoaded() {
        if (initialized.compareAndSet(false, true)) {
            runBlocking(Dispatchers.IO) {
                LegacyEncryptedPrefsMigrator.migrateIfNeeded(appContext, dataStore, aead)
                cachedToken.set(readTokenFromStore())
            }
        }
    }

    private suspend fun readTokenFromStore(): String? {
        val encrypted = dataStore.data.first()[TokenPreferenceKeys.encryptedToken] ?: return null
        return try {
            SecureAead.decrypt(aead, encrypted, tokenAssociatedData).trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            dataStore.edit { preferences ->
                preferences.remove(TokenPreferenceKeys.encryptedToken)
            }
            null
        }
    }

    private suspend fun persistToken(token: String) {
        val encrypted = SecureAead.encrypt(aead, token, tokenAssociatedData)
        dataStore.edit { preferences ->
            preferences[TokenPreferenceKeys.encryptedToken] = encrypted
        }
    }
}
