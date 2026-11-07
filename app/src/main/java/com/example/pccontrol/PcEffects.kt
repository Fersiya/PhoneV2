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

@Composable
fun PcEffects(ui: PcState, incomingSharedUrl: String?, onSharedUrlConsumed: () -> Unit) {
    with(ui) {
    DisposableEffect(Unit) {
        onDispose {
            try { udpMouseSocket?.close() } catch (_: Exception) {}
        }
    }


    LaunchedEffect(Unit) { AppLog.init(context); AppLog.add("— приложение открыто —") }
    LaunchedEffect(status) { if (status.isNotBlank()) AppLog.add("статус: $status") }
    LaunchedEffect(routeLabel) { if (routeLabel.isNotBlank()) AppLog.add("маршрут: $routeLabel | host=$host lan=$lanHost ts=$tsHost режим=$connMode") }
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

    LaunchedEffect(connMode, lanHost, tsHost, token, activeProfileId) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(500L)
            while (true) {
                resolveRoute()
                delay(if (pcOnline == true) 20000L else 6000L)
            }
        }
    }

    // Приближение: сообщаем Хосту, какую часть экрана слать в полном качестве
    // Трансляция стартует сама при открытии приложения (остановить можно кнопкой)
    LaunchedEffect(Unit) {
        if (host.isNotBlank() && token.isNotBlank() && prefs.getBoolean("stream_autostart", true)) isStreamingActive = true
    }
    LaunchedEffect(isStreamingActive) {
        if (currentTab == NavTab.SCREEN) prefs.edit().putBoolean("stream_autostart", isStreamingActive).apply()
    }

    LaunchedEffect(isStreamingActive, host, token) {
        var sent: FloatArray? = null
        while (isStreamingActive) {
            delay(70L)
            val vp = streamViewport(streamZoom, streamCx, streamCy)
            if (sent == null || !sent.contentEquals(vp)) {
                try {
                    http(host.trim(), token.trim(), "/api/view?x0=${vp[0]}&y0=${vp[1]}&x1=${vp[2]}&y1=${vp[3]}", "GET", timeoutMs = 1500)
                    sent = vp
                } catch (_: Exception) {
                }
            }
        }
    }

    // Звук ПК (только пока идёт трансляция и приложение на экране)
    LaunchedEffect(isStreamingActive, audioOn, host, token, currentTab) {
        if (!isStreamingActive || !audioOn || currentTab != NavTab.SCREEN) { audioState = ""; return@LaunchedEffect }
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var fails = 0
            while (isStreamingActive && audioOn && currentTab == NavTab.SCREEN && host.isNotBlank() && token.isNotBlank()) {
                val err = runAudioStream(host.trim(), token.trim(), !isLanAddress(host.trim())) { audioState = it }
                if (err.isNotEmpty()) {
                    audioState = err
                    fails++
                    if (fails >= 3) { audioOn = false; prefs.edit().putBoolean("audio_on", false).apply(); status = err; break }
                } else fails = 0
                delay(1000L)
            }
        }
    }

    LaunchedEffect(isStreamingActive, streamAllMonitors, host, token, currentTab) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isStreamingActive && currentTab == NavTab.SCREEN && host.isNotBlank() && token.isNotBlank()) {
                // дома — максимум качества; через интернет (Tailscale) канал уже, поэтому кадр легче
                val (w, q, targetFps) = if (isLanAddress(host.trim())) Triple(960, 54, 120) else Triple(720, 42, 60)

                var gotFrames = false
                try {
                    runMjpegStream(host.trim(), token.trim(), targetFps, w, q, streamAllMonitors,
                        streamViewport(streamZoom, streamCx, streamCy)) { bmp, fpsVal, pingVal, fv ->
                        screenBitmap = bmp
                        frameView = fv
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

    }
}
