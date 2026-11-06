package com.example.pccontrol

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PcControlMainApp(
    incomingSharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    themeId: String = "cyber",
    onThemeChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val activity = context as ComponentActivity
    val prefs = remember { context.getSharedPreferences("pc_control", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun triggerHapticClick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20L)
            }
        } catch (_: Exception) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun triggerHapticTick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(10L)
            }
        } catch (_: Exception) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun triggerHapticHeavy() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40L)
            }
        } catch (_: Exception) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val initialProfiles = remember {
        val raw = prefs.getString("saved_profiles_json", "") ?: ""
        if (raw.isNotBlank()) {
            try {
                val arr = JSONArray(raw)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    PcProfile(o.getString("id"), o.getString("name"), o.getString("host"), o.getString("token"), o.getString("mac"))
                }
            } catch (_: Exception) {
                listOf(PcProfile("1", "Основной ПК", prefs.getString("host", "") ?: "", prefs.getString("token", "") ?: "", prefs.getString("mac", "") ?: ""))
            }
        } else {
            listOf(PcProfile("1", "Основной ПК", prefs.getString("host", "") ?: "", prefs.getString("token", "") ?: "", prefs.getString("mac", "") ?: ""))
        }
    }
    var profiles by remember { mutableStateOf(initialProfiles) }
    val initialActiveId = remember {
        val savedId = prefs.getString("active_profile_id", "") ?: ""
        if (initialProfiles.any { it.id == savedId }) savedId else (initialProfiles.firstOrNull()?.id ?: "1")
    }
    var activeProfileId by remember { mutableStateOf(initialActiveId) }
    val initialActiveProfile = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()

    var host by remember { mutableStateOf(initialActiveProfile?.host ?: prefs.getString("host", "") ?: "") }
    var token by remember { mutableStateOf(initialActiveProfile?.token ?: prefs.getString("token", "") ?: "") }
    // Два режима подключения: дома (IP в Wi-Fi) и вне дома (Cloudflare-адрес); host — то, что выбрано сейчас
    var lanHost by remember {
        mutableStateOf(prefs.getString("lan_host_$initialActiveId", null) ?: (initialActiveProfile?.host ?: "").takeIf { isLanAddress(it) } ?: "")
    }
    var remoteHost by remember {
        mutableStateOf(prefs.getString("remote_host_$initialActiveId", null) ?: (initialActiveProfile?.host ?: "").takeIf { isRemoteAddress(it) } ?: "")
    }
    var connMode by remember { mutableStateOf(prefs.getString("conn_mode", "auto") ?: "auto") }
    var routeLabel by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf(initialActiveProfile?.mac ?: prefs.getString("mac", "") ?: "") }
    val installedApkHash by remember { mutableStateOf(prefs.getString("installed_apk_hash", "") ?: "") }
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
                syncPendingMedia(context, host.trim(), token.trim(), activeProfileId) { mediaSyncStatus = it }
            }
        } else {
            mediaSyncStatus = "Разрешите доступ к фото для автозагрузки"
        }
    }

    // Текущая вкладка навигации
    var currentTab by remember { mutableStateOf(if (host.isBlank() || token.isBlank()) NavTab.SETTINGS else NavTab.SCREEN) }

    // Live HUD трансляции
    var liveStreamFps by remember { mutableIntStateOf(0) }
    var liveStreamPing by remember { mutableLongStateOf(0L) }

    // Мониторинг ПК
    var cpuPercent by remember { mutableIntStateOf(0) }
    var uptimeSec by remember { mutableIntStateOf(0) }
    var ramTotalGb by remember { mutableStateOf(0.0) }
    var ramUsedGb by remember { mutableStateOf(0.0) }
    var disks by remember { mutableStateOf<List<DiskItem>>(emptyList()) }
    var isTunnelRunning by remember { mutableStateOf(false) }
    var currentTunnelUrl by remember { mutableStateOf("") }

    // Состояние сворачивания карточек в Окна/Софт
    var isProgramsExpanded by remember { mutableStateOf(true) }
    var isWindowsExpanded by remember { mutableStateOf(false) }

    // Настройки трансляции и полноэкранный режим
    var showStreamFpsOverlay by remember { mutableStateOf(prefs.getBoolean("show_stream_fps_overlay", true)) }
    var isFullscreenStream by remember { mutableStateOf(false) }
    var isFsControlsVisible by remember { mutableStateOf(false) }
    var isFsMouseMode by remember { mutableStateOf(false) }

    // Проводник файлов ПК
    var fileBrowsePath by remember { mutableStateOf("D:\\") }
    var fileBrowseParent by remember { mutableStateOf("") }
    var fileBrowseDrives by remember { mutableStateOf<List<String>>(listOf("C:", "D:")) }
    var fileBrowseEntries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var fileBrowseStatus by remember { mutableStateOf("") }
    var isBrowsingFiles by remember { mutableStateOf(false) }

    // Управление профилями ПК
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    // Трансляция экрана
    var isStreamingActive by remember { mutableStateOf(false) }
    // Пресет качества стрима: 0 = ⚡ 60 FPS Турбо (960p, q=52), 1 = ⚖️ Баланс (1280p, q=62, 45 FPS), 2 = 🔍 FHD 1080p (1920p, q=72, 30 FPS)
    var streamQualityPreset by remember {
        mutableIntStateOf(prefs.getInt("stream_quality_preset", 0))
    }
    var streamFps by remember { mutableIntStateOf(60) }
    var streamAllMonitors by remember { mutableStateOf(false) }
    var directTapMode by remember { mutableStateOf(true) }
    var streamZoom by remember { mutableFloatStateOf(1f) }
    var streamPanX by remember { mutableFloatStateOf(0f) }
    var streamPanY by remember { mutableFloatStateOf(0f) }
    var streamFhdMode by remember { mutableStateOf(false) }

    // Отслеживание скачивания файлов для кнопки "Открыть"
    var downloadingFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadedFiles by remember { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var screenBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Окна, софт, звук
    var openWindows by remember { mutableStateOf<List<WindowItem>>(emptyList()) }
    var volumeSlider by remember { mutableFloatStateOf(40f) }
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
            .putString("lan_host_$activeProfileId", lanHost.trim())
            .putString("remote_host_$activeProfileId", remoteHost.trim())
            .putString("conn_mode", connMode)
            .putString("active_profile_id", activeProfileId)
            .putFloat("mouse_sens", mouseSensitivity)
            .apply()

        val updated = profiles.map { p ->
            if (p.id == activeProfileId) p.copy(host = host.trim(), token = token.trim(), mac = mac.trim()) else p
        }
        profiles = updated
        val arr = JSONArray()
        updated.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("host", p.host)
                put("token", p.token)
                put("mac", p.mac)
            })
        }
        prefs.edit().putString("saved_profiles_json", arr.toString()).apply()
    }

    fun wake() {
        triggerHapticHeavy()
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
            val timeout = if (host.contains("trycloudflare.com") || host.startsWith("https://")) 7500 else 4000
            val r = http(host.trim(), token.trim(), "/apps", "GET", timeoutMs = timeout)
            if (r.code == 200) {
                pcOnline = true
                tokenError = false
                try {
                    val snap = parseStatusSnapshot(r.body)
                    apps = snap.apps
                    activeWindow = snap.activeWindow
                    ramPercent = snap.ramPercent
                    ramTotalGb = snap.ramTotalGb
                    ramUsedGb = snap.ramUsedGb
                    cpuPercent = snap.cpuPercent
                    uptimeSec = snap.uptimeSec
                    disks = snap.disks
                    shutdownRemainingSec = snap.shutdownRemainingSec
                    shutdownActive = snap.shutdownActive || (snap.shutdownRemainingSec > 0)
                    if (!isAdjustingVolume && snap.volume >= 0) {
                        volumeSlider = snap.volume.toFloat()
                    }
                } catch (_: Exception) {
                }
            } else if (r.code == 401) {
                pcOnline = true
                tokenError = true
                status = "🔑 Ошибка: токен не совпадает с config.json на ПК!"
                apps = emptyList()
            } else {
                pcOnline = false
                tokenError = false
                status = "Ответ ПК: HTTP ${r.code}"
                apps = emptyList()
            }
            if (currentTab == NavTab.APPS && r.code == 200) {
                val wr = http(host.trim(), token.trim(), "/windows", "GET", timeoutMs = 3000)
                if (wr.code == 200) openWindows = parseOpenWindows(wr.body)
            }
        } catch (e: Exception) {
            pcOnline = false
            tokenError = false
            apps = emptyList()
            status = "Сбой подключения: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    fun run(path: String, method: String = "POST", bodyText: String? = null, refreshAfter: Boolean = true) {
        triggerHapticClick()
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

    var cachedUdpIp: InetAddress? = null
    var cachedUdpHost = ""
    val udpMouseSocket by lazy { try { DatagramSocket() } catch (_: Exception) { null } }

    fun sendAbsMouse(nx: Float, ny: Float, click: Boolean = false) {
        if (host.isBlank() || token.isBlank()) return
        if (click) triggerHapticClick()
        val nxStr = String.format(Locale.US, "%.4f", nx)
        val nyStr = String.format(Locale.US, "%.4f", ny)
        val isTunnel = host.contains("trycloudflare.com") || host.startsWith("https://")

        if (isTunnel) {
            val payload = JSONObject().apply {
                put("action", if (click) "abs_click" else "abs_move")
                put("nx", nxStr.toDouble())
                put("ny", nyStr.toDouble())
                if (streamAllMonitors) put("allMonitors", true)
            }.toString()
            scope.launch(Dispatchers.IO) {
                try {
                    http(host.trim(), token.trim(), "/mouse", "POST", payload, timeoutMs = 400)
                } catch (_: Exception) {}
            }
        } else {
            val cmd = if (click) "tap" else "abs"
            val packetData = "$token:$cmd:$nxStr,$nyStr".toByteArray(Charsets.UTF_8)
            scope.launch(Dispatchers.IO) {
                try {
                    if (cachedUdpHost != host.trim() || cachedUdpIp == null) {
                        cachedUdpIp = InetAddress.getByName(host.trim())
                        cachedUdpHost = host.trim()
                    }
                    val addr = cachedUdpIp ?: return@launch
                    val sock = udpMouseSocket ?: return@launch
                    val packet = DatagramPacket(packetData, packetData.size, addr, PORT)
                    sock.send(packet)
                } catch (_: Exception) {
                    val payload = JSONObject().apply {
                        put("action", if (click) "abs_click" else "abs_move")
                        put("nx", nxStr.toDouble())
                        put("ny", nyStr.toDouble())
                        if (streamAllMonitors) put("allMonitors", true)
                    }.toString()
                    try {
                        http(host.trim(), token.trim(), "/mouse", "POST", payload, timeoutMs = 600)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { udpMouseSocket?.close() } catch (_: Exception) {}
        }
    }


    fun captureScreenshotToPhone() {
        if (host.isBlank() || token.isBlank()) return
        status = "Делаю скриншот экрана ПК…"
        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(buildPcUrl(host, "/screen?w=1920&q=92"))
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("X-Token", token)
                conn.connectTimeout = 6000
                if (conn.responseCode == 200) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = android.content.ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "PC_Screenshot_${System.currentTimeMillis()}.jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        status = "Скриншот экрана ПК сохранён в Галерею 📸 ✔"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        status = "Не удалось сделать скриншот: HTTP ${conn.responseCode}"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    status = "Ошибка снимка: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    var diagnosticsReport by remember { mutableStateOf("") }
    var isDiagnosing by remember { mutableStateOf(false) }

    fun runDiagnostics() {
        if (host.isBlank()) {
            diagnosticsReport = "⚠️ Укажите адрес ПК для проверки связи."
            return
        }
        isDiagnosing = true
        diagnosticsReport = "🩺 Проверяю связь с ПК…"
        scope.launch(Dispatchers.IO) {
            val sb = StringBuilder()
            sb.appendLine("=== РЕЗУЛЬТАТ ДИАГНОСТИКИ СВЯЗИ ===")
            sb.appendLine("• Проверяемый адрес: $host")
            val t0 = System.currentTimeMillis()
            try {
                val pingUrl = buildPcUrl(host, "/ping")
                sb.appendLine("• Запрос к шлюзу: $pingUrl")
                val conn = URL(pingUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                val safeTok = if (token.all { it.code in 0x20..0x7E }) token else ""
                conn.setRequestProperty("X-Token", safeTok)
                val code = conn.responseCode
                val elapsed = System.currentTimeMillis() - t0
                sb.appendLine("• Ответ сервера: HTTP $code (время: ${elapsed} мс)")

                if (code == 200) {
                    val raw = conn.inputStream.bufferedReader().use { it.readText() }
                    try {
                        val json = JSONObject(raw)
                        val authOk = json.optBoolean("authorized", false)
                        val pc = json.optString("pc", "ПК")
                        sb.appendLine("• Имя компьютера: $pc")
                        if (authOk) {
                            sb.appendLine("✅ УСПЕХ: Связь установлена, секретный токен ВЕРНЫЙ!")
                            withContext(Dispatchers.Main) {
                                pcOnline = true
                                tokenError = false
                                status = "ПК ($pc) на связи ✔ (${elapsed} мс)"
                                scope.launch { refresh() }
                            }
                        } else {
                            sb.appendLine("🔑 ОШИБКА АВТОРИЗАЦИИ: Компьютер доступен, но введён неверный токен!")
                            sb.appendLine("👉 Откройте server.py или config.json на ПК и скопируйте токен оттуда.")
                            withContext(Dispatchers.Main) {
                                pcOnline = true
                                tokenError = true
                                status = "🔑 Неверный токен ПК! Проверьте в config.json"
                            }
                        }
                    } catch (_: Exception) {
                        sb.appendLine("✅ Сервер ответил 'pong' (связь есть)")
                        withContext(Dispatchers.Main) {
                            pcOnline = true
                            scope.launch { refresh() }
                        }
                    }
                } else if (code == 401) {
                    sb.appendLine("🔑 ОШИБКА 401: Сервер найден, но токен подключения НЕВЕРЕН!")
                    sb.appendLine("👉 Скопируйте токен из config.json на ПК в настройки приложения.")
                    withContext(Dispatchers.Main) {
                        pcOnline = true
                        tokenError = true
                        status = "🔑 Токен не совпадает с config.json на ПК"
                    }
                } else {
                    sb.appendLine("⚠️ Неожиданный ответ сервера: HTTP $code")
                }
                conn.disconnect()
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - t0
                sb.appendLine("❌ СБОЙ ПОДКЛЮЧЕНИЯ (${elapsed} мс): ${e.localizedMessage ?: e.javaClass.simpleName}")
                if (host.contains("trycloudflare.com")) {
                    sb.appendLine("💡 Подсказка: возможно, мобильный оператор фильтрует trycloudflare.com.")
                    sb.appendLine("   Попробуйте подключить телефон к домашнему Wi-Fi и нажать 'Найти ПК в Wi-Fi'.")
                }
            }
            // Проверка обоих маршрутов и подсказки по сети
            sb.appendLine()
            sb.appendLine("=== МАРШРУТЫ ===")
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                val wifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                val vpn = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                sb.appendLine("• Сеть телефона: ${if (wifi) "Wi-Fi" else "мобильный интернет"}${if (vpn) " + включён VPN" else ""}")
                if (!wifi && connMode == "lan") sb.appendLine("💡 Выбран режим «Дома», а Wi-Fi нет — переключите на «Авто».")
                if (vpn) sb.appendLine("💡 VPN может не пускать к домашней сети и к trycloudflare.com — попробуйте выключить его.")
            } catch (_: Exception) {
            }
            for ((name, addr) in listOf("🏠 Дома" to lanHost.trim(), "🌐 Вне дома" to remoteHost.trim())) {
                if (addr.isBlank()) {
                    sb.appendLine("$name: адрес не задан")
                    continue
                }
                val t1 = System.currentTimeMillis()
                val res = try {
                    val r = http(addr, token.trim(), "/ping", "GET", timeoutMs = 4000)
                    val note = if (r.code == 200) " ✅" else if (r.code in intArrayOf(502, 530, 1033, 404)) " ❌ туннель на ПК не запущен или адрес устарел" else ""
                    "HTTP ${r.code}$note"
                } catch (e: Exception) {
                    "❌ нет связи (${e.localizedMessage ?: e.javaClass.simpleName})"
                }
                sb.appendLine("$name: $res, ${System.currentTimeMillis() - t1} мс")
            }
            withContext(Dispatchers.Main) {
                isDiagnosing = false
                diagnosticsReport = sb.toString()
            }
        }
    }

    suspend fun pingOk(h: String, ms: Int): Boolean = try {
        h.isNotBlank() && http(h.trim(), token.trim(), "/ping", "GET", timeoutMs = ms).code == 200
    } catch (_: Exception) {
        false
    }

    // Свежий интернет-адрес ПК из облачного канала (его туда кладёт сервер при запуске туннеля)
    suspend fun fetchRemoteCandidates(): List<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext emptyList()
        try {
            val hashBytes = java.security.MessageDigest.getInstance("SHA-256").digest(token.trim().toByteArray(Charsets.UTF_8))
            val channelId = "pcremote_" + hashBytes.joinToString("") { "%02x".format(it) }.take(16)
            val conn = URL("https://ntfy.sh/$channelId/raw?poll=1").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode != 200) return@withContext emptyList()
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            raw.lines().map { it.trim() }.filter { it.startsWith("https://") }.reversed().distinct().take(4)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchRemoteFromCloud(): String = fetchRemoteCandidates().firstOrNull() ?: ""

    // Выбор маршрута: сначала домашний адрес, если ПК не отвечает — интернет-адрес (Cloudflare)
    suspend fun resolveRoute() {
        if (token.isBlank()) return
        val lan = lanHost.trim()
        var rem = remoteHost.trim()
        val before = host
        if (connMode != "remote" && lan.isNotBlank() && pingOk(lan, 1000)) {
            host = lan
            routeLabel = "🏠 Дома (Wi-Fi)"
            // Дома подстраховываем «вне дома»: поднимаем туннель на ПК и запоминаем его свежий адрес
            if (connMode != "lan") {
                try {
                    val ts = getTunnelStatus(lan, token.trim())
                    if (ts.installed && !ts.running) {
                        toggleTunnel(lan, token.trim(), true)
                    } else if (ts.running && ts.url.isNotBlank() && ts.url != remoteHost) {
                        remoteHost = ts.url
                        save()
                    }
                } catch (_: Exception) {
                }
            }
        } else if (connMode != "lan") {
            if (rem.isBlank() || !pingOk(rem, 4000)) {
                // Берём самый свежий адрес из облака, но если он не отвечает — пробуем предыдущие (ntfy хранит историю)
                val cands = fetchRemoteCandidates()
                val alive = cands.firstOrNull { it != rem && pingOk(it, 3500) }
                val fresh = alive ?: cands.firstOrNull() ?: ""
                if (fresh.isNotBlank()) {
                    rem = fresh
                    remoteHost = fresh
                    save()
                }
            }
            if (rem.isNotBlank()) {
                host = rem
                routeLabel = "🌐 Через интернет"
            } else {
                routeLabel = "⚠️ Адрес вне дома не задан"
            }
        } else {
            if (lan.isNotBlank()) host = lan
            routeLabel = "⚠️ ПК не отвечает в Wi-Fi"
        }
        if (host != before) save()
    }

    fun fetchLatestTunnelUrl() {
        if (token.isBlank()) {
            status = "Сначала укажите токен в настройках"
            return
        }
        status = "Получаю интернет-адрес с ПК…"
        scope.launch(Dispatchers.IO) {
            try {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(token.trim().toByteArray(Charsets.UTF_8))
                val channelId = "pcremote_" + hashBytes.joinToString("") { "%02x".format(it) }.take(16)
                val conn = URL("https://ntfy.sh/$channelId/raw?poll=1").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val raw = conn.inputStream.bufferedReader().use { it.readText() }
                    val latestUrl = raw.lines().lastOrNull { it.isNotBlank() && (it.startsWith("https://") || it.startsWith("http://")) }?.trim() ?: ""
                    if (latestUrl.startsWith("https://") || latestUrl.startsWith("http://")) {
                        withContext(Dispatchers.Main) {
                            remoteHost = latestUrl
                            save()
                            status = "Интернет-адрес ПК обновлён: $latestUrl ✔"
                            scope.launch { resolveRoute(); refresh() }
                        }
                    } else {
                        withContext(Dispatchers.Main) { status = "ПК пока не передал интернет-адрес" }
                    }
                } else {
                    withContext(Dispatchers.Main) { status = "Сервер не найден в канале" }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { status = "Ошибка получения: ${e.localizedMessage ?: e.javaClass.simpleName}" }
            }
        }
    }

    fun discoverLocalPc() {
        status = "Поиск ПК в локальной сети Wi-Fi…"
        scope.launch(Dispatchers.IO) {
            var found = false
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 2500
                val pingData = "PC_DISCOVERY_PING".toByteArray(Charsets.UTF_8)
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(pingData, pingData.size, broadcastAddr, PORT)
                socket.send(packet)

                val buf = ByteArray(512)
                val replyPacket = DatagramPacket(buf, buf.size)
                socket.receive(replyPacket)
                val replyText = String(replyPacket.data, 0, replyPacket.length, Charsets.UTF_8).trim()
                if (replyText.startsWith("PC_DISCOVERY_PONG:")) {
                    val parts = replyText.split(":")
                    val pcName = if (parts.size >= 2) parts[1] else "ПК"
                    val tunnelUrl = if (parts.size >= 3) parts.subList(2, parts.size).joinToString(":") else ""
                    val foundHost = replyPacket.address.hostAddress ?: ""
                    val pairRes = pairWithPc(foundHost)
                    withContext(Dispatchers.Main) {
                        host = foundHost
                        lanHost = foundHost
                        if (pairRes.success && pairRes.token.isNotBlank()) {
                            token = pairRes.token
                            tokenError = false
                            if (pairRes.mac.isNotBlank()) mac = pairRes.mac
                        }
                        save()
                        val tokenNote = if (pairRes.success) " ✔ Токен получен" else " — ${pairRes.message}"
                        val tunnelNote = if (tunnelUrl.isNotBlank()) " (Туннель: $tunnelUrl)" else ""
                        status = "Найден ПК: $pcName ($foundHost)$tokenNote$tunnelNote"
                        scope.launch { refresh() }
                    }
                    found = true
                }
                socket.close()
            } catch (_: Exception) {}
            if (!found) {
                withContext(Dispatchers.Main) {
                    status = "ПК в локальной сети не найден. Убедитесь, что на ПК запущен «Пульт ПК» и телефон в той же Wi-Fi сети."
                }
            }
        }
    }

    fun openDownloadsFolder() {
        try {
            val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "vnd.android.document/directory")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                status = "Файл сохранён в системную папку Загрузки"
            }
        }
    }

    fun sendMouseAction(action: String, dy: Int = 0, nx: Float? = null, ny: Float? = null) {
        if (host.isBlank() || token.isBlank()) return
        if (action == "scroll") {
            triggerHapticTick()
        } else {
            triggerHapticClick()
        }

        val isTunnel = host.contains("trycloudflare.com") || host.startsWith("https://")
        if (!isTunnel) {
            val udpPacketStr = when (action) {
                "left", "right", "middle", "double" -> "$token:act:$action"
                "scroll" -> if (dy != 0) "$token:scroll:$dy" else null
                "abs_click" -> if (nx != null && ny != null) {
                    val nxStr = String.format(Locale.US, "%.4f", nx)
                    val nyStr = String.format(Locale.US, "%.4f", ny)
                    "$token:tap:$nxStr,$nyStr"
                } else null
                else -> null
            }

            if (udpPacketStr != null) {
                val packetData = udpPacketStr.toByteArray(Charsets.UTF_8)
                scope.launch(Dispatchers.IO) {
                    try {
                        if (cachedUdpHost != host.trim() || cachedUdpIp == null) {
                            cachedUdpIp = InetAddress.getByName(host.trim())
                            cachedUdpHost = host.trim()
                        }
                        val addr = cachedUdpIp ?: return@launch
                        val sock = udpMouseSocket ?: return@launch
                        val packet = DatagramPacket(packetData, packetData.size, addr, PORT)
                        sock.send(packet)
                        return@launch
                    } catch (_: Exception) {
                    }
                    val payload = JSONObject().apply {
                        put("action", action)
                        if (dy != 0) put("dy", dy)
                        if (nx != null) put("nx", String.format(Locale.US, "%.4f", nx).toDouble())
                        if (ny != null) put("ny", String.format(Locale.US, "%.4f", ny).toDouble())
                        if (streamAllMonitors) put("allMonitors", true)
                    }.toString()
                    try {
                        http(host.trim(), token.trim(), "/mouse", "POST", payload, timeoutMs = 800)
                    } catch (_: Exception) {}
                }
                return
            }
        }

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
            } catch (_: Exception) {}
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
            isDownloadingUpdate = false
            if (res.startsWith("Запуск установщика")) {
                otaUpdateInfo = null
            }
        }
    }

    LaunchedEffect(autoSyncMedia, host, token) {
        if (autoSyncMedia && host.isNotBlank() && token.isNotBlank()) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    scope.launch {
                        delay(1500)
                        syncPendingMedia(context, host.trim(), token.trim(), activeProfileId) { mediaSyncStatus = it }
                    }
                }
            }
            try {
                context.contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer
                )
                syncPendingMedia(context, host.trim(), token.trim(), activeProfileId) { mediaSyncStatus = it }
                awaitCancellation()
            } finally {
                try {
                    context.contentResolver.unregisterContentObserver(observer)
                } catch (_: Exception) {
                }
            }
        }
    }

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

    var showFileBrowser by remember { mutableStateOf(false) }

    fun browseFiles(path: String = fileBrowsePath) {
        if (host.isBlank() || token.isBlank()) return
        isBrowsingFiles = true
        fileBrowseStatus = "Загрузка папки…"
        scope.launch {
            try {
                val enc = java.net.URLEncoder.encode(path, "UTF-8")
                val r = http(host.trim(), token.trim(), "/files/browse?path=$enc", "GET", timeoutMs = 4000)
                if (r.code == 200) {
                    val res = parseBrowseResult(r.body)
                    fileBrowsePath = res.path
                    fileBrowseParent = res.parent
                    if (res.drives.isNotEmpty()) fileBrowseDrives = res.drives
                    fileBrowseEntries = res.entries
                    fileBrowseStatus = "Папка: ${res.path} (${res.entries.size} элементов)"
                } else {
                    fileBrowseStatus = "Ошибка: ${r.code} ${r.body}"
                }
            } catch (e: Exception) {
                fileBrowseStatus = "Сбой связи: ${e.localizedMessage ?: e.javaClass.simpleName}"
            } finally {
                isBrowsingFiles = false
            }
        }
    }

    fun downloadFileFromPc(entry: FileEntry) {
        if (host.isBlank() || token.isBlank()) return
        downloadingFiles = downloadingFiles + entry.name
        status = "Скачиваю ${entry.name}…"
        scope.launch {
            var savedUri: Uri? = null
            val res = withContext(Dispatchers.IO) {
                try {
                    val fullPath = if (fileBrowsePath.endsWith('/') || fileBrowsePath.endsWith(File.separatorChar)) fileBrowsePath + entry.name else fileBrowsePath + File.separator + entry.name
                    val enc = java.net.URLEncoder.encode(fullPath, "UTF-8")
                    val conn = URL(buildPcUrl(host, "/files/download?path=$enc")).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 120000
                    val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
                    conn.setRequestProperty("X-Token", safeToken)
                    if (conn.responseCode == 200) {
                        var savedOk = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val values = android.content.ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, entry.name)
                                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                                put(MediaStore.Downloads.IS_PENDING, 1)
                            }
                            val resolver = context.contentResolver
                            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            if (uri != null) {
                                try {
                                    resolver.openOutputStream(uri)?.use { output ->
                                        conn.inputStream.use { input -> input.copyTo(output) }
                                    }
                                    values.clear()
                                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                                    resolver.update(uri, values, null, null)
                                    savedOk = true
                                    savedUri = uri
                                } catch (_: Exception) {
                                    resolver.delete(uri, null, null)
                                }
                            }
                        } else {
                            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                            downloadDir.mkdirs()
                            val destFile = File(downloadDir, entry.name)
                            conn.inputStream.use { input ->
                                FileOutputStream(destFile).use { output -> input.copyTo(output) }
                            }
                            savedOk = true
                            savedUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
                        }
                        conn.disconnect()
                        if (savedOk) "Файл ${entry.name} сохранён в Загрузки ✔" else "Не удалось сохранить файл"
                    } else {
                        conn.disconnect()
                        "Ошибка скачивания: HTTP ${conn.responseCode}"
                    }
                } catch (e: Exception) {
                    "Ошибка скачивания: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }
            }
            downloadingFiles = downloadingFiles - entry.name
            if (savedUri != null) {
                downloadedFiles = downloadedFiles + (entry.name to savedUri!!)
            }
            status = res
        }
    }

    LaunchedEffect(connMode, lanHost, remoteHost, token, activeProfileId) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(500L)
            while (true) {
                resolveRoute()
                delay(if (pcOnline == true) 20000L else 6000L)
            }
        }
    }

    LaunchedEffect(isStreamingActive, streamQualityPreset, streamFps, streamFhdMode, streamAllMonitors, host, token, currentTab) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isStreamingActive && currentTab == NavTab.SCREEN && host.isNotBlank() && token.isNotBlank()) {
                val (w, q, targetFps) = when (streamQualityPreset) {
                    0 -> Triple(960, 52, 60)
                    1 -> Triple(1280, 62, 45)
                    else -> Triple(1920, 72, 30)
                }

                var gotFrames = false
                try {
                    runMjpegStream(host.trim(), token.trim(), targetFps, w, q, streamAllMonitors) { bmp, fpsVal, pingVal ->
                        screenBitmap = bmp
                        liveStreamFps = fpsVal
                        liveStreamPing = pingVal
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
                    delay(8L)
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
                NavTab.entries.forEach { tab ->
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
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "⚡ PC CONTROL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "v6.1",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val (dotColor, stateText) = when (pcOnline) {
                        true -> Color(0xFF10B981) to if (tokenError) "Токен неверен" else "В сети"
                        false -> Color(0xFFEF4444) to "Не в сети"
                        null -> Color(0xFF6B7280) to "Настройка"
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = dotColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
                            Text(
                                stateText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = dotColor
                            )
                        }
                    }
                }
            }

            if (status.isNotEmpty()) {
                // Долгое нажатие копирует сообщение целиком (удобно отправить текст ошибки)
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.pointerInput(status) {
                        androidx.compose.foundation.gestures.detectTapGestures(onLongPress = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("status", status))
                            android.widget.Toast.makeText(context, "Скопировано", android.widget.Toast.LENGTH_SHORT).show()
                        })
                    }
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
                            if (isStreamingActive && showStreamFpsOverlay) {
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

                            // Режимы плавности и качества видеопотока
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    Triple(0, "⚡ 60 FPS Турбо", MaterialTheme.colorScheme.primary),
                                    Triple(1, "⚖️ Баланс", MaterialTheme.colorScheme.secondary),
                                    Triple(2, "🔍 FHD 1080p", Color(0xFF10B981))
                                ).forEach { (presetIdx, labelText, accent) ->
                                    FilterChip(
                                        selected = streamQualityPreset == presetIdx,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            streamQualityPreset = presetIdx
                                            prefs.edit().putInt("stream_quality_preset", presetIdx).apply()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accent.copy(alpha = 0.25f),
                                            selectedLabelColor = accent
                                        ),
                                        label = { Text(labelText, fontWeight = if (streamQualityPreset == presetIdx) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CoolButton(if (showStreamFpsOverlay) "👁 FPS" else "👁‍🗨 Без FPS") {
                                    showStreamFpsOverlay = !showStreamFpsOverlay
                                    prefs.edit().putBoolean("show_stream_fps_overlay", showStreamFpsOverlay).apply()
                                }
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

                            StreamFrameHost(bitmapProvider = { screenBitmap }) { currentBmp ->
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
                                        filterQuality = FilterQuality.Low,
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
                                if (isStreamingActive && showStreamFpsOverlay) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .clickable {
                                                showStreamFpsOverlay = false
                                                prefs.edit().putBoolean("show_stream_fps_overlay", false).apply()
                                            }
                                    ) {
                                        StreamStatsText({ liveStreamFps }, { liveStreamPing }, 11.sp)
                                    }
                                }
                            }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CoolButton("ЛКМ") { sendMouseAction("left") }
                                CoolButton("ПКМ") { sendMouseAction("right") }
                                HoldScrollButton("⬆ Скролл") { sendMouseAction("scroll", dy = 3) }
                                HoldScrollButton("⬇ Скролл") { sendMouseAction("scroll", dy = -3) }
                                CoolButton("📸 Скриншот", accentColor = Color(0xFF10B981)) { captureScreenshotToPhone() }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CoolButton(if (isFullscreenStream) "✕ Выйти из полного экрана" else "⛶ Во весь экран", filled = true, accentColor = Color(0xFF0284C7)) {
                                    isFullscreenStream = !isFullscreenStream
                                }
                            }
                        }
                    }
                }

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

                        Slider(
                            value = mouseSensitivity,
                            onValueChange = { mouseSensitivity = it },
                            onValueChangeFinished = { save() },
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
                            HoldScrollButton("Скролл ⬆") { sendMouseAction("scroll", dy = 3) }
                            HoldScrollButton("Скролл ⬇") { sendMouseAction("scroll", dy = -3) }
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
                            CoolButton("Win+D 🖥️") { run("/media/win_d", refreshAfter = true) }
                            CoolButton("Диспетчер 📊") { run("/media/task_mgr", refreshAfter = true) }
                            CoolButton("Скриншот 📸") { captureScreenshotToPhone() }
                            CoolButton("🔒 Блок") { run("/lock", refreshAfter = false) }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoolButton("Ctrl+Z") { run("/media/ctrl_z", refreshAfter = false) }
                            CoolButton("Ctrl+C") { run("/media/ctrl_c", refreshAfter = false) }
                            CoolButton("Ctrl+V") { run("/media/ctrl_v", refreshAfter = false) }
                            CoolButton("Esc") { run("/media/key_esc", refreshAfter = false) }
                            CoolButton("Alt+F4") { run("/media/alt_f4", refreshAfter = true) }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoolButton("📋 Буфер → ПК") { sendPhoneClipboardToPc() }
                            CoolButton("📥 Буфер ← ПК") { fetchPcClipboardToPhone() }
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

            // ==================== 4. ОКНА И СОФТ ====================
            if (currentTab == NavTab.APPS) {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { isProgramsExpanded = !isProgramsExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Программы (${apps.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text(if (isProgramsExpanded) "▲" else "▼", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        AnimatedVisibility(visible = isProgramsExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (apps.isEmpty()) {
                                    Text("Список программ пуст. Добавьте из списка открытых окон ниже.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        Button(onClick = { run("/apps/${app.id}/launch") }, contentPadding = KeyPadding, shape = RoundedCornerShape(8.dp)) { Text("Пуск") }
                                        if (app.canClose) {
                                            OutlinedButton(onClick = { run("/apps/${app.id}/close") }, contentPadding = KeyPadding, shape = RoundedCornerShape(8.dp)) { Text("✕") }
                                        }
                                        OutlinedButton(onClick = { deleteApp(app.id) }, contentPadding = KeyPadding, shape = RoundedCornerShape(8.dp)) { Text("🗑") }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { isWindowsExpanded = !isWindowsExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Открытые окна (${openWindows.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { scope.launch { refresh() } }, contentPadding = KeyPadding, shape = RoundedCornerShape(10.dp)) {
                                    Text("🔄", fontSize = 12.sp)
                                }
                                Text(if (isWindowsExpanded) "▲" else "▼", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        AnimatedVisibility(visible = isWindowsExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    }
                }

            }

            // ==================== 5. ПИТАНИЕ И ЭКРАН ====================
            if (currentTab == NavTab.POWER) {
                if (showFileBrowser) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showFileBrowser = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                        Surface(modifier = Modifier.fillMaxSize().padding(8.dp), shape = RoundedCornerShape(20.dp)) {
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showFileBrowser = false }) { Text("✕ Закрыть") }
                        OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Файлы ПК 📁", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (fileBrowseParent.isNotBlank()) {
                                        OutlinedButton(onClick = { browseFiles(fileBrowseParent) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                            Text("⬆ Наверх", fontSize = 12.sp)
                                        }
                                    }
                                }
        
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    fileBrowseDrives.forEach { drive ->
                                        FilterChip(
                                            selected = fileBrowsePath.startsWith(drive, ignoreCase = true),
                                            onClick = { browseFiles("$drive\\") },
                                            label = { Text("Диск $drive") }
                                        )
                                    }
                                    OutlinedButton(onClick = { browseFiles(fileBrowsePath) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                        Text("🔄", fontSize = 13.sp)
                                    }
                                }
        
                                Text("Путь: $fileBrowsePath", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        
                                if (isBrowsingFiles) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                                } else if (fileBrowseEntries.isEmpty()) {
                                    Text("Папка пуста или нажмите 🔄 для загрузки", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                        fileBrowseEntries.forEach { entry ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(if (entry.isDir) "📁" else when {
                                                        entry.name.endsWith(".jpg", true) || entry.name.endsWith(".png", true) -> "🖼️"
                                                        entry.name.endsWith(".mp4", true) || entry.name.endsWith(".mkv", true) -> "🎬"
                                                        entry.name.endsWith(".mp3", true) || entry.name.endsWith(".flac", true) -> "🎵"
                                                        else -> "📄"
                                                    }, fontSize = 18.sp)
                                                    Column {
                                                        Text(entry.name, fontSize = 13.sp, fontWeight = if (entry.isDir) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        if (!entry.isDir) {
                                                            val sizeMb = entry.size / (1024.0 * 1024.0)
                                                            val sizeText = if (sizeMb >= 1.0) String.format(Locale.US, "%.1f МБ", sizeMb) else "${entry.size / 1024} КБ"
                                                            Text(sizeText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                                if (entry.isDir) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            val next = if (fileBrowsePath.endsWith("\\") || fileBrowsePath.endsWith("/")) "$fileBrowsePath${entry.name}" else "$fileBrowsePath\\${entry.name}"
                                                            browseFiles(next)
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Открыть ➔", fontSize = 11.sp)
                                                    }
                                                } else {
                                                    val isDownloading = entry.name in downloadingFiles
                                                    val isDownloaded = entry.name in downloadedFiles
                                                    if (isDownloading) {
                                                        Button(
                                                            onClick = {},
                                                            enabled = false,
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Text("⏳ Скачивание…", fontSize = 11.sp)
                                                        }
                                                    } else {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            if (isDownloaded) {
                                                                Button(
                                                                    onClick = { openDownloadsFolder() },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                    shape = RoundedCornerShape(8.dp)
                                                                ) {
                                                                    Text("📂 В папку", fontSize = 11.sp, color = Color.White)
                                                                }
                                                            }
                                                            OutlinedButton(
                                                                onClick = { downloadFileFromPc(entry) },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                shape = RoundedCornerShape(8.dp)
                                                            ) {
                                                                Text(if (isDownloaded) "⬇ Снова" else "⬇ Скачать", fontSize = 11.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                            }
                        }
                    }
                }
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Мониторинг ПК 📊", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val upHours = uptimeSec / 3600
                            val upMins = (uptimeSec % 3600) / 60
                            Text(
                                if (uptimeSec > 0) "Аптайм: ${upHours}ч ${upMins}м" else "ПК в сети",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Процессор", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$cpuPercent%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (cpuPercent > 80) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary)
                                    }
                                    LinearProgressIndicator(
                                        progress = { (cpuPercent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = if (cpuPercent > 80) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                            OutlinedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("ОЗУ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (ramTotalGb > 0) "${ramUsedGb}/${ramTotalGb} ГБ" else "$ramPercent%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (ramPercent > 85) Color(0xFFFF5252) else MaterialTheme.colorScheme.secondary)
                                    }
                                    LinearProgressIndicator(
                                        progress = { (ramPercent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = if (ramPercent > 85) Color(0xFFFF5252) else MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }

                        if (disks.isNotEmpty()) {
                            Text("Накопители (нажмите, чтобы открыть файлы):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                disks.forEach { disk ->
                                    val usedPct = if (disk.totalGb > 0) ((disk.totalGb - disk.freeGb) / disk.totalGb).toFloat().coerceIn(0f, 1f) else 0f
                                    OutlinedCard(onClick = { showFileBrowser = true; browseFiles(disk.drive.trimEnd('\\', '/') + "\\") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Диск ${disk.drive}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("${disk.freeGb} ГБ своб.", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                            LinearProgressIndicator(
                                                progress = { usedPct },
                                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                                color = if (usedPct > 0.9f) Color(0xFFFF5252) else Color(0xFF10B981),
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            Text("Всего: ${disk.totalGb} ГБ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

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

            }

            // ==================== 6. ОПЦИИ И НАСТРОЙКИ ====================
            if (currentTab == NavTab.SETTINGS) {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Компьютеры (Профили)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Button(onClick = { showAddProfileDialog = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                Text("+ Добавить ПК", fontSize = 12.sp)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            profiles.forEach { prof ->
                                FilterChip(
                                    selected = prof.id == activeProfileId,
                                    onClick = {
                                        activeProfileId = prof.id
                                        host = prof.host
                                        token = prof.token
                                        mac = prof.mac
                                        lanHost = prefs.getString("lan_host_${prof.id}", null) ?: prof.host.takeIf { isLanAddress(it) } ?: ""
                                        remoteHost = prefs.getString("remote_host_${prof.id}", null) ?: prof.host.takeIf { isRemoteAddress(it) } ?: ""
                                        save()
                                        status = "Выбран профиль: ${prof.name}"
                                        scope.launch { refresh() }
                                    },
                                    label = { Text("🖥️ ${prof.name}") }
                                )
                            }
                        }
                    }
                }

                SettingsSection(title = "🔌 Подключение", summary = routeLabel.ifBlank { "Не настроено" }, startExpanded = true) {
                    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("auto" to "🔄 Авто", "lan" to "🏠 Дома", "remote" to "🌐 Вне дома").forEach { (mode, title) ->
                                FilterChip(
                                    selected = connMode == mode,
                                    onClick = { connMode = mode; save() },
                                    label = { Text(title) }
                                )
                            }
                        }
                        if (routeLabel.isNotBlank()) {
                            Text("Сейчас: $routeLabel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = { discoverLocalPc() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔍 Найти ПК в Wi-Fi", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { runDiagnostics() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isDiagnosing) "⏳ Проверяю связь…" else "🩺 Диагностика связи (Проверить ПК)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        if (diagnosticsReport.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = diagnosticsReport,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if ("✅" in diagnosticsReport) Color(0xFF10B981) else if ("🔑" in diagnosticsReport) Color(0xFFF59E0B) else Color(0xFFFF5252),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        var showAdvanced by rememberSaveable { mutableStateOf(false) }
                        TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAdvanced) "Скрыть адреса и токен ▲" else "Адреса, токен, MAC ▼")
                        }
                        if (showAdvanced) {
                        OutlinedTextField(
                            value = lanHost,
                            onValueChange = { lanHost = it },
                            label = { Text("Адрес дома (IP ПК в Wi-Fi)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = remoteHost,
                            onValueChange = { remoteHost = it },
                            label = { Text("Адрес вне дома (Cloudflare)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { fetchLatestTunnelUrl() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔄 Получить интернет-адрес с ПК", fontSize = 12.sp)
                        }
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Токен (из окна «Пульт ПК» на компьютере)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    status = "Запрашиваю токен у ПК ($host)…"
                                    val res = pairWithPc(host.trim())
                                    if (res.success && res.token.isNotBlank()) {
                                        token = res.token
                                        tokenError = false
                                        if (res.mac.isNotBlank()) mac = res.mac
                                        save()
                                        status = res.message
                                        scope.launch { refresh() }
                                    } else {
                                        status = res.message
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔑 Получить токен с ПК по кнопке")
                        }
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
                }

                SettingsSection(title = "🎨 Оформление", summary = AppThemes.firstOrNull { it.first == themeId }?.second ?: "") {
                    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppThemes.forEach { (id, title, scheme) ->
                                FilterChip(
                                    selected = themeId == id,
                                    onClick = { onThemeChange(id) },
                                    leadingIcon = { Box(modifier = Modifier.size(12.dp).background(scheme.primary, CircleShape)) },
                                    label = { Text(title) }
                                )
                            }
                        }
                    }
                }

                SettingsSection(title = "🌐 Интернет-туннель", summary = if (currentTunnelUrl.isNotBlank() || isTunnelRunning) "Включён" else "Выключен") {
                    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val activeTunnelUrl = currentTunnelUrl
                        val isRunning = activeTunnelUrl.isNotBlank() || isTunnelRunning

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Статус", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (isRunning) "🟢 ВКЛЮЧЕН" else "⚪ ВЫКЛЮЧЕН",
                                color = if (isRunning) Color(0xFF10B981) else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            "Нужен для управления вне дома. В режиме «Авто» адрес подставляется сам.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (activeTunnelUrl.isNotBlank()) {
                            OutlinedCard(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = activeTunnelUrl,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clip.setPrimaryClip(ClipData.newPlainText("tunnel_url", activeTunnelUrl))
                                                status = "Ссылка туннеля скопирована в буфер 📋"
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Копировать", fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = {
                                                host = activeTunnelUrl
                                                save()
                                                status = "Адрес ПК переключен на Cloudflare туннель ✔"
                                                scope.launch { refresh() }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Использовать", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val start = !isRunning
                                    status = if (start) "Запускаю Cloudflare туннель на ПК…" else "Останавливаю Cloudflare туннель на ПК…"
                                    val res = toggleTunnel(host.trim(), token.trim(), start)
                                    isTunnelRunning = res.running
                                    currentTunnelUrl = res.url
                                    status = res.message.ifBlank { if (res.running) "Туннель запущен!" else "Туннель выключен" }
                                    scope.launch { refresh() }
                                }
                            },
                            colors = if (isRunning) ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)) else ButtonDefaults.buttonColors(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isRunning) "Остановить интернет-туннель 🛑" else "Включить интернет-туннель (4G) 🚀")
                        }
                    }
                }

                SettingsSection(title = "🔄 Обновления", summary = "Версия ${getInstalledVersionName(context)}") {
                    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                SettingsSection(title = "📸 Автозагрузка фото", summary = if (autoSyncMedia) "Включена" else "Выключена") {
                    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Новые снимки с камеры сами копируются в папку фото на ПК, пока вы в сети",
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
                                            val nowSec = System.currentTimeMillis() / 1000L
                                            prefs.edit()
                                                .putBoolean("auto_sync_media", true)
                                                .putLong("sync_start_timestamp_${activeProfileId}", nowSec)
                                                .apply()
                                            scope.launch {
                                                syncPendingMedia(context, host.trim(), token.trim(), activeProfileId) { mediaSyncStatus = it }
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
                                        val count = syncPendingMedia(context, host.trim(), token.trim(), activeProfileId) { mediaSyncStatus = it }
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

    if (showAddProfileDialog) {
        AlertDialog(
            onDismissRequest = { showAddProfileDialog = false },
            title = { Text("Новый компьютер") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Текущие параметры подключения будут сохранены как новый профиль ПК.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Имя ПК (например: Ноутбук, Дача)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newProfileName.trim().ifEmpty { "ПК ${profiles.size + 1}" }
                        val newProf = PcProfile(
                            id = System.currentTimeMillis().toString(),
                            name = name,
                            host = host.trim(),
                            token = token.trim(),
                            mac = mac.trim()
                        )
                        val updated = profiles + newProf
                        profiles = updated
                        activeProfileId = newProf.id
                        val arr = JSONArray()
                        updated.forEach { p ->
                            arr.put(JSONObject().apply {
                                put("id", p.id)
                                put("name", p.name)
                                put("host", p.host)
                                put("token", p.token)
                                put("mac", p.mac)
                            })
                        }
                        prefs.edit().putString("saved_profiles_json", arr.toString()).apply()
                        showAddProfileDialog = false
                        newProfileName = ""
                        status = "Профиль $name добавлен ✔"
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showAddProfileDialog = false }) { Text("Отмена") }
            }
        )
    }

    val window = (context as? ComponentActivity)?.window
    DisposableEffect(isFullscreenStream) {
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreenStream) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    if (isFullscreenStream) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            StreamFrameHostBox(bitmapProvider = { screenBitmap }) { currentBmp ->
            if (currentBmp != null) {
                Image(
                    bitmap = currentBmp,
                    contentDescription = "Полный экран",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = streamZoom
                            scaleY = streamZoom
                            translationX = streamPanX
                            translationY = streamPanY
                        }
                        .pointerInput(isFsMouseMode, host, token) {
                            if (isFsMouseMode) {
                                detectDragGestures { change, dragAmount ->
                                    // Как тачпад: курсор двигается от текущего места, а не прыгает в точку касания
                                    change.consume()
                                    accDx += dragAmount.x * mouseSensitivity / streamZoom
                                    accDy += dragAmount.y * mouseSensitivity / streamZoom
                                }
                            } else {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (zoom != 1f) {
                                        streamZoom = (streamZoom * zoom).coerceIn(1f, 5f)
                                    }
                                    val sw = size.width.toFloat()
                                    val sh = size.height.toFloat()
                                    val maxPanX = (sw * (streamZoom - 1f)) / 2f
                                    val maxPanY = (sh * (streamZoom - 1f)) / 2f
                                    if (maxPanX > 0f) {
                                        streamPanX = (streamPanX + pan.x).coerceIn(-maxPanX, maxPanX)
                                    } else {
                                        streamPanX = 0f
                                    }
                                    if (maxPanY > 0f) {
                                        streamPanY = (streamPanY + pan.y).coerceIn(-maxPanY, maxPanY)
                                    } else {
                                        streamPanY = 0f
                                    }
                                }
                            }
                        }
                        .pointerInput(host, token, streamZoom, streamPanX, streamPanY) {
                            detectTapGestures(
                                onTap = { offset ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (size.width > 0 && size.height > 0) {
                                        val sw = size.width.toFloat()
                                        val sh = size.height.toFloat()
                                        val localX = (offset.x - sw / 2f - streamPanX) / streamZoom + sw / 2f
                                        val localY = (offset.y - sh / 2f - streamPanY) / streamZoom + sh / 2f
                                        val (nx, ny) = fitNormalized(localX, localY, sw, sh, currentBmp.width, currentBmp.height)
                                        sendMouseAction("abs_click", nx = nx, ny = ny)
                                    } else {
                                        sendMouseAction("left")
                                    }
                                },
                                onDoubleTap = { offset ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (size.width > 0 && size.height > 0) {
                                        val sw = size.width.toFloat()
                                        val sh = size.height.toFloat()
                                        val localX = (offset.x - sw / 2f - streamPanX) / streamZoom + sw / 2f
                                        val localY = (offset.y - sh / 2f - streamPanY) / streamZoom + sh / 2f
                                        val (nx, ny) = fitNormalized(localX, localY, sw, sh, currentBmp.width, currentBmp.height)
                                        sendMouseAction("abs_double", nx = nx, ny = ny)
                                    } else {
                                        sendMouseAction("double")
                                    }
                                },
                                onLongPress = { offset ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (size.width > 0 && size.height > 0) {
                                        val sw = size.width.toFloat()
                                        val sh = size.height.toFloat()
                                        val localX = (offset.x - sw / 2f - streamPanX) / streamZoom + sw / 2f
                                        val localY = (offset.y - sh / 2f - streamPanY) / streamZoom + sh / 2f
                                        val (nx, ny) = fitNormalized(localX, localY, sw, sh, currentBmp.width, currentBmp.height)
                                        sendMouseAction("abs_right", nx = nx, ny = ny)
                                    } else {
                                        sendMouseAction("right")
                                    }
                                }
                            )
                        }
                )
            } else {
                Text("Ожидание видеопотока…", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
            }

            if (showStreamFpsOverlay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable {
                            showStreamFpsOverlay = false
                            prefs.edit().putBoolean("show_stream_fps_overlay", false).apply()
                        }
                ) {
                    StreamStatsText({ liveStreamFps }, { liveStreamPing }, 12.sp)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isFsControlsVisible) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { isFsMouseMode = !isFsMouseMode },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = if (isFsMouseMode) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)) else ButtonDefaults.outlinedButtonColors()
                                ) {
                                    Text(if (isFsMouseMode) "🎯 Мышь" else "✋ Обзор", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isFsMouseMode) MaterialTheme.colorScheme.primary else Color.White)
                                }
                                OutlinedButton(onClick = { sendMouseAction("left") }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), shape = RoundedCornerShape(10.dp)) { Text("ЛКМ", fontSize = 11.sp) }
                                OutlinedButton(onClick = { sendMouseAction("right") }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), shape = RoundedCornerShape(10.dp)) { Text("ПКМ", fontSize = 11.sp) }
                                HoldScrollButton("⬆") { sendMouseAction("scroll", dy = 3) }
                                HoldScrollButton("⬇") { sendMouseAction("scroll", dy = -3) }
                                OutlinedButton(onClick = {
                                    streamZoom = 1f
                                    streamPanX = 0f
                                    streamPanY = 0f
                                }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), shape = RoundedCornerShape(10.dp)) { Text("1×", fontSize = 11.sp) }
                                OutlinedButton(onClick = {
                                    showStreamFpsOverlay = !showStreamFpsOverlay
                                    prefs.edit().putBoolean("show_stream_fps_overlay", showStreamFpsOverlay).apply()
                                }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), shape = RoundedCornerShape(10.dp)) {
                                    Text(if (showStreamFpsOverlay) "👁" else "👁‍🗨", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { isFullscreenStream = false },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                                ) { Text("✕", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { isFsControlsVisible = !isFsControlsVisible }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (isFsControlsVisible) "▼" else "▲", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    pending?.let { (path, title) ->
        val timerAction = when (path) {
            "/shutdown" -> "shutdown"
            "/sleep" -> "sleep"
            else -> null
        }
        var timerMinutesInput by remember(path) { mutableStateOf("30") }
        val minutes = (timerMinutesInput.toIntOrNull() ?: 0).coerceIn(0, 1440)
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (timerAction == null) {
                        Text("Команда будет выполнена на ПК немедленно.")
                    } else {
                        Button(
                            onClick = { pending = null; run(path) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Сейчас", fontWeight = FontWeight.Bold) }
                        Text("или через:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                                FilterChip(
                                    selected = minutes == m,
                                    onClick = { timerMinutesInput = m.toString() },
                                    label = { Text("$m мин") }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = timerMinutesInput,
                            onValueChange = { timerMinutesInput = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("Свои минуты") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (timerAction == null) {
                    TextButton(onClick = { pending = null; run(path) }) { Text("Да") }
                } else {
                    TextButton(
                        enabled = minutes > 0,
                        onClick = { pending = null; startCustomTimer(minutes, timerAction) }
                    ) { Text("⏱ Через $minutes мин") }
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Отмена") }
            }
        )
    }
}
