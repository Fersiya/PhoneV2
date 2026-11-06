package com.example.pccontrol

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections

val mediaSyncMutex = Mutex()

suspend fun http(
    host: String,
    token: String,
    path: String,
    method: String,
    bodyText: String? = null,
    timeoutMs: Int = 3500
): HttpResult =
    withContext(Dispatchers.IO) {
        val conn = URL(buildPcUrl(host, path)).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs + 3500
            val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
            conn.setRequestProperty("X-Token", safeToken)
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.setRequestProperty("Pragma", "no-cache")
            if (method == "POST") {
                conn.doOutput = true
                val payload = bodyText?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                conn.setFixedLengthStreamingMode(payload.size)
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                conn.outputStream.use { it.write(payload) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            HttpResult(code, body)
        } finally {
            conn.disconnect()
        }
    }

suspend fun runMjpegStream(
    host: String,
    token: String,
    fps: Int,
    width: Int,
    quality: Int,
    allMonitors: Boolean,
    onFrame: (ImageBitmap, Int, Long) -> Unit
) = withContext(Dispatchers.IO) {
    val monParam = if (allMonitors) "all" else "0"
    val conn = URL(buildPcUrl(host, "/stream?fps=$fps&w=$width&q=$quality&m=$monParam")).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 3000
        conn.readTimeout = 6000
        val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
        conn.setRequestProperty("X-Token", safeToken)
        if (conn.responseCode != 200) return@withContext
        val stream = conn.inputStream.buffered(65536)
        var bufferA: Bitmap? = null
        var bufferB: Bitmap? = null
        var useBufferA = true
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inMutable = true
            inSampleSize = 1
        }
        var frameCount = 0
        var lastFpsTime = System.currentTimeMillis()
        var calcFps = fps

        // Переиспользуемый буфер байтов кадра и заголовков (Zero GC Allocations для супер-плавности)
        var reusableFrameBuffer = ByteArray(256 * 1024)
        val headerBuf = ByteArray(128)

        while (coroutineContext.isActive) {
            val frameStartMs = System.currentTimeMillis()
            var contentLength = -1

            while (coroutineContext.isActive) {
                var lineLen = 0
                while (true) {
                    val b = stream.read()
                    if (b == -1) return@withContext
                    if (b == 10) break // '\n'
                    if (lineLen < headerBuf.size) {
                        headerBuf[lineLen++] = b.toByte()
                    }
                }
                val effLen = if (lineLen > 0 && headerBuf[lineLen - 1] == 13.toByte()) lineLen - 1 else lineLen // '\r'
                if (effLen == 0 && contentLength > 0) {
                    break
                }
                if (effLen > 15) {
                    val prefix = "content-length:"
                    var match = true
                    for (i in 0 until 15) {
                        val c = headerBuf[i].toInt().toChar().lowercaseChar()
                        if (c != prefix[i]) {
                            match = false
                            break
                        }
                    }
                    if (match) {
                        var parsedVal = 0
                        var idx = 15
                        while (idx < effLen && (headerBuf[idx] == 32.toByte() || headerBuf[idx] == 9.toByte())) {
                            idx++
                        }
                        while (idx < effLen && headerBuf[idx] in 48.toByte()..57.toByte()) {
                            parsedVal = parsedVal * 10 + (headerBuf[idx] - 48.toByte())
                            idx++
                        }
                        if (parsedVal > 0) {
                            contentLength = parsedVal
                        }
                    }
                }
            }

            if (contentLength <= 0 || contentLength > 10 * 1024 * 1024) break

            if (contentLength > reusableFrameBuffer.size) {
                reusableFrameBuffer = ByteArray(maxOf(contentLength + 32768, reusableFrameBuffer.size * 2))
            }

            var readTotal = 0
            while (readTotal < contentLength && coroutineContext.isActive) {
                val r = stream.read(reusableFrameBuffer, readTotal, contentLength - readTotal)
                if (r == -1) break
                readTotal += r
            }

            if (readTotal == contentLength) {
                var skip = 0
                while (skip < 2 && coroutineContext.isActive) {
                    val b = stream.read()
                    if (b == -1) break
                    skip++
                }

                opts.inBitmap = if (useBufferA) bufferA else bufferB
                val bmp = try {
                    BitmapFactory.decodeByteArray(reusableFrameBuffer, 0, contentLength, opts)
                } catch (_: IllegalArgumentException) {
                    opts.inBitmap = null
                    BitmapFactory.decodeByteArray(reusableFrameBuffer, 0, contentLength, opts)
                }

                if (bmp != null) {
                    if (useBufferA) bufferA = bmp else bufferB = bmp
                    useBufferA = !useBufferA
                    val img = bmp.asImageBitmap()

                    frameCount++
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastFpsTime >= 1000) {
                        calcFps = frameCount
                        frameCount = 0
                        lastFpsTime = nowMs
                    }
                    val currentPing = nowMs - frameStartMs

                    launch(Dispatchers.Main.immediate) {
                        onFrame(img, calcFps, currentPing)
                    }
                }
            }
        }
    } catch (_: Exception) {
    } finally {
        try { conn.disconnect() } catch (_: Exception) {}
    }
}

suspend fun fetchScreenBitmap(
    host: String,
    token: String,
    width: Int,
    quality: Int,
    allMonitors: Boolean
): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val monParam = if (allMonitors) "all" else "0"
        val conn = URL(buildPcUrl(host, "/screen?w=$width&q=$quality&m=$monParam")).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 1200
            conn.readTimeout = 1500
            val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
            conn.setRequestProperty("X-Token", safeToken)
            conn.setRequestProperty("Connection", "keep-alive")
            if (conn.responseCode != 200) {
                conn.errorStream?.close()
                return@withContext null
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return@withContext null
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
        } catch (_: Exception) {
            null
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

suspend fun checkOtaUpdate(context: Context, host: String, token: String, installedHash: String): OtaCheckResult =
    withContext(Dispatchers.IO) {
        try {
            val r = http(host, token, "/update/check", "GET", timeoutMs = 3000)
            if (r.code == 404) {
                return@withContext OtaCheckResult(null, "Ошибка 404: перезапустите server.py на ПК")
            }
            if (r.code == 401) {
                return@withContext OtaCheckResult(null, "Ошибка 401: неверный токен подключения")
            }
            if (r.code == 200) {
                val o = JSONObject(r.body)
                val avail = o.optBoolean("available", false)
                val dir = o.optString("updatesDir", "")
                if (!avail) {
                    val dirMsg = if (dir.isNotEmpty()) " (папка: $dir)" else ""
                    return@withContext OtaCheckResult(null, "На ПК в папке updates нет .apk$dirMsg")
                }
                val vCode = o.optLong("versionCode", 0L)
                val vName = o.optString("versionName", "")
                val hash = o.optString("hash", "")
                val size = o.optLong("size", 0L)
                val fn = o.optString("filename", "app-debug.apk")

                val currentCode = getInstalledVersionCode(context)
                val runningApkHash = try {
                    computeFileSha256(File(context.packageCodePath))
                } catch (_: Exception) {
                    ""
                }

                if (hash.isNotEmpty() && runningApkHash.isNotEmpty() && hash.equals(runningApkHash, ignoreCase = true)) {
                    return@withContext OtaCheckResult(null, "Установлена последняя версия ($fn) ✔")
                }

                if (vCode > currentCode || (hash.isNotEmpty() && runningApkHash.isNotEmpty() && !hash.equals(runningApkHash, ignoreCase = true))) {
                    return@withContext OtaCheckResult(UpdateInfo(true, vCode, vName, hash, size), "Найдено обновление: $fn ✔")
                } else {
                    return@withContext OtaCheckResult(null, "Установлена актуальная версия ✔")
                }
            }
            OtaCheckResult(null, "Ответ ПК: ${r.code}")
        } catch (e: Exception) {
            OtaCheckResult(null, "ПК недоступен (${e.javaClass.simpleName})")
        }
    }

suspend fun downloadAndInstallApk(
    context: Context,
    host: String,
    token: String,
    expectedHash: String = "",
    onProgress: (Float) -> Unit
): String =
    withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    withContext(Dispatchers.Main) {
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                    return@withContext "Разрешите установку из этого источника и нажмите 'Обновить' ещё раз"
                }
            }

            val conn = URL(buildPcUrl(host, "/update/download")).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 60000
            val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
            conn.setRequestProperty("X-Token", safeToken)
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.setRequestProperty("Pragma", "no-cache")
            if (conn.responseCode != 200) {
                return@withContext "Ошибка загрузки: код ${conn.responseCode}"
            }
            val totalSize = conn.contentLength
            val apkFile = File(context.cacheDir, "pc-control-update.apk")
            if (apkFile.exists()) apkFile.delete()

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        if (totalSize > 0) {
                            onProgress(totalDownloaded.toFloat() / totalSize.toFloat())
                        }
                    }
                }
            }

            if (expectedHash.isNotBlank()) {
                val computed = computeFileSha256(apkFile)
                if (!computed.equals(expectedHash.trim(), ignoreCase = true)) {
                    apkFile.delete()
                    return@withContext "Ошибка: хеш файла не совпадает! Ожидался $expectedHash, получен $computed"
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            context.startActivity(installIntent)
            "Запуск установщика Android…"
        } catch (e: Exception) {
            "Ошибка: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

suspend fun command(
    host: String,
    token: String,
    path: String,
    method: String = "POST",
    bodyText: String? = null
): String =
    try {
        val r = http(host, token, path, method, bodyText)
        when (r.code) {
            200 -> "Готово ✔"
            401 -> "Неверный токен"
            else -> "Ошибка ${r.code}: ${r.body}"
        }
    } catch (e: Exception) {
        "ПК недоступен (${e.javaClass.simpleName})"
    }

suspend fun sendMagicPacket(mac: String): String =
    withContext(Dispatchers.IO) {
        try {
            val clean = mac.replace(Regex("[^0-9A-Fa-f]"), "")
            if (clean.length != 12) {
                return@withContext "MAC-адрес должен состоять из 12 HEX-символов"
            }
            val macBytes = ByteArray(6) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            val packet = ByteArray(6 + 16 * 6)
            for (i in 0 until 6) packet[i] = 0xFF.toByte()
            for (i in 0 until 16) System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)

            val targets = linkedSetOf("255.255.255.255")
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }
                    ?: emptyList()
                for (ni in interfaces) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (ia in ni.interfaceAddresses) {
                        ia.broadcast?.hostAddress?.let { targets.add(it) }
                    }
                }
            } catch (_: Exception) {
            }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (target in targets) {
                    val addr = InetAddress.getByName(target)
                    for (port in intArrayOf(9, 7)) {
                        socket.send(DatagramPacket(packet, packet.size, addr, port))
                    }
                }
            }
            "Сигнал включения отправлен ✔"
        } catch (e: Exception) {
            "Не удалось отправить (${e.javaClass.simpleName})"
        }
    }

suspend fun uploadMediaFile(
    context: Context,
    host: String,
    token: String,
    uri: Uri,
    displayName: String,
    knownSize: Long = -1L
): Boolean = withContext(Dispatchers.IO) {
    try {
        val encodedName = java.net.URLEncoder.encode(displayName, "UTF-8")
        val conn = URL(buildPcUrl(host, "/upload/media")).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 6000
        conn.readTimeout = 120000
        val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
        conn.setRequestProperty("X-Token", safeToken)
        conn.setRequestProperty("X-Filename", encodedName)
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.doOutput = true

        val stream = context.contentResolver.openInputStream(uri) ?: return@withContext false
        val size = if (knownSize > 0) knownSize else {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            } catch (_: Exception) { -1L }
        }

        if (size > 0) {
            conn.setFixedLengthStreamingMode(size)
        } else {
            conn.setChunkedStreamingMode(65536)
        }

        stream.use { input ->
            conn.outputStream.use { output ->
                val buf = ByteArray(65536)
                var r: Int
                while (input.read(buf).also { r = it } != -1) {
                    output.write(buf, 0, r)
                }
                output.flush()
            }
        }
        val code = conn.responseCode
        conn.disconnect()
        code == 200
    } catch (_: Exception) {
        false
    }
}

suspend fun syncPendingMedia(
    context: Context,
    host: String,
    token: String,
    profileId: String,
    onStatus: (String) -> Unit
): Int = withContext(Dispatchers.IO) {
    if (!mediaSyncMutex.tryLock()) return@withContext 0
    try {
        if (host.isBlank() || token.isBlank()) return@withContext 0
        val prefs = context.getSharedPreferences("pc_control_media", Context.MODE_PRIVATE)
        val uploadedKey = "uploaded_ids_${profileId}"
        val timestampKey = "sync_start_timestamp_${profileId}"
        val uploadedSet = prefs.getStringSet(uploadedKey, emptySet())?.toMutableSet() ?: mutableSetOf()

        var syncStartTimestamp = prefs.getLong(timestampKey, 0L)
        if (syncStartTimestamp <= 0L) {
            syncStartTimestamp = System.currentTimeMillis() / 1000L
            prefs.edit().putLong(timestampKey, syncStartTimestamp).apply()
        }

        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(syncStartTimestamp.toString())

        val cursor = try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                proj,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} ASC"
            )
        } catch (_: Exception) {
            null
        }

        var uploadedCount = 0
        cursor?.use { c ->
            val idCol = c.getColumnIndex(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndex(MediaStore.Images.Media.SIZE)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val idStr = "img_$id"
                if (uploadedSet.contains(idStr)) continue

                val name = c.getString(nameCol) ?: "photo_$id.jpg"
                val size = c.getLong(sizeCol)
                val itemUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                withContext(Dispatchers.Main) {
                    onStatus("Отправляю фото $name на ПК…")
                }

                val ok = uploadMediaFile(context, host, token, itemUri, name, size)
                if (ok) {
                    uploadedSet.add(idStr)
                    prefs.edit().putStringSet(uploadedKey, uploadedSet).apply()
                    uploadedCount++
                }
            }
        }
        if (uploadedCount > 0) {
            withContext(Dispatchers.Main) {
                onStatus("Отправлено новых фото: $uploadedCount ✔")
            }
        }
        uploadedCount
    } finally {
        mediaSyncMutex.unlock()
    }
}

suspend fun pairWithPc(host: String): PairResult = withContext(Dispatchers.IO) {
    if (host.isBlank()) {
        return@withContext PairResult(false, "", "Сначала укажите адрес ПК или нажмите «Найти ПК в Wi-Fi»")
    }
    try {
        val targetUrl = buildPcUrl(host, "/api/pair")
        val conn = URL(targetUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 3000
        conn.readTimeout = 4000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code == 200) {
            val json = JSONObject(text)
            val tok = json.optString("token", "")
            val pc = json.optString("pc", "ПК")
            if (tok.isNotBlank()) {
                PairResult(true, tok, "Токен и MAC получены от $pc ✔", json.optString("mac", ""))
            } else {
                PairResult(false, "", "ПК ответил, но токен пустой")
            }
        } else if (code == 403) {
            val serverMsg = try { JSONObject(text).optString("message", "") } catch (_: Exception) { "" }
            PairResult(false, "", serverMsg.ifBlank { "ПК отклонил запрос: разрешено только в домашней сети Wi-Fi" })
        } else {
            PairResult(false, "", "Ошибка ПК: HTTP $code ($text)")
        }
    } catch (e: Exception) {
        PairResult(false, "", "Не удалось связаться с ПК: ${e.message ?: "таймаут"}")
    }
}

suspend fun getTunnelStatus(host: String, token: String): TunnelStatus = withContext(Dispatchers.IO) {
    if (host.isBlank()) return@withContext TunnelStatus(false, "", false, "Адрес ПК не указан")
    val r = http(host, token, "/api/tunnel", "GET", timeoutMs = 3000)
    if (r.code == 200) {
        try {
            val o = JSONObject(r.body)
            TunnelStatus(
                running = o.optBoolean("running", false),
                url = o.optString("url", ""),
                installed = o.optBoolean("installed", false)
            )
        } catch (e: Exception) {
            TunnelStatus(false, "", false, "Ошибка ответа: ${e.message}")
        }
    } else {
        TunnelStatus(false, "", false, "HTTP ${r.code}")
    }
}

suspend fun toggleTunnel(host: String, token: String, start: Boolean): TunnelStatus = withContext(Dispatchers.IO) {
    if (host.isBlank()) return@withContext TunnelStatus(false, "", false, "Адрес ПК не указан")
    val endpoint = if (start) "/api/tunnel/start" else "/api/tunnel/stop"
    val r = http(host, token, endpoint, "POST", timeoutMs = 5000)
    if (r.code == 200) {
        try {
            val o = JSONObject(r.body)
            val info = o.optJSONObject("info")
            TunnelStatus(
                running = info?.optBoolean("running", false) ?: start,
                url = info?.optString("url", "") ?: "",
                installed = info?.optBoolean("installed", false) ?: true,
                message = o.optString("message", if (start) "Туннель запускается…" else "Туннель остановлен")
            )
        } catch (e: Exception) {
            TunnelStatus(false, "", false, "Ошибка: ${e.message}")
        }
    } else {
        TunnelStatus(false, "", false, "Ошибка: HTTP ${r.code}")
    }
}
