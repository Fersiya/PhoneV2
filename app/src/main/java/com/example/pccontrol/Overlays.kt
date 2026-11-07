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
fun AddProfileDialog(ui: PcState) {
    with(ui) {
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

    }
}

@Composable
fun FullscreenStream(ui: PcState) {
    with(ui) {
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

    androidx.compose.animation.AnimatedVisibility(
        visible = isFullscreenStream,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)) +
            androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(300), initialScale = 0.92f),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) +
            androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(200), targetScale = 0.95f)
    ) {
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
                        .pointerInput(isFsMouseMode, host, token) {
                            if (isFsMouseMode) {
                                detectDragGestures { change, dragAmount ->
                                    // Как тачпад: курсор двигается от текущего места, а не прыгает в точку касания
                                    change.consume()
                                    accDx += dragAmount.x * mouseSensitivity / streamZoom
                                    accDy += dragAmount.y * mouseSensitivity / streamZoom
                                }
                            } else {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    val sw = size.width.toFloat()
                                    val sh = size.height.toFloat()
                                    val bw = currentBmp.width
                                    val bh = currentBmp.height
                                    val sc = minOf(sw / bw, sh / bh)
                                    val dw = bw * sc
                                    val dh = bh * sc
                                    val uC = ((centroid.x - (sw - dw) / 2f) / dw).coerceIn(0f, 1f)
                                    val vC = ((centroid.y - (sh - dh) / 2f) / dh).coerceIn(0f, 1f)
                                    val old = streamViewport(streamZoom, streamCx, streamCy)
                                    val ow = old[2] - old[0]
                                    val px = old[0] + uC * ow
                                    val py = old[1] + vC * ow
                                    val nz = (streamZoom * zoom).coerceIn(1f, 6f)
                                    val nw = 1f / nz
                                    val nx0 = (px - uC * nw - pan.x / dw * nw).coerceIn(0f, 1f - nw)
                                    val ny0 = (py - vC * nw - pan.y / dh * nw).coerceIn(0f, 1f - nw)
                                    streamZoom = nz
                                    streamCx = nx0 + nw / 2f
                                    streamCy = ny0 + nw / 2f
                                }
                            }
                        }
                        .pointerInput(host, token) {
                            detectTapGestures(
                                onTap = { offset ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (size.width > 0 && size.height > 0) {
                                        val (fu, fv) = fitNormalized(offset.x, offset.y, size.width.toFloat(), size.height.toFloat(), currentBmp.width, currentBmp.height)
                                        val (nx, ny) = tapToScreen(fu, fv)
                                        sendMouseAction("abs_click", nx = nx, ny = ny)
                                    } else {
                                        sendMouseAction("left")
                                    }
                                },
                                onDoubleTap = { offset ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (size.width > 0 && size.height > 0) {
                                        val (fu, fv) = fitNormalized(offset.x, offset.y, size.width.toFloat(), size.height.toFloat(), currentBmp.width, currentBmp.height)
                                        val (nx, ny) = tapToScreen(fu, fv)
                                        sendMouseAction("abs_double", nx = nx, ny = ny)
                                    } else {
                                        sendMouseAction("double")
                                    }
                                },
                                onLongPress = { offset ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (size.width > 0 && size.height > 0) {
                                        val (fu, fv) = fitNormalized(offset.x, offset.y, size.width.toFloat(), size.height.toFloat(), currentBmp.width, currentBmp.height)
                                        val (nx, ny) = tapToScreen(fu, fv)
                                        sendMouseAction("abs_right", nx = nx, ny = ny)
                                    } else {
                                        sendMouseAction("right")
                                    }
                                }
                            )
                        }
                        .graphicsLayer {
                            val lay = streamViewLayer(streamViewport(streamZoom, streamCx, streamCy), frameView)
                            val sc = minOf(size.width / currentBmp.width, size.height / currentBmp.height)
                            scaleX = lay[0]
                            scaleY = lay[1]
                            translationX = lay[2] * currentBmp.width * sc
                            translationY = lay[3] * currentBmp.height * sc
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
                    .imePadding()
                    .padding(bottom = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AnimatedVisibility(
                        visible = isFsControlsVisible,
                        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = textToType,
                                        onValueChange = { textToType = it },
                                        placeholder = { Text("Печать на ПК", fontSize = 12.sp) },
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                    )
                                    OutlinedButton(
                                        onClick = { sendTypeText(enter = true) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("↵", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                                }
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        "Esc" to "/media/key_esc", "Win+D" to "/media/win_d", "Ctrl+C" to "/media/ctrl_c",
                                        "Ctrl+V" to "/media/ctrl_v", "Ctrl+Z" to "/media/ctrl_z", "Alt+F4" to "/media/alt_f4",
                                        "Диспетчер" to "/media/task_mgr"
                                    ).forEach { (label, path) ->
                                        OutlinedButton(
                                            onClick = { run(path, refreshAfter = false) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) { Text(label, fontSize = 11.sp) }
                                    }
                                    OutlinedButton(
                                        onClick = { sendPhoneClipboardToPc() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("📋→ПК", fontSize = 11.sp) }
                                    OutlinedButton(
                                        onClick = { captureScreenshotToPhone() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("📸", fontSize = 11.sp) }
                                }
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
                                    streamCx = 0.5f
                                    streamCy = 0.5f
                                }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), shape = RoundedCornerShape(10.dp)) { Text("1×", fontSize = 11.sp) }
                                OutlinedButton(onClick = {
                                    audioOn = !audioOn
                                    prefs.edit().putBoolean("audio_on", audioOn).apply()
                                }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), shape = RoundedCornerShape(10.dp)) {
                                    Text(if (audioOn) "🔊" else "🔇", fontSize = 11.sp)
                                }
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
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .size(width = 64.dp, height = 24.dp)
                            .clickable {
                                isFsControlsVisible = !isFsControlsVisible
                                prefs.edit().putBoolean("fs_controls_visible", isFsControlsVisible).apply()
                            }
                            .pointerInput(Unit) {
                                var drag = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { drag = 0f },
                                    onVerticalDrag = { _, dy -> drag += dy },
                                    onDragEnd = {
                                        val want = if (drag < -30f) true else if (drag > 30f) false else isFsControlsVisible
                                        if (want != isFsControlsVisible) {
                                            isFsControlsVisible = want
                                            prefs.edit().putBoolean("fs_controls_visible", want).apply()
                                        }
                                    }
                                )
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (isFsControlsVisible) "⌄" else "⌃", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    }
}

@Composable
fun PendingDialog(ui: PcState) {
    with(ui) {
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
}
