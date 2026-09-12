package com.brotv.iptv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.brotv.iptv.data.model.PlaylistProfile

/**
 * Persists IPTV subscription profiles (list name / username / password / host)
 * encrypted at rest via a Keystore-backed MasterKey. Nothing here is ever
 * hardcoded, and nothing is written in plaintext SharedPreferences or logs.
 *
 * This store is intentionally per-app-install / per-device: it never syncs to
 * any backend, so credentials never leave the box except when the user
 * actively goes through the QR pairing handshake (see data/pairing/).
 */
class SecureCredentialStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "brotv_secure_profiles",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveProfile(profile: PlaylistProfile) {
        prefs.edit()
            .putString(KEY_LIST_NAME, profile.listName)
            .putString(KEY_USERNAME, profile.username)
            .putString(KEY_PASSWORD, profile.password)
            .putString(KEY_HOST, profile.hostUrl)
            .putString(KEY_ACTIVE_PROFILE, profile.listName)
            .apply()
    }

    fun loadActiveProfile(): PlaylistProfile? {
        val listName = prefs.getString(KEY_LIST_NAME, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        val host = prefs.getString(KEY_HOST, null) ?: return null
        return PlaylistProfile(listName, username, password, host)
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_LIST_NAME = "list_name"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_HOST = "host_url"
        const val KEY_ACTIVE_PROFILE = "active_profile"
    }
}
