package dev.sfpixel.tailscaleswitch.util

object SsidUtils {
    /**
     * Cleans the SSID string by removing surrounding quotes and handling special unknown values.
     * Returns null if the SSID is unknown, empty, or represents a lack of connection.
     */
    fun cleanSsid(rawSsid: String?): String? {
        if (rawSsid == null) return null
        
        val cleaned = rawSsid.removeSurrounding("\"")
        
        return if (cleaned == "<unknown ssid>" || cleaned == "0x" || cleaned.isBlank()) {
            null
        } else {
            cleaned
        }
    }
}
