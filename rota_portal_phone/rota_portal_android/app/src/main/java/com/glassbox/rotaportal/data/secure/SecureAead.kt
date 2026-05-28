package com.glassbox.rotaportal.data.secure

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.nio.charset.StandardCharsets

internal object SecureAead {
    private const val KEYSET_NAME = "rota_portal_keyset"
    private const val KEYSET_PREF = "rota_portal_keyset_prefs"
    private const val MASTER_KEY_URI = "android-keystore://rota_portal_master_key"

    @Volatile
    private var instance: Aead? = null

    fun get(context: Context): Aead {
        return instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }
    }

    fun encrypt(aead: Aead, plaintext: String, associatedData: ByteArray): String {
        val ciphertext = aead.encrypt(plaintext.toByteArray(StandardCharsets.UTF_8), associatedData)
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    fun decrypt(aead: Aead, encoded: String, associatedData: ByteArray): String {
        val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
        return String(aead.decrypt(ciphertext, associatedData), StandardCharsets.UTF_8)
    }

    private fun create(context: Context): Aead {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREF)
            .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }
}
