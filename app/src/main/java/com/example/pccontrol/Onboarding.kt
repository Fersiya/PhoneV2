package com.example.pccontrol

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SERVER_RELEASES = "https://github.com/Fersiya/PhoneV2-server/releases/latest"
private const val TAILSCALE_URL = "https://tailscale.com/download"

/** Мастер первого запуска: показывается, пока нет адреса/токена и пользователь его не закрыл. */
@Composable
fun OnboardingDialog(ui: PcState) {
    with(ui) {
        // Решаем один раз при запуске: мастер нужен только тем, у кого ещё нет адреса и токена
        val needed = remember { !prefs.getBoolean("onboarding_done", false) && (host.isBlank() || token.isBlank()) }
        var done by remember { mutableStateOf(false) }
        if (!needed || done) return@with
        var step by remember { mutableIntStateOf(0) }
        val connected = host.isNotBlank() && token.isNotBlank()

        fun open(url: String) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { status = "Не удалось открыть браузер" }
        }
        fun finish() {
            prefs.edit().putBoolean("onboarding_done", true).apply()
            done = true
            currentTab = NavTab.SCREEN
        }

        AlertDialog(
            onDismissRequest = {},
            title = { Text(listOf("⚡ Добро пожаловать", "📡 Подключение", "🌐 Вне дома")[step], fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (step) {
                        0 -> {
                            Text("«Пульт ПК» управляет Windows-компьютером с телефона: экран и звук, мышь, программы, питание, файлы.")
                            Text("Сначала на самом ПК нужно поставить программу-хост (бесплатно):", fontSize = 13.sp)
                            Button(onClick = { open(SERVER_RELEASES) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("⬇ Скачать хост для ПК")
                            }
                            Text("Откройте эту страницу на компьютере, скачайте PCControl-Setup.exe и запустите. Окно «Пульт ПК» оставьте открытым.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        1 -> {
                            Text("Телефон и ПК должны быть в одной Wi-Fi сети. Нажмите кнопку — приложение найдёт ПК и само получит токен.")
                            Button(onClick = { discoverLocalPc() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("🔍 Найти ПК в Wi-Fi")
                            }
                            if (status.isNotBlank()) Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            if (connected) Text("✔ ПК найден и подключён", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Не находит? Проверьте, что хост запущен, а брандмауэр Windows разрешил «Пульт ПК».", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            Text("Чтобы управлять ПК из другой сети (мобильный интернет, чужой Wi-Fi), поставьте бесплатный Tailscale на ПК и телефон и войдите в один аккаунт.")
                            OutlinedButton(onClick = { open(TAILSCALE_URL) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("Открыть Tailscale")
                            }
                            Text("Это можно сделать позже. Приложение само выберет маршрут: дома — Wi-Fi, вне дома — Tailscale.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                if (step < 2) {
                    TextButton(onClick = { step++ }, enabled = step != 1 || connected) { Text("Далее") }
                } else {
                    TextButton(onClick = { finish() }) { Text("Готово") }
                }
            },
            dismissButton = {
                Row {
                    if (step > 0) TextButton(onClick = { step-- }) { Text("Назад") }
                    TextButton(onClick = { finish() }) { Text("Пропустить") }
                }
            }
        )
    }
}
