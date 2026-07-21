package dev.sfpixel.tailscaleswitch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SsidRepository(private val context: Context) {
    private val trustedSsidsKey = stringSetPreferencesKey("trusted_ssids")
    private val serviceEnabledKey = androidx.datastore.preferences.core.booleanPreferencesKey("service_enabled")

    val trustedSsids: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[trustedSsidsKey] ?: emptySet()
        }

    val isServiceEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[serviceEnabledKey] ?: false
        }

    suspend fun addSsid(ssid: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[trustedSsidsKey] ?: emptySet()
            preferences[trustedSsidsKey] = current + ssid
        }
    }

    suspend fun removeSsid(ssid: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[trustedSsidsKey] ?: emptySet()
            preferences[trustedSsidsKey] = current - ssid
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[serviceEnabledKey] = enabled
        }
    }
}
