package dev.sfpixel.tailscaleswitch.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SsidUtilsTest {

    @Test
    fun `cleanSsid removes surrounding quotes`() {
        val raw = "\"MyHomeWifi\""
        val expected = "MyHomeWifi"
        assertEquals(expected, SsidUtils.cleanSsid(raw))
    }

    @Test
    fun `cleanSsid returns null for unknown ssid`() {
        assertNull(SsidUtils.cleanSsid("<unknown ssid>"))
        assertNull(SsidUtils.cleanSsid("\"<unknown ssid>\""))
    }

    @Test
    fun `cleanSsid returns null for 0x`() {
        assertNull(SsidUtils.cleanSsid("0x"))
        assertNull(SsidUtils.cleanSsid("\"0x\""))
    }

    @Test
    fun `cleanSsid returns null for null input`() {
        assertNull(SsidUtils.cleanSsid(null))
    }

    @Test
    fun `cleanSsid returns null for blank input`() {
        assertNull(SsidUtils.cleanSsid(""))
        assertNull(SsidUtils.cleanSsid("   "))
        assertNull(SsidUtils.cleanSsid("\"\""))
    }

    @Test
    fun `cleanSsid handles unquoted valid ssid`() {
        val raw = "ValidSSID"
        assertEquals(raw, SsidUtils.cleanSsid(raw))
    }
}
