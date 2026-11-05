package com.example.pccontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {

    @Test
    fun testParseStatusSnapshot_validJson() {
        val json = """
            {
                "apps": [
                    {"id": "brave", "title": "Brave", "canClose": true, "running": true}
                ],
                "activeWindow": "Google Chrome",
                "ramPercent": 45,
                "ramTotalGb": 16.0,
                "ramUsedGb": 7.2,
                "cpuPercent": 25,
                "uptimeSec": 3600,
                "disks": [
                    {"drive": "C:", "totalGb": 500.0, "freeGb": 200.0, "usedPercent": 60}
                ],
                "shutdownRemainingSec": 0,
                "shutdownActive": false,
                "volume": 70
            }
        """.trimIndent()

        val snapshot = parseStatusSnapshot(json)
        assertEquals(1, snapshot.apps.size)
        assertEquals("brave", snapshot.apps[0].id)
        assertTrue(snapshot.apps[0].running)
        assertEquals("Google Chrome", snapshot.activeWindow)
        assertEquals(45, snapshot.ramPercent)
        assertEquals(16.0, snapshot.ramTotalGb, 0.01)
        assertEquals(70, snapshot.volume)
        assertEquals(1, snapshot.disks.size)
        assertEquals("C:", snapshot.disks[0].drive)
        assertEquals("", snapshot.tunnelUrl)
    }

    @Test
    fun testParseBrowseResult_validJson() {
        val json = """
            {
                "path": "D:\\Photos",
                "parent": "D:\\",
                "drives": ["C:", "D:"],
                "entries": [
                    {"name": "photo1.jpg", "isDir": false, "size": 1024000, "mtime": 1700000000},
                    {"name": "Trips", "isDir": true, "size": 0, "mtime": 1700000000}
                ]
            }
        """.trimIndent()

        val res = parseBrowseResult(json)
        assertEquals("D:\\Photos", res.path)
        assertEquals("D:\\", res.parent)
        assertEquals(2, res.drives.size)
        assertEquals(2, res.entries.size)
        assertFalse(res.entries[0].isDir)
        assertTrue(res.entries[1].isDir)
    }

    @Test
    fun testParseOpenWindows_validJson() {
        val json = """
            {
                "windows": [
                    {
                        "hwnd": 123456,
                        "title": "Telegram",
                        "process": "telegram.exe",
                        "path": "C:\\Telegram\\telegram.exe",
                        "active": true,
                        "minimized": false
                    }
                ]
            }
        """.trimIndent()

        val windows = parseOpenWindows(json)
        assertEquals(1, windows.size)
        assertEquals(123456L, windows[0].hwnd)
        assertEquals("Telegram", windows[0].title)
        assertTrue(windows[0].active)
        assertFalse(windows[0].minimized)
    }

    @Test
    fun testAddressKinds() {
        assertTrue(isLanAddress("192.168.0.10"))
        assertTrue(isLanAddress("192.168.0.10:8765"))
        assertFalse(isLanAddress("https://abc.trycloudflare.com"))
        assertTrue(isRemoteAddress("https://abc.trycloudflare.com"))
        assertTrue(isRemoteAddress("abc.trycloudflare.com"))
        assertFalse(isRemoteAddress("192.168.0.10"))
        assertFalse(isRemoteAddress(""))
    }
}
