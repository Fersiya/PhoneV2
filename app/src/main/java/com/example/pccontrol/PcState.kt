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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.ui.hapticfeedback.HapticFeedback
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class PcState(
    val context: Context,
    val haptic: HapticFeedback,
    val scope: CoroutineScope
) {
    val activity = context as ComponentActivity
    var themeId by mutableStateOf("cyber")
    var onThemeChange: (String) -> Unit = {}
    lateinit var mediaPermLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>

    val prefs = run { context.getSharedPreferences("pc_control", Context.MODE_PRIVATE) }
    val vibrator = run {
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

    val initialProfiles = run {
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
    var profiles by run { mutableStateOf(initialProfiles) }
    val initialActiveId = run {
        val savedId = prefs.getString("active_profile_id", "") ?: ""
        if (initialProfiles.any { it.id == savedId }) savedId else (initialProfiles.firstOrNull()?.id ?: "1")
    }
    var activeProfileId by run { mutableStateOf(initialActiveId) }
    val initialActiveProfile = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()

    var host by run { mutableStateOf(initialActiveProfile?.host ?: prefs.getString("host", "") ?: "") }
    var token by run { mutableStateOf(initialActiveProfile?.token ?: prefs.getString("token", "") ?: "") }
    // Подключение: дома (IP в Wi-Fi) и вне дома (Tailscale); host — то, что выбрано сейчас
    var lanHost by run {
        mutableStateOf(prefs.getString("lan_host_$initialActiveId", null) ?: (initialActiveProfile?.host ?: "").takeIf { isLanAddress(it) } ?: "")
    }
    // Необязательный третий путь: адрес ПК в Tailscale (100.x.x.x). Приложение Tailscale просто работает у телефона в фоне.
    var tsHost by run { mutableStateOf(prefs.getString("ts_host_$initialActiveId", "") ?: "") }
    var connMode by run { mutableStateOf(prefs.getString("conn_mode", "auto") ?: "auto") }
    var routeLabel by run { mutableStateOf("") }
    var mac by run { mutableStateOf(initialActiveProfile?.mac ?: prefs.getString("mac", "") ?: "") }
    val installedApkHash by run { mutableStateOf(prefs.getString("installed_apk_hash", "") ?: "") }
    var mouseSensitivity by run { mutableFloatStateOf(prefs.getFloat("mouse_sens", 1.6f)) }

    // Автосинхронизация фото и видео на ПК
    var autoSyncMedia by run { mutableStateOf(prefs.getBoolean("auto_sync_media", false)) }
    var mediaSyncStatus by run { mutableStateOf("") }

    // Текущая вкладка навигации
    var currentTab by run { mutableStateOf(if (host.isBlank() || token.isBlank()) NavTab.SETTINGS else NavTab.SCREEN) }

    // Live HUD трансляции
    var liveStreamFps by run { mutableIntStateOf(0) }
    var liveStreamPing by run { mutableLongStateOf(0L) }

    // Мониторинг ПК
    var cpuPercent by run { mutableIntStateOf(0) }
    var uptimeSec by run { mutableIntStateOf(0) }
    var ramTotalGb by run { mutableStateOf(0.0) }
    var ramUsedGb by run { mutableStateOf(0.0) }
    var disks by run { mutableStateOf<List<DiskItem>>(emptyList()) }

    // Состояние сворачивания карточек в Окна/Софт
    var isProgramsExpanded by run { mutableStateOf(true) }
    var isWindowsExpanded by run { mutableStateOf(false) }

    // Настройки трансляции и полноэкранный режим
    var showStreamFpsOverlay by run { mutableStateOf(prefs.getBoolean("show_stream_fps_overlay", true)) }
    var isFullscreenStream by run { mutableStateOf(false) }
    var isFsControlsVisible by run { mutableStateOf(prefs.getBoolean("fs_controls_visible", false)) }
    var isFsMouseMode by run { mutableStateOf(false) }

    // Проводник файлов ПК
    var fileBrowsePath by run { mutableStateOf("D:\\") }
    var fileBrowseParent by run { mutableStateOf("") }
    var fileBrowseDrives by run { mutableStateOf<List<String>>(listOf("C:", "D:")) }
    var fileBrowseEntries by run { mutableStateOf<List<FileEntry>>(emptyList()) }
    var fileBrowseStatus by run { mutableStateOf("") }
    var isBrowsingFiles by run { mutableStateOf(false) }

    // Управление профилями ПК
    var showAddProfileDialog by run { mutableStateOf(false) }
    var newProfileName by run { mutableStateOf("") }

    // Трансляция экрана
    var isStreamingActive by run { mutableStateOf(false) }
    // Один режим трансляции: 960 px, q52, цель 120 к/с (Хост сам ограничит частотой монитора)
    var streamAllMonitors by run { mutableStateOf(false) }
    var directTapMode by run { mutableStateOf(prefs.getBoolean("direct_tap_mode", true)) }
    var audioOn by run { mutableStateOf(prefs.getBoolean("audio_on", false)) }
    var audioState by run { mutableStateOf("") }
    var streamZoom by run { mutableFloatStateOf(1f) }
    // Центр приближения (доли экрана ПК) и область, которую реально содержит последний кадр
    var streamCx by run { mutableFloatStateOf(0.5f) }
    var streamCy by run { mutableFloatStateOf(0.5f) }
    var frameView by run { mutableStateOf(floatArrayOf(0f, 0f, 1f, 1f)) }
    fun tapToScreen(u: Float, v: Float): Pair<Float, Float> {
        val vp = streamViewport(streamZoom, streamCx, streamCy)
        return Pair(vp[0] + u.coerceIn(0f, 1f) * (vp[2] - vp[0]), vp[1] + v.coerceIn(0f, 1f) * (vp[3] - vp[1]))
    }

    // Отслеживание скачивания файлов для кнопки "Открыть"
    var downloadingFiles by run { mutableStateOf<Set<String>>(emptySet()) }
    var downloadedFiles by run { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var screenBitmap by run { mutableStateOf<ImageBitmap?>(null) }

    // Окна, софт, звук
    var openWindows by run { mutableStateOf<List<WindowItem>>(emptyList()) }
    var volumeSlider by run { mutableFloatStateOf(40f) }
    var isAdjustingVolume by run { mutableStateOf(false) }
    var videoUrl by run { mutableStateOf("") }
    var textToType by run { mutableStateOf("") }
    var status by run { mutableStateOf("") }
    var pending by run { mutableStateOf<Pair<String, String>?>(null) }
    var apps by run { mutableStateOf<List<AppItem>>(emptyList()) }
    var activeWindow by run { mutableStateOf("") }
    var ramPercent by run { mutableIntStateOf(0) }
    var shutdownRemainingSec by run { mutableIntStateOf(0) }
    var shutdownActive by run { mutableStateOf(false) }
    var pcOnline by run { mutableStateOf<Boolean?>(null) }
    var tokenError by run { mutableStateOf(false) }

    // OTA обновление
    var otaUpdateInfo by run { mutableStateOf<UpdateInfo?>(null) }
    var isDownloadingUpdate by run { mutableStateOf(false) }
    var downloadProgress by run { mutableFloatStateOf(0f) }

    var accDx by run { mutableFloatStateOf(0f) }
    var accDy by run { mutableFloatStateOf(0f) }

    fun save() {
        prefs.edit()
            .putString("host", host.trim())
            .putString("token", token.trim())
            .putString("mac", mac.trim())
            .putString("lan_host_$activeProfileId", lanHost.trim())
            .putString("ts_host_$activeProfileId", tsHost.trim())
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

    var diagnosticsReport by run { mutableStateOf("") }
    var isDiagnosing by run { mutableStateOf(false) }

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
            for ((name, addr) in listOf("🏠 Дома" to lanHost.trim(), "🔒 Tailscale" to tsHost.trim())) {
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


    // Выбор маршрута: сначала домашний адрес, если ПК не отвечает — интернет-адрес (Cloudflare)
    suspend fun resolveRoute() {
        if (token.isBlank()) return
        val lan = lanHost.trim()
        val before = host
        if (lan.isNotBlank() && pingOk(lan, 1000)) {
            host = lan
            routeLabel = "🏠 Дома (Wi-Fi)"
            // Дома запоминаем Tailscale-адрес ПК — он понадобится вне дома
            try {
                val ts = getTunnelStatus(lan, token.trim())
                if (ts.tailscale.isNotBlank() && ts.tailscale != tsHost) {
                    tsHost = ts.tailscale
                    save()
                }
            } catch (_: Exception) {
            }
        } else if (connMode != "lan" && tsHost.isNotBlank() && pingOk(tsHost.trim(), 3000)) {
            host = tsHost.trim()
            routeLabel = "🔒 Через Tailscale"
        } else if (connMode != "lan") {
            if (tsHost.isNotBlank()) host = tsHost.trim()
            routeLabel = if (tsHost.isBlank()) "⚠️ Вне дома нужен Tailscale: откройте приложение дома, а на ПК и телефоне включите Tailscale"
            else "⚠️ ПК не отвечает по Tailscale — он должен быть включён на телефоне и на ПК"
        } else {
            if (lan.isNotBlank()) host = lan
            routeLabel = "⚠️ ПК не отвечает в Wi-Fi"
        }
        if (host != before) save()
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
                    val foundHost = replyPacket.address.hostAddress ?: ""
                    val pairRes = pairWithPc(foundHost)
                    withContext(Dispatchers.Main) {
                        host = foundHost
                        lanHost = foundHost
                        if (pairRes.success && pairRes.token.isNotBlank()) {
                            token = pairRes.token
                            tokenError = false
                            if (pairRes.mac.isNotBlank()) mac = pairRes.mac
                            if (pairRes.tailscale.isNotBlank()) tsHost = pairRes.tailscale
                        }
                        save()
                        val tokenNote = if (pairRes.success) " ✔ Токен получен" else " — ${pairRes.message}"
                        status = "Найден ПК: $pcName ($foundHost)$tokenNote"
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
        val exeName = win.process.removeSuffix(".exe").removeSuffix(".EXE")
        val lastPart = win.title.split(" - ", " — ").last().trim()
        val cleanTitle = when {
            lastPart.isNotEmpty() && lastPart.length <= 24 -> lastPart
            exeName.isNotEmpty() -> exeName.replaceFirstChar { it.titlecase() }
            else -> win.title.take(24)
        }
        val payload = JSONObject().apply {
            put("title", cleanTitle)
            put("launch", win.path.ifEmpty { win.process })
            put("process", win.process)
        }.toString()
        scope.launch {
            status = try {
                val r = http(host.trim(), token.trim(), "/apps/add", "POST", payload)
                when (r.code) {
                    200 -> "«$cleanTitle» добавлено в Софт ✔"
                    409 -> "«$cleanTitle» уже есть в Софте"
                    401 -> "Неверный токен"
                    else -> "Не удалось добавить: ${r.body.ifBlank { "ошибка ${r.code}" }}"
                }
            } catch (e: Exception) {
                "ПК недоступен (${e.javaClass.simpleName})"
            }
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

    var showFileBrowser by run { mutableStateOf(false) }

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

}
