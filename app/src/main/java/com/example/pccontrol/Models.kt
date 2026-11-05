package com.example.pccontrol

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

const val PORT = 8765
const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
const val POLL_INTERVAL_MS = 2500L
val KeyPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp)

enum class NavTab(val title: String, val icon: String) {
    SCREEN("Экран", "🖥️"),
    GAME("Геймпад", "🎮"),
    MEDIA("Медиа", "🎵"),
    APPS("Окна/Софт", "🪟"),
    POWER("Питание", "⚡"),
    SETTINGS("Опции", "⚙️")
}

data class HttpResult(val code: Int, val body: String)
data class AppItem(val id: String, val title: String, val canClose: Boolean, val running: Boolean)
data class WindowItem(val hwnd: Long, val title: String, val process: String, val path: String, val active: Boolean, val minimized: Boolean)
data class DiskItem(val drive: String, val totalGb: Double, val freeGb: Double, val usedPercent: Int)

data class PcStatusSnapshot(
    val apps: List<AppItem>,
    val activeWindow: String,
    val ramPercent: Int,
    val ramTotalGb: Double = 0.0,
    val ramUsedGb: Double = 0.0,
    val cpuPercent: Int = 0,
    val uptimeSec: Int = 0,
    val disks: List<DiskItem> = emptyList(),
    val shutdownRemainingSec: Int,
    val shutdownActive: Boolean,
    val volume: Int = -1,
    val tunnelUrl: String = ""
)

data class FileEntry(val name: String, val isDir: Boolean, val size: Long, val mtime: Long)
data class BrowseResult(val path: String, val parent: String, val drives: List<String>, val entries: List<FileEntry>)

data class PcProfile(
    val id: String,
    val name: String,
    val host: String,
    val token: String,
    val mac: String
)

data class UpdateInfo(val available: Boolean, val versionCode: Long, val versionName: String, val hash: String, val size: Long)
data class OtaCheckResult(val update: UpdateInfo?, val message: String)
data class PairResult(val success: Boolean, val token: String, val message: String)
data class TunnelStatus(val running: Boolean, val url: String, val installed: Boolean = true, val message: String = "")
