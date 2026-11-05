package com.example.pccontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildPcUrlTest {

    @Test
    fun testBuildPcUrl_plainIp() {
        val result = buildPcUrl("192.168.1.100", "/apps")
        assertEquals("http://192.168.1.100:8765/apps", result)
    }

    @Test
    fun testBuildPcUrl_withPort() {
        val result = buildPcUrl("192.168.1.100:9000", "/apps")
        assertEquals("http://192.168.1.100:9000/apps", result)
    }

    @Test
    fun testBuildPcUrl_trycloudflare() {
        val result = buildPcUrl("my-tunnel.trycloudflare.com", "/ping")
        assertEquals("https://my-tunnel.trycloudflare.com/ping", result)
    }

    @Test
    fun testBuildPcUrl_alreadyHasHttp() {
        val result = buildPcUrl("http://10.0.0.5:8765", "/stream")
        assertEquals("http://10.0.0.5:8765/stream", result)
    }

    @Test
    fun testBuildPcUrl_alreadyHasHttps() {
        val result = buildPcUrl("https://custom-domain.com", "/screen")
        assertEquals("https://custom-domain.com/screen", result)
    }

    @Test
    fun testBuildPcUrl_pairEndpoint() {
        val result = buildPcUrl("192.168.0.152", "/api/pair")
        assertEquals("http://192.168.0.152:8765/api/pair", result)
    }
}
