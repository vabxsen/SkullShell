package dev.aicli.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Application-managed secrets (provider API keys, cached auth tokens) at rest, backed by the
 * Android Keystore via [EncryptedSharedPreferences] (AES256-GCM value encryption, AES256-SIV key
 * encryption; the master key itself never leaves the Keystore's hardware/TEE boundary on
 * devices that support it). This is the *only* place in the app that should persist a credential
 * — never DataStore, never a plain file, never a log line (see AppLogger.redact, which exists
 * as defense-in-depth, not as the primary control).
 */
class SecretStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "aicli_secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun get(key: String): String? = prefs.getString(key, null)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun has(key: String): Boolean = prefs.contains(key)

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        fun providerAuthKey(providerId: String) = "auth_$providerId"
    }
}
