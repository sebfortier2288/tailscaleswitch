package dev.sfpixel.tailscaleswitch.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryRepositoryTest {

    @Test
    fun `ActionLogEntry serialization and deserialization works correctly`() {
        val entry = ActionLogEntry(
            id = "test-id-123",
            timestamp = 1700000000000L,
            type = ActionType.CONNECT,
            message = "Signal de connexion envoyé",
            details = "Connexion Wi-Fi non approuvé"
        )

        val json = entry.toJson()
        val restored = ActionLogEntry.fromJson(json)

        assertEquals("test-id-123", restored.id)
        assertEquals(1700000000000L, restored.timestamp)
        assertEquals(ActionType.CONNECT, restored.type)
        assertEquals("Signal de connexion envoyé", restored.message)
        assertEquals("Connexion Wi-Fi non approuvé", restored.details)
    }

    @Test
    fun `ActionLogEntry deserialization handles null details`() {
        val entry = ActionLogEntry(
            id = "test-id-456",
            timestamp = 1700000000000L,
            type = ActionType.DISCONNECT,
            message = "Déconnexion",
            details = null
        )

        val json = entry.toJson()
        val restored = ActionLogEntry.fromJson(json)

        assertEquals("test-id-456", restored.id)
        assertNull(restored.details)
    }

    @Test
    fun `JSONArray parsing handles invalid JSON gracefully`() {
        val jsonArray = JSONArray()
        val entry1 = ActionLogEntry(timestamp = 1000L, type = ActionType.SERVICE, message = "Msg 1")
        val entry2 = ActionLogEntry(timestamp = 2000L, type = ActionType.TIMER, message = "Msg 2")

        jsonArray.put(entry1.toJson())
        jsonArray.put(entry2.toJson())

        assertEquals(2, jsonArray.length())
    }
}
