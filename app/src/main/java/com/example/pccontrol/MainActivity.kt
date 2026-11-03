package com.example.pccontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
            val darkTheme = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PcControlScreen(
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

private const val CURRENT_APP_VERSION_CODE = 2
private const val PORT = 8765
private const val POLL_INTERVAL_MS = 3000L
private const val MOUSE_SENSITIVITY = 1.6f
private val KeyPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)

data class HttpResult(val code: Int, val body: String)
data class AppItem(val id: String, val title: String, val canClose: Boolean, val running: Boolean)
data class WindowItem(val hwnd: Long, val title: String, val process: String, val active: Boolean, val minimized: Boolean)
data class PcStatusSnapshot(val apps: List<AppItem>, val activeWindow: String, val ramPercent: Int, val shutdownRemainingSec: Int)
data class UpdateInfo(val available: Boolean, val versionCode: Long, val versionName: String, val size: Long)

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
            if (conn.responseCode != 200) return@withContext null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

suspend fun checkOtaUpdate(host: String, token: String): UpdateInfo? =
    withContext(Dispatchers.IO) {
        try {
            val r = http(host, token, "/update/check", "GET", timeoutMs = 2500)
            if (r.code == 200) {
                val o = JSONObject(r.body)
                val avail = o.optBoolean("available", false)
                val vCode = o.optLong("versionCode", 0L)
                val vName = o.optString("versionName", "")
                val size = o.optLong("size", 0L)
                if (avail && vCode > CURRENT_APP_VERSION_CODE) {
                    return@withContext UpdateInfo(true, vCode, vName, size)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

suspend fun downloadAndInstallApk(
    context: Context,
    host: String,
    token: String,
    onProgress: (Float) -> Unit
): String =
    withContext(Dispatchers.IO) {
        try {
            val conn = URL("http://$host:$PORT/update/download").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 60000
            val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
            conn.setRequestProperty("X-Token", safeToken)
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

            // Запуск системного установщика пакетов Android через FileProvider
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
            "Запуск обновления…"
        } catch (e: Exception) {
            "Ошибка обновления: ${e.localizedMessage ?: e.javaClass.simpleName}"
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
    return PcStatusSnapshot(
        apps = appList,
        activeWindow = root.optString("activeWindow", ""),
        ramPercent = root.optInt("ramPercent", 0),
        shutdownRemainingSec = root.optInt("shutdownRemainingSec", 0)
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
                return@withContext "MAC-адрес должен состоять из 12 HEX-символов, например A1:B2:C3:D4:E5:F6"
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
private fun SectionHeader(
    title: String,
    isOpen: Boolean,
    onToggle: () -> Unit,
    badgeText: String? = null
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (isOpen) "▼" else "▶", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (badgeText != null) {
            Text(badgeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowScope.KeyButton(
    text: String,
    weight: Float = 1f,
    filled: Boolean = false,
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
            modifier = Modifier.weight(weight)
        ) { Text(text, textAlign = TextAlign.Center) }
    } else {
        OutlinedButton(
            onClick = trigger,
            contentPadding = KeyPadding,
            modifier = Modifier.weight(weight)
        ) { Text(text, textAlign = TextAlign.Center) }
    }
}

@Composable
fun PcControlScreen(
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

    // Видимость блоков (аккордеоны)
    var showSettings by remember { mutableStateOf(host.isBlank() || token.isBlank()) }
    var showScreenStream by remember { mutableStateOf(false) }
    var showTouchpad by remember { mutableStateOf(false) }
    var showYoutube by remember { mutableStateOf(false) }
    var showWindows by remember { mutableStateOf(false) }
    var showVolumePlayer by remember { mutableStateOf(true) }
    var showPower by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(true) }

    // Состояния трансляции экрана, Ultra-Smooth FPS и зума
    var streamPaused by remember { mutableStateOf(false) }
    var streamSmoothFps by remember { mutableStateOf(true) } // Высокая плавность
    var streamHd by remember { mutableStateOf(false) }
    var streamAllMonitors by remember { mutableStateOf(false) }
    var directTapMode by remember { mutableStateOf(true) }
    var streamZoom by remember { mutableFloatStateOf(1f) }
    var screenBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Окна, программы, громкость
    var openWindows by remember { mutableStateOf<List<WindowItem>>(emptyList()) }
    var volumeSlider by remember { mutableFloatStateOf(40f) }
    var videoUrl by remember { mutableStateOf("") }
    var textToType by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var activeWindow by remember { mutableStateOf("") }
    var ramPercent by remember { mutableIntStateOf(0) }
    var shutdownRemainingSec by remember { mutableIntStateOf(0) }
    var pcOnline by remember { mutableStateOf<Boolean?>(null) }
    var tokenError by remember { mutableStateOf(false) }

    // Диалог добавления программы
    var showAddAppDialog by remember { mutableStateOf(false) }
    var newAppTitle by remember { mutableStateOf("") }
    var newAppPath by remember { mutableStateOf("") }
    var newAppProcess by remember { mutableStateOf("") }
    var newAppForce by remember { mutableStateOf(false) }

    // Состояния OTA обновления
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
            .apply()
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
            if (r.code == 401) showSettings = true
            if (r.code == 200) {
                val snap = parseStatusSnapshot(r.body)
                apps = snap.apps
                activeWindow = snap.activeWindow
                ramPercent = snap.ramPercent
                shutdownRemainingSec = snap.shutdownRemainingSec
            } else {
                apps = emptyList()
            }
            if (showWindows && r.code == 200) {
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
                delay(400)
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
                http(host.trim(), token.trim(), "/mouse", "POST", payload, timeoutMs = 1200)
            } catch (_: Exception) {
            }
        }
    }

    fun sendExactVolume(percent: Int) {
        save()
        volumeSlider = percent.toFloat()
        status = "Громкость $percent%…"
        scope.launch {
            val res = command(host.trim(), token.trim(), "/volume", "POST", percent.toString())
            status = if (res == "Готово ✔") "Громкость: $percent% ✔" else res
        }
    }

    fun sendTypeText(enter: Boolean) {
        if (textToType.isEmpty() && !enter) {
            status = "Введи текст для отправки на ПК"
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
            status = if (res == "Готово ✔") "Текст введён ✔" else res
            if (res == "Готово ✔") textToType = ""
        }
    }

    fun sendUrlToPc(urlOrQuery: String) {
        val clean = urlOrQuery.trim()
        if (clean.isEmpty()) {
            status = "Вставь ссылку или поисковый запрос"
            return
        }
        save()
        val isSearch = !clean.startsWith("http://") && !clean.startsWith("https://") && (" " in clean || "." !in clean)
        status = if (isSearch) "Ищу на YouTube…" else "Открываю на ПК…"
        scope.launch {
            val res = command(host.trim(), token.trim(), "/open-url", "POST", clean)
            status = if (res == "Готово ✔") {
                if (isSearch) "Поиск открыт на ПК ✔" else "Открыто на ПК ✔"
            } else res
            delay(500)
            refresh()
        }
    }

    fun pasteAndOpenClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim() ?: ""
        if (text.isEmpty()) {
            status = "Буфер обмена телефона пуст"
            return
        }
        val foundUrl = Regex("""https?://\S+""").find(text)?.value
        val target = foundUrl ?: text
        videoUrl = target
        sendUrlToPc(target)
    }

    fun sendPhoneClipboardToPc() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
        if (text.isEmpty()) {
            status = "Буфер телефона пуст"
            return
        }
        save()
        scope.launch {
            val res = command(host.trim(), token.trim(), "/clipboard", "POST", text)
            status = if (res == "Готово ✔") "Скопировано в буфер ПК ✔" else res
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
                    status = "Буфер ПК скопирован в телефон ✔"
                } else {
                    status = "Буфер обмена ПК пуст"
                }
            } catch (_: Exception) {
                status = "Не удалось прочитать буфер ПК"
            }
        }
    }

    fun submitNewApp() {
        if (newAppTitle.isBlank() || newAppPath.isBlank()) {
            status = "Заполни название и путь к файлу"
            return
        }
        val payload = JSONObject().apply {
            put("title", newAppTitle.trim())
            put("launch", newAppPath.trim())
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
            status = downloadAndInstallApk(context, host.trim(), token.trim()) { progress ->
                downloadProgress = progress
            }
            isDownloadingUpdate = false
            otaUpdateInfo = null
        }
    }

    // Автопроверка обновлений по воздуху при старте
    LaunchedEffect(host, token) {
        if (host.isNotBlank() && token.isNotBlank()) {
            otaUpdateInfo = checkOtaUpdate(host.trim(), token.trim())
        }
    }

    LaunchedEffect(incomingSharedUrl) {
        if (!incomingSharedUrl.isNullOrBlank()) {
            videoUrl = incomingSharedUrl
            if (host.isNotBlank() && token.isNotBlank()) {
                sendUrlToPc(incomingSharedUrl)
            } else {
                status = "Укажи адрес ПК и токен в настройках"
                showSettings = true
            }
            onSharedUrlConsumed()
        }
    }

    LaunchedEffect(showWindows) {
        if (showWindows) refresh()
    }

    // Турбо-цикл трансляции экрана с поддержкой до 60 FPS
    LaunchedEffect(showScreenStream, streamPaused, streamHd, streamAllMonitors, streamSmoothFps, host, token) {
        while (showScreenStream && !streamPaused && host.isNotBlank() && token.isNotBlank()) {
            val w = when {
                streamHd -> 1366
                streamSmoothFps -> 720  // Оптимально для высокой частоты кадров
                else -> 854
            }
            val q = when {
                streamHd -> 72
                streamSmoothFps -> 48  // Меньше вес -> ультра-быстрая передача по Wi-Fi
                else -> 55
            }
            val bmp = fetchScreenBitmap(host.trim(), token.trim(), w, q, streamAllMonitors)
            if (bmp != null) {
                screenBitmap = bmp
                pcOnline = true
            }
            // 20 мс = ~50 FPS (плавно как на 180 Гц мониторе)
            val delayMs = when {
                streamSmoothFps -> 20L
                streamHd -> 150L
                else -> 80L
            }
            delay(delayMs)
        }
    }

    LaunchedEffect(showTouchpad, showScreenStream, host, token) {
        while ((showTouchpad || showScreenStream) && host.isNotBlank() && token.isNotBlank()) {
            val dx = accDx.roundToInt()
            val dy = accDy.roundToInt()
            if (dx != 0 || dy != 0) {
                accDx -= dx
                accDy -= dy
                val payload = """{"action":"move","dx":$dx,"dy":$dy}"""
                try {
                    http(host.trim(), token.trim(), "/mouse", "POST", payload, timeoutMs = 800)
                } catch (_: Exception) {
                }
            }
            delay(20L)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Заголовок и статус
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Управление ПК", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val (dotColor, stateText) = when (pcOnline) {
                true -> Color(0xFF4CAF50) to if (tokenError) "Токен неверен" else "В сети"
                false -> Color(0xFFE53935) to "Не в сети"
                null -> Color(0xFF9E9E9E) to "Настройка"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
                Text(stateText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (status.isNotEmpty()) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Блок уведомления об OTA обновлении
        if (otaUpdateInfo != null && !isDownloadingUpdate) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Найдено обновление приложения", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Версия ${otaUpdateInfo!!.versionName} доступна на ПК", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { startOtaUpdate() }, contentPadding = KeyPadding) {
                        Text("Обновить ⬇")
                    }
                }
            }
        } else if (isDownloadingUpdate) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(progress = { downloadProgress }, modifier = Modifier.size(24.dp))
                    Text(
                        "Загрузка обновления: ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Телеметрия
        if (pcOnline == true && !tokenError) {
            val infoParts = mutableListOf<String>()
            if (ramPercent > 0) infoParts.add("ОЗУ: $ramPercent%")
            if (shutdownRemainingSec > 0) {
                val mins = (shutdownRemainingSec + 59) / 60
                infoParts.add("⏱ Выкл: $mins мин")
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

        // 1. Блок настроек (скрыт под стрелочку)
        SectionHeader(
            title = "Настройки подключения",
            isOpen = showSettings,
            onToggle = { showSettings = !showSettings },
            badgeText = if (!showSettings && host.isNotBlank()) host else null
        )
        AnimatedVisibility(visible = showSettings) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; save() },
                    label = { Text("Адрес ПК (Tailscale IP или локальный)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; save() },
                    label = { Text("Токен (из config.json)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it; save() },
                    label = { Text("MAC-адрес ПК (для кнопки «Включить»)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyButton("Проверить обновления 🔄") {
                        scope.launch {
                            val u = checkOtaUpdate(host.trim(), token.trim())
                            otaUpdateInfo = u
                            status = if (u != null) "Найдено обновление!" else "Установлена последняя версия"
                        }
                    }
                }
            }
        }

        // 2. Трансляция экрана (плавная, с зумом и быстрым кликом)
        SectionHeader(
            title = "📺 Трансляция экрана ПК",
            isOpen = showScreenStream,
            onToggle = {
                showScreenStream = !showScreenStream
                if (showScreenStream) streamPaused = false
            },
            badgeText = if (showScreenStream) (if (streamSmoothFps) "50 FPS" else "Стандарт") else null
        )
        AnimatedVisibility(visible = showScreenStream) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton(if (streamPaused) "▶ Пуск" else "⏸ Пауза") {
                        streamPaused = !streamPaused
                    }
                    KeyButton(if (streamSmoothFps) "⚡ 50 FPS" else "30 FPS", filled = streamSmoothFps) {
                        streamSmoothFps = !streamSmoothFps
                        if (streamSmoothFps) streamHd = false
                    }
                    KeyButton(if (streamHd) "HD" else "SD", filled = streamHd) {
                        streamHd = !streamHd
                        if (streamHd) streamSmoothFps = false
                    }
                    KeyButton("🔍 ${streamZoom.toInt()}×") {
                        streamZoom = when (streamZoom) {
                            1f -> 2f
                            2f -> 3f
                            else -> 1f
                        }
                    }
                    KeyButton(if (streamAllMonitors) "Все экр." else "1 экр.") {
                        streamAllMonitors = !streamAllMonitors
                    }
                    KeyButton(if (directTapMode) "🎯 В точку" else "🖱 Тач") {
                        directTapMode = !directTapMode
                    }
                }

                val currentBmp = screenBitmap
                val aspect = if (currentBmp != null && currentBmp.height > 0) {
                    (currentBmp.width.toFloat() / currentBmp.height.toFloat()).coerceIn(1.2f, 3.2f)
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
                                .pointerInput(host, token, directTapMode, streamAllMonitors) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (directTapMode && size.width > 0 && size.height > 0) {
                                                val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                                sendMouseAction("abs_click", nx = nx, ny = ny)
                                            } else {
                                                sendMouseAction("left")
                                            }
                                        },
                                        onDoubleTap = { offset ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (directTapMode && size.width > 0 && size.height > 0) {
                                                val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                                sendMouseAction("abs_double", nx = nx, ny = ny)
                                            } else {
                                                sendMouseAction("double")
                                            }
                                        },
                                        onLongPress = { offset ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (directTapMode && size.width > 0 && size.height > 0) {
                                                val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
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
                                        accDx += dragAmount.x * MOUSE_SENSITIVITY
                                        accDy += dragAmount.y * MOUSE_SENSITIVITY
                                    }
                                }
                        )
                    } else {
                        Text("Подключение к экрану…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton("ЛКМ") { sendMouseAction("left") }
                    KeyButton("ПКМ") { sendMouseAction("right") }
                    KeyButton("⬆ Скролл") { sendMouseAction("scroll", dy = 3) }
                    KeyButton("⬇ Скролл") { sendMouseAction("scroll", dy = -3) }
                    KeyButton("Alt+Tab") { run("/media/alt_tab", refreshAfter = true) }
                }
            }
        }

        // 3. YouTube и ссылки (скрыт под стрелочку)
        SectionHeader(
            title = "YouTube и ссылки",
            isOpen = showYoutube,
            onToggle = { showYoutube = !showYoutube }
        )
        AnimatedVisibility(visible = showYoutube) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = videoUrl,
                        onValueChange = { videoUrl = it },
                        label = { Text("Ссылка или поиск в YouTube") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { sendUrlToPc(videoUrl) }, modifier = Modifier.height(56.dp)) {
                        Text("На ПК ▶")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyButton("📋 Из буфера", weight = 1.1f) { pasteAndOpenClipboard() }
                    KeyButton("✕ Вкладку") { run("/media/close_tab", refreshAfter = false) }
                    KeyButton("↩ Вернуть") { run("/media/reopen_tab", refreshAfter = false) }
                }
            }
        }

        // 4. Звук, плеер и управление медиа
        SectionHeader(
            title = "Звук и медиаплеер",
            isOpen = showVolumePlayer,
            onToggle = { showVolumePlayer = !showVolumePlayer }
        )
        AnimatedVisibility(visible = showVolumePlayer) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("${volumeSlider.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = volumeSlider,
                        onValueChange = { volumeSlider = it },
                        onValueChangeFinished = { sendExactVolume(volumeSlider.roundToInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { run("/media/audio_output", refreshAfter = false) },
                        contentPadding = KeyPadding
                    ) {
                        Text("🎧 Выход")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton("−10%") { run("/media/vol_down_10", refreshAfter = false) }
                    KeyButton("Тише") { run("/media/vol_down", refreshAfter = false) }
                    KeyButton("Мут") { run("/media/vol_mute", refreshAfter = false) }
                    KeyButton("Громче") { run("/media/vol_up", refreshAfter = false) }
                    KeyButton("+10%") { run("/media/vol_up_10", refreshAfter = false) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyButton("⏮ Пред.") { run("/media/prev", refreshAfter = false) }
                    KeyButton("⏯ Пауза / Play", weight = 1.3f, filled = true) { run("/media/play_pause", refreshAfter = false) }
                    KeyButton("⏭ След.") { run("/media/next", refreshAfter = false) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton("⏪ -10с") { run("/media/yt_back_10", refreshAfter = false) }
                    KeyButton("-5с") { run("/media/yt_back", refreshAfter = false) }
                    KeyButton("⛶ Экран", weight = 1.2f) { run("/media/yt_fullscreen", refreshAfter = false) }
                    KeyButton("+5с") { run("/media/yt_forward", refreshAfter = false) }
                    KeyButton("+10с ⏩") { run("/media/yt_forward_10", refreshAfter = false) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton("⬆ Выше") { run("/media/yt_up", refreshAfter = false) }
                    KeyButton("⬇ Ниже") { run("/media/yt_down", refreshAfter = false) }
                    KeyButton("💬 Субтитры") { run("/media/yt_subtitles", refreshAfter = false) }
                    KeyButton("🖥 Свернуть") { run("/media/win_d", refreshAfter = false) }
                }
            }
        }

        // 5. Тачпад, клавиатура и буфер обмена
        SectionHeader(
            title = "Тачпад и ввод текста",
            isOpen = showTouchpad,
            onToggle = { showTouchpad = !showTouchpad }
        )
        AnimatedVisibility(visible = showTouchpad) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
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
                                accDx += dragAmount.x * MOUSE_SENSITIVITY
                                accDy += dragAmount.y * MOUSE_SENSITIVITY
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Тачпад: свайп — курсор • Тап — ЛКМ • 2×Тап — 2×Клик • Удержание — ПКМ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton("ЛКМ") { sendMouseAction("left") }
                    KeyButton("2× Клик") { sendMouseAction("double") }
                    KeyButton("ПКМ") { sendMouseAction("right") }
                    KeyButton("Скролл ⬆") { sendMouseAction("scroll", dy = 3) }
                    KeyButton("Скролл ⬇") { sendMouseAction("scroll", dy = -3) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textToType,
                        onValueChange = { textToType = it },
                        label = { Text("Текст для печати на ПК") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { sendTypeText(enter = true) }, modifier = Modifier.height(56.dp)) {
                        Text("Ввод ↵")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton("⌨ Без Enter") { sendTypeText(enter = false) }
                    KeyButton("⌫ Стереть") { run("/media/key_backspace", refreshAfter = false) }
                    KeyButton("Пробел") { run("/media/key_space", refreshAfter = false) }
                    KeyButton("Esc") { run("/media/key_esc", refreshAfter = false) }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton("📋 Буфер → ПК") { sendPhoneClipboardToPc() }
                    KeyButton("📥 Буфер ← ПК") { fetchPcClipboardToPhone() }
                    KeyButton("Alt+Tab") { run("/media/alt_tab", refreshAfter = true) }
                    KeyButton("Alt+F4") { run("/media/alt_f4", refreshAfter = true) }
                }
            }
        }

        // 6. Открытые окна на ПК
        SectionHeader(
            title = "Открытые окна на ПК",
            isOpen = showWindows,
            onToggle = { showWindows = !showWindows },
            badgeText = if (showWindows && openWindows.isNotEmpty()) "${openWindows.size} окон" else null
        )
        AnimatedVisibility(visible = showWindows) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                if (openWindows.isEmpty()) {
                    Text("Нет открытых окон или список обновляется…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                openWindows.forEach { win ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                        Button(onClick = { sendWindowAction(win.hwnd, "focus") }, contentPadding = KeyPadding) { Text("На экран") }
                        OutlinedButton(onClick = { sendWindowAction(win.hwnd, "minimize") }, contentPadding = KeyPadding) { Text("—") }
                        OutlinedButton(onClick = { sendWindowAction(win.hwnd, "close") }, contentPadding = KeyPadding) { Text("✕") }
                    }
                }
            }
        }

        // 7. Программы и возможность добавлять свои
        SectionHeader(
            title = "Программы",
            isOpen = showApps,
            onToggle = { showApps = !showApps },
            badgeText = if (apps.isNotEmpty()) "${apps.size} шт." else null
        )
        AnimatedVisibility(visible = showApps) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { showAddAppDialog = true }, contentPadding = KeyPadding) {
                        Text("+ Добавить программу")
                    }
                }

                if (apps.isEmpty()) {
                    Text(
                        if (pcOnline == true && !tokenError) "Список пуст." else "Список появится при подключении к ПК.",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                        Button(onClick = { run("/apps/${app.id}/launch") }, contentPadding = KeyPadding) {
                            Text("Пуск")
                        }
                        if (app.canClose) {
                            OutlinedButton(onClick = { run("/apps/${app.id}/close") }, contentPadding = KeyPadding) {
                                Text("Закрыть")
                            }
                        }
                        OutlinedButton(onClick = { deleteApp(app.id) }, contentPadding = KeyPadding) {
                            Text("🗑")
                        }
                    }
                }
            }
        }

        // 8. Питание и экран (скрыт под стрелочку)
        SectionHeader(
            title = "Питание и экран",
            isOpen = showPower,
            onToggle = { showPower = !showPower }
        )
        AnimatedVisibility(visible = showPower) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { scope.launch { status = sendMagicPacket(mac) } }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Включить ПК (Wake-on-LAN)")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { pending = "/shutdown" to "Выключить ПК" },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Выключить") }
                    Button(
                        onClick = { pending = "/sleep" to "Перевести ПК в режим сна" },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Сон 💤") }
                    Button(
                        onClick = { pending = "/restart" to "Перезагрузить ПК" },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Рестарт") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyButton("🌙 Экран выкл.") { run("/monitor_off", refreshAfter = false) }
                    KeyButton("🔒 Блок экрана") { run("/lock", refreshAfter = false) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton("⏱ 30м") { run("/shutdown_30m", refreshAfter = true) }
                    KeyButton("⏱ 60м") { run("/shutdown_60m", refreshAfter = true) }
                    KeyButton("⏱ 2ч") { run("/shutdown_120m", refreshAfter = true) }
                    KeyButton("Отмена") { run("/cancel", refreshAfter = true) }
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

    // Диалог добавления программы прямо с телефона
    if (showAddAppDialog) {
        AlertDialog(
            onDismissRequest = { showAddAppDialog = false },
            title = { Text("Добавить программу") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newAppTitle,
                        onValueChange = { newAppTitle = it },
                        label = { Text("Название (например: Telegram)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAppPath,
                        onValueChange = { newAppPath = it },
                        label = { Text("Путь к exe (например: %APPDATA%\\Telegram...\\Telegram.exe)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAppProcess,
                        onValueChange = { newAppProcess = it },
                        label = { Text("Имя процесса для закрытия (например: Telegram.exe)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newAppForce, onCheckedChange = { newAppForce = it })
                        Text("Принудительное закрытие (/F)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { submitNewApp() }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showAddAppDialog = false }) { Text("Отмена") }
            }
        )
    }
}
