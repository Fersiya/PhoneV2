package com.example.pccontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressTest {
    @Test
    fun tailscaleAddress_isNotHome() {
        // Адрес Tailscale — это тоже IPv4, поэтому приложение считает его «адресом по IP».
        assertTrue(isLanAddress("100.126.230.42"))
        assertEquals("http://100.126.230.42:8765/ping", buildPcUrl("100.126.230.42", "/ping"))
    }

    @Test
    fun homeAddress_withPort() {
        assertTrue(isLanAddress("192.168.0.152:8765"))
        assertEquals("http://192.168.0.152:9000/x", buildPcUrl("192.168.0.152:9000", "x"))
    }

    @Test
    fun domain_isRemote() {
        assertFalse(isLanAddress("my.example.com"))
        assertTrue(isRemoteAddress("my.example.com"))
        assertEquals("https://my.example.com/ping", buildPcUrl("my.example.com", "/ping"))
    }

    @Test
    fun blank_isNeither() {
        assertFalse(isLanAddress(""))
        assertFalse(isRemoteAddress("  "))
    }
}

class TailscaleAddressTest {
    @Test
    fun tailscaleRange() {
        assertTrue(isTailscaleAddress("100.126.230.42"))
        assertTrue(isTailscaleAddress("100.64.0.1:8765"))
        assertFalse(isTailscaleAddress("100.128.0.1"))
        assertFalse(isTailscaleAddress("192.168.0.152"))
    }

    @Test
    fun homeLan_excludesTailscale() {
        assertTrue(isHomeLanAddress("192.168.0.152"))
        assertFalse(isHomeLanAddress("100.126.230.42"))
        assertFalse(isHomeLanAddress("my.example.com"))
    }
}
