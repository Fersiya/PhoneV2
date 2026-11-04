package com.example.pccontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import android.Manifest
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import kotlinx.coroutines.awaitCancellation
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var sharedUrlState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractSharedUrl(intent)?.let { sharedUrlState.value = it }

        setContent {
            MaterialTheme(colorScheme = CyberDarkPalette) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PcControlMainApp(
                        incomingSharedUrl = sharedUrlState.value,
                        onSharedUrlConsumed = { sharedUrlState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedUrl(intent)?.let { sharedUrlState.value = it }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("""https?://\S+""").find(text)?.value
    }
}

// Современная палитра: глубокий Midnight Navy с акцентами Electric Cyan и Neon Violet
private val CyberDarkPalette = darkColorScheme(
    primary = Color(0xFF00E5FF),          // Неоновый циан
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFFB2F5EA),
    secondary = Color(0xFFA78BFA),        // Фиолетовый
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = Color(0xFF3B1E78),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = Color(0xFF10B981),         // Изумрудно-зеленый (статус онлайн)
    background = Color(0xFF0A0F1D),       // Глубокий темный фон
    surface = Color(0xFF111827),          // Поверхность карточек
    surfaceVariant = Color(0xFF1F2937),   // Контейнеры
    onSurface = Color(0xFFF9FAFB),        // Четкий белый текст
    onSurfaceVariant = Color(0xFF9CA3AF), // Вспомогательный серый текст
    outline = Color(0xFF374151),          // Тонкие рамки
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private const val PORT = 8765
private const val POLL_INTERVAL_MS = 2500L
private val KeyPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp)

enum class NavTab(val title: String, val icon: String) {
    SCREEN("Экран", "🖥️"),
    MEDIA("Медиа", "🎵"),
    APPS("Окна/Софт", "🪟"),
    POWER("Питание", "⚡"),
    SETTINGS("Опции", "⚙️")
}

data class HttpResult(val code: Int, val body: String)
data class AppItem(val id: String, val title: String, val canClose: Boolean, val running: Boolean)
data class WindowItem(val hwnd: Long, val title: String, val process: String, val path: String, val active: Boolean, val minimized: Boolean)
data class PcStatusSnapshot(val apps: List<AppItem>, val activeWindow: String, val ramPercent: Int, val shutdownRemainingSec: Int, val shutdownActive: Boolean, val volume: Int = -1)
data class UpdateInfo(val available: Boolean, val versionCode: Long, val versionName: String, val hash: String, val size: Long)
data class OtaCheckResult(val update: UpdateInfo?, val message: String)

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
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.8"
    } catch (_: Exception) {
        "0.0"
    }

suspend fun http(
    host: String,
    token: String,
    path: String,
    method: String,
    bodyText: String? = null,
    timeoutMs: Int = 3500
): HttpResult =
    withContext(Dispatchers.IO) {
        val conn = URL("http://$host:$PORT$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs + 3500
            val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
            conn.setRequestProperty("X-Token", safeToken)
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
    onFrame: (ImageBitmap) -> Unit
) = withContext(Dispatchers.IO) {
    val monParam = if (allMonitors) "all" else "0"
    val conn = URL("http://$host:$PORT/stream?fps=$fps&w=$width&q=$quality&m=$monParam").openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 3000
        conn.readTimeout = 6000
        val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
        conn.setRequestProperty("X-Token", safeToken)
        if (conn.responseCode != 200) return@withContext
        val stream = conn.inputStream.buffered(16384)
        var reusableBitmap: Bitmap? = null
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inMutable = true
        }
        val lineBuf = java.io.ByteArrayOutputStream(128)

        fun readLineAscii(): String? {
            lineBuf.reset()
            while (true) {
                val b = stream.read()
                if (b == -1) return null
                if (b == '\n'.code) {
                    val bytes = lineBuf.toByteArray()
                    val len = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                    return String(bytes, 0, len, Charsets.US_ASCII)
                }
                lineBuf.write(b)
            }
        }

        while (coroutineContext.isActive) {
            var contentLength = -1
            while (coroutineContext.isActive) {
                val line = readLineAscii() ?: return@withContext
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1
                } else if (line.isEmpty() && contentLength > 0) {
                    break
                }
            }
            if (contentLength <= 0) break

            val frameBytes = ByteArray(contentLength)
            var readTotal = 0
            while (readTotal < contentLength && coroutineContext.isActive) {
                val r = stream.read(frameBytes, readTotal, contentLength - readTotal)
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
                opts.inBitmap = reusableBitmap
                val bmp = try {
                    BitmapFactory.decodeByteArray(frameBytes, 0, contentLength, opts)
                } catch (_: IllegalArgumentException) {
                    opts.inBitmap = null
                    BitmapFactory.decodeByteArray(frameBytes, 0, contentLength, opts)
                }
                if (bmp != null) {
                    reusableBitmap = bmp
                    val img = bmp.asImageBitmap()
                    withContext(Dispatchers.Main) {
                        onFrame(img)
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
        val conn = URL("http://$host:$PORT/screen?w=$width&q=$quality&m=$monParam").openConnection() as HttpURLConnection
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
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
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
                // Читаем настоящий хеш работающего сейчас приложения из файла base.apk
                val runningApkHash = try {
                    computeFileSha256(File(context.packageCodePath))
                } catch (_: Exception) {
                    ""
                }

                // Если установленный APK физически имеет тот же хеш — обновляться не нужно
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

            val conn = URL("http://$host:$PORT/update/download").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 60000
            val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
            conn.setRequestProperty("X-Token", safeToken)
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

            // Проверка целостности SHA-256
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

fun parseStatusSnapshot(json: String): PcStatusSnapshot {
    val root = JSONObject(json)
    val arr = root.getJSONArray("apps")
    val appList = List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        AppItem(o.getString("id"), o.getString("title"), o.getBoolean("canClose"), o.getBoolean("running"))
    }
    val remSec = root.optInt("shutdownRemainingSec", 0)
    val active = root.optBoolean("shutdownActive", remSec > 0)
    return PcStatusSnapshot(
        apps = appList,
        activeWindow = root.optString("activeWindow", ""),
        ramPercent = root.optInt("ramPercent", 0),
        shutdownRemainingSec = remSec,
        shutdownActive = active,
        volume = root.optInt("volume", -1)
    )
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

@Composable
private fun RowScope.CoolButton(
    text: String,
    weight: Float = 1f,
    filled: Boolean = false,
    accentColor: Color? = null,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val trigger: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
    if (filled) {
        Button(
            onClick = trigger,
            contentPadding = KeyPadding,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor ?: MaterialTheme.colorScheme.primary,
                contentColor = if (accentColor != null) Color.White else MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.weight(weight)
        ) { Text(text, textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    } else {
        OutlinedButton(
            onClick = trigger,
            contentPadding = KeyPadding,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(weight)
        ) { Text(text, textAlign = TextAlign.Center, fontSize = 13.sp) }
    }
}

suspend fun uploadMediaFile(
    context: Context,
    host: String,
    token: String,
    uri: Uri,
    displayName: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        val encodedName = java.net.URLEncoder.encode(displayName, "UTF-8")
        val conn = URL("http://$host:$PORT/upload/media").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 6000
        conn.readTimeout = 120000
        val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
        conn.setRequestProperty("X-Token", safeToken)
        conn.setRequestProperty("X-Filename", encodedName)
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.doOutput = true

        val stream = context.contentResolver.openInputStream(uri) ?: return@withContext false
        val size = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        } catch (_: Exception) { -1L }

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
    onStatus: (String) -> Unit
): Int = withContext(Dispatchers.IO) {
    if (host.isBlank() || token.isBlank()) return@withContext 0
    val prefs = context.getSharedPreferences("pc_control_media", Context.MODE_PRIVATE)
    val uploadedSet = prefs.getStringSet("uploaded_ids", emptySet())?.toMutableSet() ?: mutableSetOf()

    val urisToQuery = listOf(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    )

    var uploadedCount = 0
    for (contentUri in urisToQuery) {
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val cursor = try {
            context.contentResolver.query(
                contentUri,
                proj,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )
        } catch (_: Exception) {
            null
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            var checked = 0
            while (c.moveToNext() && checked < 50) {
                checked++
                val id = c.getLong(idCol)
                val idStr = "${contentUri.lastPathSegment}_$id"
                if (uploadedSet.contains(idStr)) continue

                val name = c.getString(nameCol) ?: "media_$id.jpg"
                val itemUri = ContentUris.withAppendedId(contentUri, id)

                withContext(Dispatchers.Main) {
                    onStatus("Отправляю $name на ПК…")
                }

                val ok = uploadMediaFile(context, host, token, itemUri, name)
                if (ok) {
                    uploadedSet.add(idStr)
                    prefs.edit().putStringSet("uploaded_ids", uploadedSet).apply()
                    uploadedCount++
                }
            }
        }
    }
    if (uploadedCount > 0) {
        withContext(Dispatchers.Main) {
            onStatus("Отправлено новых файлов: $uploadedCount ✔")
        }
    }
    uploadedCount
}

@Composable
fun PcControlMainApp(
    incomingSharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val activity = context as ComponentActivity
    val prefs = remember { context.getSharedPreferences("pc_control", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf(prefs.getString("host", "") ?: "") }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var mac by remember { mutableStateOf(prefs.getString("mac", "") ?: "") }
    var installedApkHash by remember { mutableStateOf(prefs.getString("installed_apk_hash", "") ?: "") }
    var mouseSensitivity by remember { mutableFloatStateOf(prefs.getFloat("mouse_sens", 1.6f)) }

    // Автосинхронизация фото и видео на ПК
    var autoSyncMedia by remember { mutableStateOf(prefs.getBoolean("auto_sync_media", false)) }
    var mediaSyncStatus by remember { mutableStateOf("") }

    val mediaPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        if (granted) {
            autoSyncMedia = true
            prefs.edit().putBoolean("auto_sync_media", true).apply()
            scope.launch {
                syncPendingMedia(context, host.trim(), token.trim()) { mediaSyncStatus = it }
            }
        } else {
            mediaSyncStatus = "Разрешите доступ к фото для автозагрузки"
        }
    }

    // Текущая вкладка навигации
    var currentTab by remember { mutableStateOf(if (host.isBlank() || token.isBlank()) NavTab.SETTINGS else NavTab.SCREEN) }

    // Трансляция экрана
    var isStreamingActive by remember { mutableStateOf(false) } // ПО УМОЛЧАНИЮ СТРОГО ВЫКЛЮЧЕНА
    var streamFps by remember { mutableIntStateOf(60) } // 30, 60, 90
    var streamHd by remember { mutableStateOf(false) }
    var streamAllMonitors by remember { mutableStateOf(false) }
    var directTapMode by remember { mutableStateOf(true) }
    var streamZoom by remember { mutableFloatStateOf(1f) }
    var screenBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Окна, софт, звук
    var openWindows by remember { mutableStateOf<List<WindowItem>>(emptyList()) }
    var volumeSlider by remember { mutableFloatStateOf(40f) }
    var hasInitializedVolume by remember { mutableStateOf(false) }
    var isAdjustingVolume by remember { mutableStateOf(false) }
    var videoUrl by remember { mutableStateOf("") }
    var textToType by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var activeWindow by remember { mutableStateOf("") }
    var ramPercent by remember { mutableIntStateOf(0) }
    var shutdownRemainingSec by remember { mutableIntStateOf(0) }
    var shutdownActive by remember { mutableStateOf(false) }
    var pcOnline by remember { mutableStateOf<Boolean?>(null) }
    var tokenError by remember { mutableStateOf(false) }

    // Диалог добавления программы
    var showAddAppDialog by remember { mutableStateOf(false) }
    var newAppTitle by remember { mutableStateOf("") }
    var newAppPath by remember { mutableStateOf("") }
    var newAppProcess by remember { mutableStateOf("") }
    var newAppForce by remember { mutableStateOf(false) }

    // OTA обновление
    var otaUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    var accDx by remember { mutableFloatStateOf(0f) }
    var accDy by remember { mutableFloatStateOf(0f) }

    fun save() {
        prefs.edit()
            .putString("host", host.trim())
            .putString("token", token.trim())
            .putString("mac", mac.trim())
            .putFloat("mouse_sens", mouseSensitivity)
            .apply()
    }

    fun wake() {
        save()
        status = "Отправляю сигнал WoL…"
        scope.launch { status = sendMagicPacket(mac) }
    }

    suspend fun refresh() {
        if (host.isBlank() || token.isBlank()) {
            pcOnline = null
            apps = emptyList()
            return
        }
        try {
            val r = http(host.trim(), token.trim(), "/apps", "GET", timeoutMs = 2500)
            pcOnline = true
            tokenError = r.code == 401
            if (r.code == 200) {
                val snap = parseStatusSnapshot(r.body)
                apps = snap.apps
                activeWindow = snap.activeWindow
                ramPercent = snap.ramPercent
                shutdownRemainingSec = snap.shutdownRemainingSec
                shutdownActive = snap.shutdownActive || (snap.shutdownRemainingSec > 0)
                if (!isAdjustingVolume && snap.volume >= 0) {
                    volumeSlider = snap.volume.toFloat()
                    hasInitializedVolume = true
                }
            } else {
                apps = emptyList()
            }
            if (currentTab == NavTab.APPS && r.code == 200) {
                val wr = http(host.trim(), token.trim(), "/windows", "GET", timeoutMs = 2000)
                if (wr.code == 200) openWindows = parseOpenWindows(wr.body)
            }
        } catch (e: Exception) {
            pcOnline = false
            tokenError = false
            apps = emptyList()
        }
    }

    fun run(path: String, method: String = "POST", bodyText: String? = null, refreshAfter: Boolean = true) {
        save()
        status = "Отправляю…"
        scope.launch {
            status = command(host.trim(), token.trim(), path, method, bodyText)
            if (refreshAfter) {
                delay(350)
                refresh()
            }
        }
    }

    fun sendWindowAction(hwnd: Long, action: String) {
        val payload = JSONObject().apply {
            put("hwnd", hwnd)
            put("action", action)
        }.toString()
        scope.launch {
            status = command(host.trim(), token.trim(), "/windows", "POST", payload)
            delay(300)
            refresh()
        }
    }

    fun sendMouseAction(action: String, dy: Int = 0, nx: Float? = null, ny: Float? = null) {
        if (host.isBlank() || token.isBlank()) return
        val payload = JSONObject().apply {
            put("action", action)
            if (dy != 0) put("dy", dy)
            if (nx != null) put("nx", String.format(Locale.US, "%.4f", nx).toDouble())
            if (ny != null) put("ny", String.format(Locale.US, "%.4f", ny).toDouble())
            if (streamAllMonitors) put("allMonitors", true)
        }.toString()
        scope.launch {
            try {
                http(host.trim(), token.trim(), "/mouse", "POST", payload, timeoutMs = 1000)
            } catch (_: Exception) {
            }
        }
    }

    fun sendExactVolume(percent: Int) {
        save()
        volumeSlider = percent.toFloat()
        status = "Громкость: $percent%…"
        scope.launch {
            val res = command(host.trim(), token.trim(), "/volume", "POST", percent.toString())
            status = if (res == "Готово ✔") "Громкость: $percent% ✔" else res
        }
    }

    fun sendTypeText(enter: Boolean) {
        if (textToType.isEmpty() && !enter) {
            status = "Введи текст"
            return
        }
        save()
        val payload = JSONObject().apply {
            put("text", textToType)
            put("enter", enter)
        }.toString()
        status = "Печатаю на ПК…"
        scope.launch {
            val res = command(host.trim(), token.trim(), "/type", "POST", payload)
            status = if (res == "Готово ✔") "Текст отправлен ✔" else res
            if (res == "Готово ✔") textToType = ""
        }
    }

    fun sendUrlToPc(urlOrQuery: String) {
        val clean = urlOrQuery.trim()
        if (clean.isEmpty()) {
            status = "Введи ссылку или поисковый запрос"
            return
        }
        save()
        val isSearch = !clean.startsWith("http://") && !clean.startsWith("https://") && (" " in clean || "." !in clean)
        status = if (isSearch) "Ищу на YouTube…" else "Открываю на ПК…"
        scope.launch {
            val res = command(host.trim(), token.trim(), "/open-url", "POST", clean)
            status = if (res == "Готово ✔") {
                if (isSearch) "Поиск открыт ✔" else "Открыто на ПК ✔"
            } else res
            delay(500)
            refresh()
        }
    }

    fun pasteAndOpenClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0)?.coerceToText(context)?.toString()?.trim() ?: "" else ""
        if (text.isEmpty()) {
            status = "Буфер обмена пуст"
            return
        }
        val foundUrl = Regex("""https?://\S+""").find(text)?.value
        val target = foundUrl ?: text
        videoUrl = target
        sendUrlToPc(target)
    }

    fun sendPhoneClipboardToPc() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0)?.coerceToText(context)?.toString() ?: "" else ""
        if (text.isEmpty()) {
            status = "Буфер телефона пуст"
            return
        }
        save()
        scope.launch {
            val res = command(host.trim(), token.trim(), "/clipboard", "POST", text)
            status = if (res == "Готово ✔") "Скопировано на ПК ✔" else res
        }
    }

    fun fetchPcClipboardToPhone() {
        save()
        scope.launch {
            try {
                val r = http(host.trim(), token.trim(), "/clipboard", "GET")
                if (r.code == 200 && r.body.isNotEmpty()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("PC Clipboard", r.body))
                    status = "Буфер с ПК получен ✔"
                } else {
                    status = "Буфер ПК пуст"
                }
            } catch (_: Exception) {
                status = "Ошибка чтения буфера ПК"
            }
        }
    }

    fun submitNewApp() {
        val title = newAppTitle.trim()
        val launch = if (newAppPath.isNotBlank()) newAppPath.trim() else title
        if (title.isBlank() && launch.isBlank()) {
            status = "Введите название программы"
            return
        }
        val payload = JSONObject().apply {
            put("title", title.ifEmpty { launch })
            put("launch", launch)
            if (newAppProcess.isNotBlank()) put("process", newAppProcess.trim())
            put("force", newAppForce)
        }.toString()
        scope.launch {
            val res = command(host.trim(), token.trim(), "/apps/add", "POST", payload)
            status = if (res == "Готово ✔") "Программа добавлена ✔" else res
            showAddAppDialog = false
            newAppTitle = ""
            newAppPath = ""
            newAppProcess = ""
            newAppForce = false
            refresh()
        }
    }

    fun startCustomTimer(minutes: Int, action: String) {
        val payload = JSONObject().apply {
            put("minutes", minutes)
            put("action", action)
        }.toString()
        scope.launch {
            status = command(host.trim(), token.trim(), "/timer", "POST", payload)
            delay(300)
            refresh()
        }
    }

    fun quickAddAppFromWindow(win: WindowItem) {
        val cleanTitle = win.title.split("-").first().trim().ifEmpty { win.process.replace(".exe", "").replaceFirstChar { it.titlecase() } }
        val payload = JSONObject().apply {
            put("title", cleanTitle)
            put("launch", win.path.ifEmpty { win.process })
            put("process", win.process)
        }.toString()
        scope.launch {
            status = command(host.trim(), token.trim(), "/apps/add", "POST", payload)
            delay(300)
            refresh()
        }
    }

    fun deleteApp(appId: String) {
        scope.launch {
            val res = command(host.trim(), token.trim(), "/apps/$appId/delete", "POST")
            status = if (res == "Готово ✔") "Удалено ✔" else res
            refresh()
        }
    }

    fun startOtaUpdate() {
        val update = otaUpdateInfo ?: return
        isDownloadingUpdate = true
        downloadProgress = 0f
        scope.launch {
            val res = downloadAndInstallApk(context, host.trim(), token.trim(), update.hash) { progress ->
                downloadProgress = progress
            }
            status = res
            // Хеш проверяется напрямую из context.packageCodePath при запуске
            isDownloadingUpdate = false
            if (res.startsWith("Запуск установщика")) {
                otaUpdateInfo = null
            }
        }
    }

    // Автоматическая отправка новых фото и видео на ПК
    LaunchedEffect(autoSyncMedia, host, token) {
        if (autoSyncMedia && host.isNotBlank() && token.isNotBlank()) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    scope.launch {
                        syncPendingMedia(context, host.trim(), token.trim()) { mediaSyncStatus = it }
                    }
                }
            }
            try {
                context.contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer
                )
                context.contentResolver.registerContentObserver(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer
                )
                syncPendingMedia(context, host.trim(), token.trim()) { mediaSyncStatus = it }
                awaitCancellation()
            } finally {
                try {
                    context.contentResolver.unregisterContentObserver(observer)
                } catch (_: Exception) {
                }
            }
        }
    }

    // Автосохранение настроек с дебаунсом (без микрофризов при вводе)
    LaunchedEffect(host, token, mac) {
        delay(800)
        save()
    }

    LaunchedEffect(host, token) {
        if (host.isNotBlank() && token.isNotBlank()) {
            val res = checkOtaUpdate(context, host.trim(), token.trim(), installedApkHash)
            otaUpdateInfo = res.update
        }
    }

    LaunchedEffect(incomingSharedUrl) {
        if (!incomingSharedUrl.isNullOrBlank()) {
            videoUrl = incomingSharedUrl
            currentTab = NavTab.MEDIA
            if (host.isNotBlank() && token.isNotBlank()) {
                sendUrlToPc(incomingSharedUrl)
            } else {
                status = "Укажите адрес ПК в настройках"
                currentTab = NavTab.SETTINGS
            }
            onSharedUrlConsumed()
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == NavTab.APPS) refresh()
    }

    // Непрерывный высокоскоростной видеопоток экрана (MJPEG поток 60/90/120 FPS + HTTP fallback)
    LaunchedEffect(isStreamingActive, streamFps, streamHd, streamAllMonitors, host, token, currentTab) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isStreamingActive && currentTab == NavTab.SCREEN && host.isNotBlank() && token.isNotBlank()) {
                val w = when {
                    streamHd -> 1280
                    streamFps >= 120 -> 540
                    streamFps >= 90 -> 640
                    streamFps >= 60 -> 720
                    else -> 854
                }
                val q = when {
                    streamHd -> 68
                    streamFps >= 120 -> 36
                    streamFps >= 90 -> 42
                    streamFps >= 60 -> 48
                    else -> 55
                }
                val targetFps = if (streamHd) minOf(60, streamFps) else streamFps

                var gotFrames = false
                try {
                    runMjpegStream(host.trim(), token.trim(), targetFps, w, q, streamAllMonitors) { bmp ->
                        screenBitmap = bmp
                        pcOnline = true
                        gotFrames = true
                    }
                } catch (_: Exception) {
                }

                if (!gotFrames && isStreamingActive && currentTab == NavTab.SCREEN) {
                    val bmp = fetchScreenBitmap(host.trim(), token.trim(), w, q, streamAllMonitors)
                    if (bmp != null) {
                        screenBitmap = bmp
                        pcOnline = true
                    }
                    delay(33L)
                } else {
                    delay(200L)
                }
            }
        }
    }

    // Высокоскоростной цикл мыши (UDP 125 Гц для плавного движения + HTTP fallback) с закрытием сокета
    LaunchedEffect(currentTab, host, token) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val udpSocket = try { DatagramSocket() } catch (_: Exception) { null }
            try {
                var resolvedIp: InetAddress? = null
                var lastResolvedHost = ""
                while (currentTab == NavTab.SCREEN && host.isNotBlank() && token.isNotBlank()) {
                    val dx = accDx.roundToInt()
                    val dy = accDy.roundToInt()
                    if (dx != 0 || dy != 0) {
                        accDx -= dx
                        accDy -= dy
                        var sentUdp = false
                        if (udpSocket != null) {
                            try {
                                if (host != lastResolvedHost || resolvedIp == null) {
                                    resolvedIp = withContext(Dispatchers.IO) { InetAddress.getByName(host.trim()) }
                                    lastResolvedHost = host
                                }
                                val pkt = "${token.trim()}:m:$dx,$dy".toByteArray(Charsets.UTF_8)
                                val packet = DatagramPacket(pkt, pkt.size, resolvedIp, PORT)
                                withContext(Dispatchers.IO) { udpSocket.send(packet) }
                                sentUdp = true
                            } catch (_: Exception) {
                                sentUdp = false
                            }
                        }
                        if (!sentUdp) {
                            val payload = """{"action":"move","dx":$dx,"dy":$dy}"""
                            try {
                                http(host.trim(), token.trim(), "/mouse", "POST", payload, timeoutMs = 400)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    delay(8L) // 125 Гц — игровой плавный опрос без рывков
                }
            } finally {
                udpSocket?.close()
            }
        }
    }

    LaunchedEffect(host, token) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 8.dp
            ) {
                NavTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentTab = tab
                        },
                        icon = { Text(tab.icon, fontSize = 20.sp) },
                        label = {
                            Text(
                                tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentTab == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Верхняя стильная шапка
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Пульт ПК",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• ${currentTab.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val (dotColor, stateText) = when (pcOnline) {
                    true -> Color(0xFF10B981) to if (tokenError) "Токен неверен" else "В сети"
                    false -> Color(0xFFEF4444) to "Не в сети"
                    null -> Color(0xFF6B7280) to "Настройка"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
                    Text(stateText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }

            // Статус и телеметрия
            if (status.isNotEmpty()) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (pcOnline == true && !tokenError) {
                val infoParts = mutableListOf<String>()
                if (ramPercent > 0) infoParts.add("ОЗУ: $ramPercent%")
                if (shutdownRemainingSec > 0) {
                    val mins = (shutdownRemainingSec + 59) / 60
                    infoParts.add("⏱ Выкл. через $mins мин")
                }
                if (activeWindow.isNotBlank()) infoParts.add("Окно: $activeWindow")
                if (infoParts.isNotEmpty()) {
                    Text(
                        infoParts.joinToString("  •  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Баннер OTA-обновления (появляется только если есть РЕАЛЬНО НОВЫЙ файл с другим хешем)
            if (otaUpdateInfo != null && !isDownloadingUpdate) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Доступно обновление на ПК! 🚀", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Файл: ${otaUpdateInfo!!.versionName}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { otaUpdateInfo = null }) {
                                Text("Позже", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { startOtaUpdate() },
                                contentPadding = KeyPadding,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Обновить ⬇")
                            }
                        }
                    }
                }
            } else if (isDownloadingUpdate) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(progress = { downloadProgress }, modifier = Modifier.size(20.dp))
                        Text(
                            "Загрузка обновления: ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // ==================== 1. ЭКРАН И МЫШЬ ====================
            if (currentTab == NavTab.SCREEN) {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Трансляция экрана", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (isStreamingActive) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                                    Text("Прямой эфир", color = Color(0xFF10B981), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (!isStreamingActive) {
                            Button(
                                onClick = { isStreamingActive = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("▶ Запустить трансляцию экрана", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "Трансляция выключена (не расходует сеть и заряд батареи)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedButton(
                                onClick = { isStreamingActive = false },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("⏹ Остановить трансляцию", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }

                            // Панель переключения FPS: 30 / 60 / 90
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("FPS:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                listOf(30, 60, 90, 120).forEach { fpsVal ->
                                    FilterChip(
                                        selected = streamFps == fpsVal,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            streamFps = fpsVal
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = if (fpsVal == 90) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = Color.White
                                        ),
                                        label = { Text(if (fpsVal == 90) "⚡ 90 FPS" else "$fpsVal FPS") }
                                    )
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CoolButton(if (streamHd) "HD ✔" else "SD", filled = streamHd) { streamHd = !streamHd }
                                CoolButton("🔍 ${streamZoom.toInt()}×") {
                                    streamZoom = when (streamZoom) {
                                        1f -> 2f
                                        2f -> 3f
                                        else -> 1f
                                    }
                                }
                                CoolButton(if (streamAllMonitors) "Все экр." else "1 экр.") { streamAllMonitors = !streamAllMonitors }
                                CoolButton(if (directTapMode) "🎯 В точку" else "🖱 Свайп") { directTapMode = !directTapMode }
                            }

                            val currentBmp = screenBitmap
                            val aspect = if (currentBmp != null && currentBmp.height > 0) {
                                (currentBmp.width.toFloat() / currentBmp.height.toFloat()).coerceIn(0.5f, 4.0f)
                            } else {
                                16f / 9f
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspect)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentBmp != null) {
                                    Image(
                                        bitmap = currentBmp,
                                        contentDescription = "Экран ПК",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(scaleX = streamZoom, scaleY = streamZoom)
                                            .pointerInput(host, token, directTapMode, streamAllMonitors, streamZoom) {
                                                detectTapGestures(
                                                    onTap = { offset ->
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        if (directTapMode && size.width > 0 && size.height > 0) {
                                                            val nx = (((offset.x / size.width.toFloat()) - 0.5f) / streamZoom + 0.5f).coerceIn(0f, 1f)
                                                            val ny = (((offset.y / size.height.toFloat()) - 0.5f) / streamZoom + 0.5f).coerceIn(0f, 1f)
                                                            sendMouseAction("abs_click", nx = nx, ny = ny)
                                                        } else {
                                                            sendMouseAction("left")
                                                        }
                                                    },
                                                    onDoubleTap = { offset ->
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (directTapMode && size.width > 0 && size.height > 0) {
                                                            val nx = (((offset.x / size.width.toFloat()) - 0.5f) / streamZoom + 0.5f).coerceIn(0f, 1f)
                                                            val ny = (((offset.y / size.height.toFloat()) - 0.5f) / streamZoom + 0.5f).coerceIn(0f, 1f)
                                                            sendMouseAction("abs_double", nx = nx, ny = ny)
                                                        } else {
                                                            sendMouseAction("double")
                                                        }
                                                    },
                                                    onLongPress = { offset ->
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (directTapMode && size.width > 0 && size.height > 0) {
                                                            val nx = (((offset.x / size.width.toFloat()) - 0.5f) / streamZoom + 0.5f).coerceIn(0f, 1f)
                                                            val ny = (((offset.y / size.height.toFloat()) - 0.5f) / streamZoom + 0.5f).coerceIn(0f, 1f)
                                                            sendMouseAction("abs_right", nx = nx, ny = ny)
                                                        } else {
                                                            sendMouseAction("right")
                                                        }
                                                    }
                                                )
                                            }
                                            .pointerInput(host, token) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    accDx += dragAmount.x * mouseSensitivity
                                                    accDy += dragAmount.y * mouseSensitivity
                                                }
                                            }
                                    )
                                } else {
                                    Text("Ожидание первого кадра…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CoolButton("ЛКМ") { sendMouseAction("left") }
                                CoolButton("ПКМ") { sendMouseAction("right") }
                                CoolButton("⬆ Скролл") { sendMouseAction("scroll", dy = 3) }
                                CoolButton("⬇ Скролл") { sendMouseAction("scroll", dy = -3) }
                                CoolButton("Alt+Tab") { run("/media/alt_tab", refreshAfter = true) }
                            }
                        }
                    }
                }

                // Карточка тачпада и клавиатуры
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Тачпад и ввод текста", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Скорость: ${String.format(Locale.US, "%.1f", mouseSensitivity)}×", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Чувствительность мыши
                        Slider(
                            value = mouseSensitivity,
                            onValueChange = { mouseSensitivity = it; save() },
                            valueRange = 0.8f..3.0f,
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(135.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .pointerInput(host, token) {
                                    detectTapGestures(
                                        onTap = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            sendMouseAction("left")
                                        },
                                        onDoubleTap = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            sendMouseAction("double")
                                        },
                                        onLongPress = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            sendMouseAction("right")
                                        }
                                    )
                                }
                                .pointerInput(host, token) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        accDx += dragAmount.x * mouseSensitivity
                                        accDy += dragAmount.y * mouseSensitivity
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Сенсорная панель\nТап — ЛКМ • 2×Тап — 2×Клик • Удержание — ПКМ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoolButton("ЛКМ") { sendMouseAction("left") }
                            CoolButton("2× Клик") { sendMouseAction("double") }
                            CoolButton("ПКМ") { sendMouseAction("right") }
                            CoolButton("Скролл ⬆") { sendMouseAction("scroll", dy = 3) }
                            CoolButton("Скролл ⬇") { sendMouseAction("scroll", dy = -3) }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = textToType,
                                onValueChange = { textToType = it },
                                label = { Text("Печать на ПК (RU / EN)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { sendTypeText(enter = true) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(56.dp)
                            ) {
                                Text("Ввод ↵", fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoolButton("⌨ Без Enter") { sendTypeText(enter = false) }
                            CoolButton("⌫ Стереть") { run("/media/key_backspace", refreshAfter = false) }
                            CoolButton("Пробел") { run("/media/key_space", refreshAfter = false) }
                            CoolButton("Esc") { run("/media/key_esc", refreshAfter = false) }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoolButton("📋 Буфер → ПК") { sendPhoneClipboardToPc() }
                            CoolButton("📥 Буфер ← ПК") { fetchPcClipboardToPhone() }
                            CoolButton("Alt+Tab") { run("/media/alt_tab", refreshAfter = true) }
                            CoolButton("Alt+F4") { run("/media/alt_f4", refreshAfter = true) }
                        }
                    }
                }
            }

            // ==================== 2. МЕДИА И YOUTUBE ====================
            if (currentTab == NavTab.MEDIA) {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("YouTube и ссылки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = videoUrl,
                                onValueChange = { videoUrl = it },
                                label = { Text("Ссылка или поиск на YouTube") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { sendUrlToPc(videoUrl) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(56.dp)) {
                                Text("На ПК ▶", fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoolButton("📋 Из буфера на ПК", weight = 1.1f) { pasteAndOpenClipboard() }
                            CoolButton("✕ Закрыть вкладку") { run("/media/close_tab", refreshAfter = false) }
                            CoolButton("↩ Вернуть вкладку") { run("/media/reopen_tab", refreshAfter = false) }
                        }
                    }
                }

                // Громкость: только удобная регулировочная полоса + процент и кнопка аудиовыхода
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val vol = volumeSlider.roundToInt()
                        val volIcon = when {
                            vol == 0 -> "🔇"
                            vol <= 30 -> "🔈"
                            vol <= 70 -> "🔉"
                            else -> "🔊"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(volIcon, fontSize = 24.sp)
                                Text("Громкость звука", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("$vol%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                OutlinedButton(
                                    onClick = { run("/media/audio_output", refreshAfter = false) },
                                    contentPadding = KeyPadding,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("🎧 Выход")
                                }
                            }
                        }

                        Slider(
                            value = volumeSlider,
                            onValueChange = {
                                isAdjustingVolume = true
                                volumeSlider = it
                            },
                            onValueChangeFinished = {
                                isAdjustingVolume = false
                                sendExactVolume(volumeSlider.roundToInt())
                            },
                            valueRange = 0f..100f,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                }

                // Пульт плеера и YouTube (чистый вид без кнопок скорости и субтитров)
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Пульт плеера и YouTube", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoolButton("⏮ Пред.") { run("/media/prev", refreshAfter = false) }
                            CoolButton("⏯ Пауза / Play", weight = 1.4f, filled = true) { run("/media/play_pause", refreshAfter = false) }
                            CoolButton("⏭ След.") { run("/media/next", refreshAfter = false) }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoolButton("⏪ -10с") { run("/media/yt_back_10", refreshAfter = false) }
                            CoolButton("-5с") { run("/media/yt_back", refreshAfter = false) }
                            CoolButton("⛶ Полный экран", weight = 1.2f) { run("/media/yt_fullscreen", refreshAfter = false) }
                            CoolButton("+5с") { run("/media/yt_forward", refreshAfter = false) }
                            CoolButton("+10с ⏩") { run("/media/yt_forward_10", refreshAfter = false) }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoolButton("⬆ Shorts выше") { run("/media/yt_up", refreshAfter = false) }
                            CoolButton("⬇ Shorts ниже") { run("/media/yt_down", refreshAfter = false) }
                            CoolButton("🖥 Свернуть окна") { run("/media/win_d", refreshAfter = false) }
                        }
                    }
                }
            }

            // ==================== 3. ОКНА И СОФТ ====================
            if (currentTab == NavTab.APPS) {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Открытые окна на ПК", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { scope.launch { refresh() } }, contentPadding = KeyPadding, shape = RoundedCornerShape(10.dp)) {
                                Text("Обновить 🔄")
                            }
                        }

                        if (openWindows.isEmpty()) {
                            Text("Окна не найдены или список обновляется…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        openWindows.forEach { win ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (win.active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(win.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (win.active) "${win.process} • активно" else win.process,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (win.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(onClick = { sendWindowAction(win.hwnd, "focus") }, contentPadding = KeyPadding, shape = RoundedCornerShape(8.dp)) { Text("Экран") }
                                OutlinedButton(onClick = { quickAddAppFromWindow(win) }, contentPadding = KeyPadding, shape = RoundedCornerShape(8.dp)) { Text("+ В софт") }
                                OutlinedButton(onClick = { sendWindowAction(win.hwnd, "close") }, contentPadding = KeyPadding, shape = RoundedCornerShape(8.dp)) { Text("✕") }
                            }
                        }
                    }
                }

                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Программы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        if (apps.isEmpty()) {
                            Text("Список программ пуст.", style = MaterialTheme.typography.bodyMedium)
                        }
                        apps.forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.title, style = MaterialTheme.typography.bodyLarge)
                                    if (app.running) {
                                        Text("работает", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Button(onClick = { run("/apps/${app.id}/launch") }, contentPadding = KeyPadding, shape = RoundedCornerShape(8.dp)) {
                                    Text("Пуск")
                                }
                                if (app.canClose) {
                                    OutlinedButton(onClick = { run("/apps/${app.id}/close") }, contentPadding = KeyPadding, shape = RoundedCornerShape(8.dp)) {
                                        Text("Закрыть")
                                    }
                                }
                                OutlinedButton(onClick = { deleteApp(app.id) }, contentPadding = KeyPadding, shape = RoundedCornerShape(8.dp)) {
                                    Text("🗑")
                                }
                            }
                        }
                    }
                }
            }

            // ==================== 4. ПИТАНИЕ И ЭКРАН ====================
            if (currentTab == NavTab.POWER) {
                // Умная плашка отмены: отображается ТОЛЬКО когда активен таймер выключения!
                if (shutdownActive || shutdownRemainingSec > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("⏱", fontSize = 24.sp)
                                Column {
                                    Text("Таймер выключения ПК запущен", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    val mins = (shutdownRemainingSec + 59) / 60
                                    Text("ПК выключится через $mins мин.", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Button(
                                onClick = { run("/cancel", refreshAfter = true) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("❌ Отменить выключение ПК", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Управление питанием", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        Button(
                            onClick = { wake() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Включить ПК (Wake-on-LAN)", fontWeight = FontWeight.Bold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoolButton("Выключить", accentColor = Color(0xFFDC2626), filled = true) {
                                pending = "/shutdown" to "Выключить ПК"
                            }
                            CoolButton("Сон 💤", accentColor = Color(0xFF7C3AED), filled = true) {
                                pending = "/sleep" to "Перевести ПК в режим сна"
                            }
                            CoolButton("Рестарт") {
                                pending = "/restart" to "Перезагрузить ПК"
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoolButton("🌙 Экран выкл.") { run("/monitor_off", refreshAfter = false) }
                            CoolButton("🔒 Блокировка") { run("/lock", refreshAfter = false) }
                        }
                    }
                }

                // Таймер сна и выключения: удобный ручной ввод минут + быстрые кнопки
                var timerMinutesInput by remember { mutableStateOf("30") }

                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Таймер сна и выключения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = timerMinutesInput,
                            onValueChange = { timerMinutesInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Время в минутах (например: 45)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Быстрые прибавки к таймеру, чтобы не открывать клавиатуру
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoolButton("+15м") {
                                val cur = timerMinutesInput.toIntOrNull() ?: 0
                                timerMinutesInput = (cur + 15).toString()
                            }
                            CoolButton("+30м") {
                                val cur = timerMinutesInput.toIntOrNull() ?: 0
                                timerMinutesInput = (cur + 30).toString()
                            }
                            CoolButton("+60м") {
                                val cur = timerMinutesInput.toIntOrNull() ?: 0
                                timerMinutesInput = (cur + 60).toString()
                            }
                            CoolButton("Сброс") { timerMinutesInput = "30" }
                        }

                        val chosenMinutes = timerMinutesInput.toIntOrNull() ?: 30
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CoolButton("⏱ Выключить через $chosenMinutes м", accentColor = Color(0xFFDC2626), filled = true) {
                                startCustomTimer(chosenMinutes, "shutdown")
                            }
                            CoolButton("💤 Сон через $chosenMinutes м", accentColor = Color(0xFF7C3AED), filled = true) {
                                startCustomTimer(chosenMinutes, "sleep")
                            }
                        }
                    }
                }
            }

            // ==================== 5. ОПЦИИ И НАСТРОЙКИ ====================
            if (currentTab == NavTab.SETTINGS) {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Настройки подключения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Адрес ПК (Tailscale IP или локальный)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Токен (из config.json на ПК)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = mac,
                            onValueChange = { mac = it },
                            label = { Text("MAC-адрес ПК (для Wake-on-LAN)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Обновление приложения (OTA)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Установленная версия: v${getInstalledVersionName(context)} (код ${getInstalledVersionCode(context)})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    status = "Проверяю папку updates на ПК…"
                                    val res = checkOtaUpdate(context, host.trim(), token.trim(), installedApkHash)
                                    otaUpdateInfo = res.update
                                    status = res.message
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Проверить обновления на ПК 🔄")
                        }
                    }
                }

                // Карточка автозагрузки фото и видео на ПК
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Автозагрузка фото и видео 📸", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Новые фото и видео с камеры сразу сохраняются в папку photos на ПК через Tailscale",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.size(8.dp))
                            Switch(
                                checked = autoSyncMedia,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        } else {
                                            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        }
                                        if (hasPerm) {
                                            autoSyncMedia = true
                                            prefs.edit().putBoolean("auto_sync_media", true).apply()
                                            scope.launch {
                                                syncPendingMedia(context, host.trim(), token.trim()) { mediaSyncStatus = it }
                                            }
                                        } else {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                mediaPermLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
                                            } else {
                                                mediaPermLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                                            }
                                        }
                                    } else {
                                        autoSyncMedia = false
                                        prefs.edit().putBoolean("auto_sync_media", false).apply()
                                    }
                                }
                            )
                        }

                        if (mediaSyncStatus.isNotBlank()) {
                            Text(
                                mediaSyncStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = if ("✔" in mediaSyncStatus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (autoSyncMedia) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        mediaSyncStatus = "Проверяю новые фото и видео…"
                                        val count = syncPendingMedia(context, host.trim(), token.trim()) { mediaSyncStatus = it }
                                        if (count == 0) mediaSyncStatus = "Все фото и видео уже на ПК ✔"
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Синхронизировать сейчас 🔄")
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог подтверждения выключения/сна
    pending?.let { (path, title) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("$title?") },
            text = { Text("Команда будет выполнена на ПК немедленно.") },
            confirmButton = {
                TextButton(onClick = { pending = null; run(path) }) { Text("Да") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Отмена") }
            }
        )
    }


}