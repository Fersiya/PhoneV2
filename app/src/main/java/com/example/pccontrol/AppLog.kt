package com.example.pccontrol

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Небольшой журнал событий приложения (маршрут, ошибки). Хранится в кэше, ~200 КБ максимум. */
object AppLog {
    private const val MAX_BYTES = 200_000L
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private var file: File? = null

    fun init(context: Context) {
        if (file == null) file = File(context.cacheDir, "pccontrol-log.txt")
    }

    @Synchronized
    fun add(msg: String) {
        val f = file ?: return
        try {
            if (f.exists() && f.length() > MAX_BYTES) {
                val keep = f.readText().takeLast((MAX_BYTES / 2).toInt())
                f.writeText(keep)
            }
            f.appendText("${fmt.format(Date())} $msg\n")
        } catch (_: Exception) {
        }
    }

    /** Собирает файл-отчёт (устройство + настройки без токена + журнал + диагностика) и открывает «Поделиться». */
    fun share(context: Context, settingsInfo: String, diagnostics: String) {
        val out = File(context.cacheDir, "pccontrol-report.txt")
        val body = buildString {
            appendLine("Пульт ПК — отчёт ${fmt.format(Date())}")
            appendLine("Версия: ${getInstalledVersionName(context)}")
            appendLine("Телефон: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
            appendLine()
            appendLine("== Настройки ==")
            appendLine(settingsInfo)
            appendLine()
            if (diagnostics.isNotBlank()) {
                appendLine("== Диагностика ==")
                appendLine(diagnostics)
                appendLine()
            }
            appendLine("== Журнал ==")
            appendLine(try { file?.readText() ?: "" } catch (_: Exception) { "" })
        }
        out.writeText(body)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Пульт ПК — отчёт")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Отправить отчёт").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
