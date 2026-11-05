package com.example.pccontrol

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File

fun getInstalledVersionCode(context: Context): Long =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
        }
    } catch (_: Exception) {
        -1L
    }

fun getInstalledVersionName(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "6.0"
    } catch (_: Exception) {
        "0.0"
    }

fun buildPcUrl(host: String, pathWithQuery: String): String {
    val cleanHost = host.trim().removeSuffix("/")
    val path = if (pathWithQuery.startsWith("/")) pathWithQuery else "/$pathWithQuery"
    return when {
        cleanHost.startsWith("http://") || cleanHost.startsWith("https://") -> "$cleanHost$path"
        cleanHost.contains("trycloudflare.com") || (cleanHost.contains(".") && !cleanHost.all { it.isDigit() || it == '.' || it == ':' }) -> "https://$cleanHost$path"
        else -> if (":" in cleanHost) "http://$cleanHost$path" else "http://$cleanHost:$PORT$path"
    }
}

fun computeFileSha256(file: File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(16384)
        var read: Int
        while (input.read(buf).also { read = it } != -1) {
            digest.update(buf, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun parseStatusSnapshot(json: String): PcStatusSnapshot {
    val root = JSONObject(json)
    val arr = root.getJSONArray("apps")
    val appList = List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        AppItem(o.getString("id"), o.getString("title"), o.getBoolean("canClose"), o.getBoolean("running"))
    }
    val remSec = root.optInt("shutdownRemainingSec", 0)
    val active = root.optBoolean("shutdownActive", remSec > 0)

    val disksArr = root.optJSONArray("disks")
    val diskList = if (disksArr != null) {
        List(disksArr.length()) { i ->
            val d = disksArr.getJSONObject(i)
            DiskItem(
                drive = d.optString("drive", ""),
                totalGb = d.optDouble("totalGb", 0.0),
                freeGb = d.optDouble("freeGb", 0.0),
                usedPercent = d.optInt("usedPercent", 0)
            )
        }
    } else emptyList()

    return PcStatusSnapshot(
        apps = appList,
        activeWindow = root.optString("activeWindow", ""),
        ramPercent = root.optInt("ramPercent", 0),
        ramTotalGb = root.optDouble("ramTotalGb", 0.0),
        ramUsedGb = root.optDouble("ramUsedGb", 0.0),
        cpuPercent = root.optInt("cpuPercent", 0),
        uptimeSec = root.optInt("uptimeSec", 0),
        disks = diskList,
        shutdownRemainingSec = remSec,
        shutdownActive = active,
        volume = root.optInt("volume", -1),
        tunnelUrl = root.optString("tunnelUrl", "")
    )
}

fun parseBrowseResult(json: String): BrowseResult {
    val root = JSONObject(json)
    val path = root.optString("path", "")
    val parent = root.optString("parent", "")
    val drivesArr = root.optJSONArray("drives")
    val drives = if (drivesArr != null) List(drivesArr.length()) { drivesArr.getString(it) } else emptyList()
    val entriesArr = root.optJSONArray("entries")
    val entries = if (entriesArr != null) List(entriesArr.length()) { i ->
        val o = entriesArr.getJSONObject(i)
        FileEntry(o.optString("name"), o.optBoolean("isDir"), o.optLong("size"), o.optLong("mtime"))
    } else emptyList()
    return BrowseResult(path, parent, drives, entries)
}

fun parseOpenWindows(json: String): List<WindowItem> {
    val arr = JSONObject(json).getJSONArray("windows")
    return List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        WindowItem(
            hwnd = o.getLong("hwnd"),
            title = o.getString("title"),
            process = o.optString("process", ""),
            path = o.optString("path", ""),
            active = o.optBoolean("active", false),
            minimized = o.optBoolean("minimized", false)
        )
    }
}

/** Домашний адрес: IPv4 (с портом или без), например 192.168.0.10 или 192.168.0.10:8765. */
fun isLanAddress(h: String): Boolean {
    val t = h.trim().removePrefix("http://").removeSuffix("/")
    return t.isNotEmpty() && t.all { it.isDigit() || it == '.' || it == ':' } && t.contains('.')
}

/** Адрес вне дома: ссылка https или доменное имя (trycloudflare.com и т.п.). */
fun isRemoteAddress(h: String): Boolean {
    val t = h.trim()
    return t.isNotEmpty() && !isLanAddress(t) && (t.startsWith("https://") || t.contains("."))
}

/**
 * Координаты касания (в пикселях области sw×sh, уже без зума/сдвига) -> доли 0..1 внутри картинки,
 * вписанной в область с чёрными полями (ContentScale.Fit). Без этого края экрана ПК недостижимы.
 */
fun fitNormalized(localX: Float, localY: Float, sw: Float, sh: Float, bw: Int, bh: Int): Pair<Float, Float> {
    if (bw <= 0 || bh <= 0 || sw <= 0f || sh <= 0f) return Pair((localX / sw).coerceIn(0f, 1f), (localY / sh).coerceIn(0f, 1f))
    val scale = minOf(sw / bw, sh / bh)
    val dw = bw * scale
    val dh = bh * scale
    val ox = (sw - dw) / 2f
    val oy = (sh - dh) / 2f
    return Pair(((localX - ox) / dw).coerceIn(0f, 1f), ((localY - oy) / dh).coerceIn(0f, 1f))
}
