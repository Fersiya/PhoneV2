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
fun ColumnScope.SettingsTab(ui: PcState) {
    with(ui) {
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().appear(0)) {
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
                                        tsHost = prefs.getString("ts_host_${prof.id}", "") ?: ""
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
                            listOf("auto" to "🔄 Авто", "lan" to "🏠 Дома", "remote" to "🔒 Вне дома").forEach { (mode, title) ->
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
                        OutlinedButton(
                            onClick = {
                                val info = "режим=$connMode\nhost=$host\nlan=$lanHost\nts=$tsHost\nmac=${if (mac.isBlank()) "нет" else mac}\nтокен=${if (token.isBlank()) "нет" else "задан"}\nмаршрут=$routeLabel\nПК онлайн=$pcOnline"
                                try { AppLog.share(context, info, diagnosticsReport) } catch (e: Exception) { status = "Не удалось собрать отчёт: ${e.message}" }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📄 Собрать отчёт и отправить", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                            value = tsHost,
                            onValueChange = { tsHost = it },
                            label = { Text("Tailscale (необязательно, 100.x.x.x)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
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
                                        if (res.tailscale.isNotBlank()) tsHost = res.tailscale
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
