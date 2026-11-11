package dev.sfpixel.tailscaleswitch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "history")

enum class ActionType {
    CONNECT,
    DISCONNECT,
    NETWORK_CHANGE,
    TIMER,
    SERVICE
}

data class ActionLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: ActionType,
    val message: String,
    val details: String? = null,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("type", type.name)
            put("message", message)
            details?.let { put("details", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ActionLogEntry {
            return ActionLogEntry(
                id = json.optString("id", UUID.randomUUID().toString()),
                timestamp = json.getLong("timestamp"),
                type = try {
                    ActionType.valueOf(json.getString("type"))
                } catch (_: Exception) {
                    ActionType.SERVICE
                },
                message = json.getString("message"),
                details = json.optString("details").takeIf { it.isNotBlank() }
            )
        }
    }
}

class HistoryRepository(private val context: Context) {
    private val logsKey = stringPreferencesKey("action_logs")
    private val maxRetentionMillis = 24 * 60 * 60 * 1000L // 24 hours

    val recentLogs: Flow<List<ActionLogEntry>> = context.historyDataStore.data
        .map { preferences ->
            val jsonString = preferences[logsKey] ?: "[]"
            val now = System.currentTimeMillis()
            val cutoff = now - maxRetentionMillis
            parseAndFilterLogs(jsonString, cutoff)
        }

    suspend fun logAction(type: ActionType, message: String, details: String? = null) {
        val newEntry = ActionLogEntry(
            type = type,
            message = message,
            details = details,
        )
        context.historyDataStore.edit { preferences ->
            val currentJsonString = preferences[logsKey] ?: "[]"
            val now = System.currentTimeMillis()
            val cutoff = now - maxRetentionMillis
            val currentList = parseAndFilterLogs(currentJsonString, cutoff).toMutableList()

            // Add new entry at beginning
            currentList.add(0, newEntry)

            // Limit maximum entries stored to 300
            val trimmedList = currentList.take(300)

            val array = JSONArray()
            trimmedList.forEach { entry ->
                array.put(entry.toJson())
            }
            preferences[logsKey] = array.toString()
        }
    }

    suspend fun clearHistory() {
        context.historyDataStore.edit { preferences ->
            preferences[logsKey] = "[]"
        }
    }

    private fun parseAndFilterLogs(jsonString: String, cutoffTimestamp: Long): List<ActionLogEntry> {
        val list = mutableListOf<ActionLogEntry>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val entry = ActionLogEntry.fromJson(obj)
                if (entry.timestamp >= cutoffTimestamp) {
                    list.add(entry)
                }
            }
        } catch (_: Exception) {
            // If json parsing fails, return empty list
        }
        return list.sortedByDescending { it.timestamp }
    }
}
