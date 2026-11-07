package com.example.pccontrol

import androidx.compose.foundation.layout.ColumnScope
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
fun ColumnScope.ScreenTab(ui: PcState) {
    with(ui) {
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
                                    PulseDot(Color(0xFF10B981))
                                    Text("Прямой эфир", color = Color(0xFF10B981), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        AnimatedVisibility(visible = isStreamingActive && streamNote.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(streamNote, modifier = Modifier.padding(8.dp), fontSize = 12.sp, color = Color(0xFFF59E0B))
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
                                "Трансляция выключена (не расходует сеть и заряд батареи). Включённая запускается сама при открытии приложения.",
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

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CoolButton(if (showStreamFpsOverlay) "👁 FPS" else "👁‍🗨 Без FPS") {
                                    showStreamFpsOverlay = !showStreamFpsOverlay
                                    prefs.edit().putBoolean("show_stream_fps_overlay", showStreamFpsOverlay).apply()
                                }
                                CoolButton("🔍 ${streamZoom.toInt()}×") {
                                    streamZoom = when {
                                        streamZoom < 1.5f -> 2f
                                        streamZoom < 2.5f -> 3f
                                        else -> 1f
                                    }
                                    streamCx = 0.5f
                                    streamCy = 0.5f
                                }
                                CoolButton(if (audioOn) "🔊 Звук" else "🔇 Звук") {
                                    audioOn = !audioOn
                                    prefs.edit().putBoolean("audio_on", audioOn).apply()
                                }
                                CoolButton(if (streamAllMonitors) "Все экр." else "1 экр.") { streamAllMonitors = !streamAllMonitors }
                                CoolButton(if (directTapMode) "🎯 В точку" else "🖱 Свайп") {
                                    directTapMode = !directTapMode
                                    prefs.edit().putBoolean("direct_tap_mode", directTapMode).apply()
                                }
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
                                            .pointerInput(host, token, directTapMode, streamAllMonitors) {
                                                detectTapGestures(
                                                    onTap = { offset ->
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        if (directTapMode && size.width > 0 && size.height > 0) {
                                                            val (nx, ny) = tapToScreen(offset.x / size.width.toFloat(), offset.y / size.height.toFloat())
                                                            sendMouseAction("abs_click", nx = nx, ny = ny)
                                                        } else {
                                                            sendMouseAction("left")
                                                        }
                                                    },
                                                    onDoubleTap = { offset ->
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (directTapMode && size.width > 0 && size.height > 0) {
                                                            val (nx, ny) = tapToScreen(offset.x / size.width.toFloat(), offset.y / size.height.toFloat())
                                                            sendMouseAction("abs_double", nx = nx, ny = ny)
                                                        } else {
                                                            sendMouseAction("double")
                                                        }
                                                    },
                                                    onLongPress = { offset ->
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (directTapMode && size.width > 0 && size.height > 0) {
                                                            val (nx, ny) = tapToScreen(offset.x / size.width.toFloat(), offset.y / size.height.toFloat())
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
                                            .graphicsLayer {
                                                val lay = streamViewLayer(streamViewport(streamZoom, streamCx, streamCy), frameView)
                                                scaleX = lay[0]
                                                scaleY = lay[1]
                                                translationX = lay[2] * size.width
                                                translationY = lay[3] * size.height
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
}
