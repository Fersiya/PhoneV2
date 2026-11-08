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
fun ColumnScope.PowerTab(ui: PcState) {
    with(ui) {
                if (showFileBrowser) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showFileBrowser = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                        Surface(modifier = Modifier.fillMaxSize().padding(8.dp), shape = RoundedCornerShape(20.dp)) {
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showFileBrowser = false }) { Text("✕ Закрыть") }
                        OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().appear(0)) {
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
                                                        val (got, total) = fileDownloadProgress[entry.name] ?: (0L to 0L)
                                                        val frac = if (total > 0) (got.toFloat() / total).coerceIn(0f, 1f) else 0f
                                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(112.dp)) {
                                                            Text(
                                                                if (total > 0) "${(frac * 100).toInt()}% · ${formatMb(got)}/${formatMb(total)}" else "⏳ ${formatMb(got)}",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            if (total > 0) {
                                                                LinearProgressIndicator(
                                                                    progress = { frac },
                                                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                                                )
                                                            } else {
                                                                LinearProgressIndicator(
                                                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                                                )
                                                            }
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
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().appear(70)) {
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
                                            Text("Диск ${disk.drive}", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                                            Text("${disk.freeGb} ГБ свободно", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
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

                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().appear(140)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Управление питанием", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionTile("⚡", "Включить\n(Wake-on-LAN)", Color(0xFF10B981)) { wake() }
                            ActionTile("💤", "Сон", Color(0xFF7C3AED)) { pending = "/sleep" to "Перевести ПК в режим сна" }
                            ActionTile("🌙", "Экран\nвыкл.", Color(0xFF0EA5E9)) { run("/monitor_off", refreshAfter = false) }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionTile("🔒", "Блокировка", Color(0xFFF59E0B)) { run("/lock", refreshAfter = false) }
                            ActionTile("🔄", "Рестарт", Color(0xFF6366F1)) { pending = "/restart" to "Перезагрузить ПК" }
                            ActionTile("⛔", "Выключить", Color(0xFFDC2626)) { pending = "/shutdown" to "Выключить ПК" }
                        }
                    }
                }

    }
}
