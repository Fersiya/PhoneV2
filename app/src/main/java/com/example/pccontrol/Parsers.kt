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
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "6.3"
    } catch (_: Exception) {
        "0.0"
    }

fun buildPcUrl(host: String, pathWithQuery: String): String {
    val cleanHost = host.trim().removeSuffix("/")
    val path = if (pathWithQuery.startsWith("/")) pathWithQuery else "/$pathWithQuery"
    return when {
        cleanHost.startsWith("http://") || cleanHost.startsWith("https://") -> "$cleanHost$path"
        (cleanHost.contains(".") && !cleanHost.all { it.isDigit() || it == '.' || it == ':' }) -> "https://$cleanHost$path"
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

/** Адрес Tailscale (100.64.0.0/10) — это тоже IPv4, но канал через интернет, а не домашний Wi-Fi. */
fun isTailscaleAddress(h: String): Boolean {
    val t = h.trim().removePrefix("http://").removeSuffix("/").substringBefore(':')
    val p = t.split('.')
    if (p.size != 4) return false
    val a = p[0].toIntOrNull() ?: return false
    val b = p[1].toIntOrNull() ?: return false
    return a == 100 && b in 64..127
}

/** Быстрый домашний канал: адрес локальной сети и не Tailscale. */
fun isHomeLanAddress(h: String): Boolean = isLanAddress(h) && !isTailscaleAddress(h)

/** Адрес вне дома: ссылка https или доменное имя. */
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


/** Видимая часть экрана ПК при зуме [zoom] с центром (cx, cy): x0, y0, x1, y1 — доли 0..1. */
fun streamViewport(zoom: Float, cx: Float, cy: Float): FloatArray {
    val vw = 1f / zoom.coerceIn(1f, 6f)
    val x0 = (cx - vw / 2f).coerceIn(0f, 1f - vw)
    val y0 = (cy - vw / 2f).coerceIn(0f, 1f - vw)
    return floatArrayOf(x0, y0, x0 + vw, y0 + vw)
}

/**
 * Как показать кадр, пришедший с областью [f], чтобы на экране была область [v]:
 * масштаб X, масштаб Y, сдвиг X, сдвиг Y (сдвиг — доли ширины/высоты картинки). Если область кадра
 * совпала с нужной — это 1,1,0,0, т.е. никакого растяжения и картинка остаётся чёткой.
 */
fun streamViewLayer(v: FloatArray, f: FloatArray): FloatArray {
    val vw = (v[2] - v[0]).coerceAtLeast(0.01f)
    val vh = (v[3] - v[1]).coerceAtLeast(0.01f)
    val fw = (f[2] - f[0]).coerceAtLeast(0.01f)
    val fh = (f[3] - f[1]).coerceAtLeast(0.01f)
    val sx = fw / vw
    val sy = fh / vh
    val tx = -v[0] / vw - 0.5f + (f[0] / fw + 0.5f) * sx
    val ty = -v[1] / vh - 0.5f + (f[1] / fh + 0.5f) * sy
    return floatArrayOf(sx, sy, tx, ty)
}


/** Понятный текст вместо технической ошибки сети. */
fun friendlyNetError(raw: String?): String {
    val m = (raw ?: "").lowercase()
    return when {
        m.isBlank() -> "нет связи с ПК"
        "failed to connect" in m || "econnrefused" in m || "connection refused" in m -> "ПК не отвечает (выключен, спит или нет сети)"
        "unexpected end of stream" in m || "connection reset" in m || "software caused connection abort" in m || "broken pipe" in m -> "связь с ПК оборвалась"
        "timed out" in m || "timeout" in m -> "ПК слишком долго не отвечает"
        "cancelled" in m || "canceled" in m -> "запрос отменён"
        "unable to resolve host" in m || "unknownhost" in m -> "не удалось найти адрес ПК"
        "network is unreachable" in m || "enetunreach" in m -> "нет сети на телефоне"
        else -> raw ?: "ошибка сети"
    }
}
