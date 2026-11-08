package com.example.pccontrol

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Быстрые действия без открытия приложения: плитки шторки и виджет на рабочем столе. */
object QuickActions {
    private fun prefs(c: Context) = c.getSharedPreferences("pc_control", Context.MODE_PRIVATE)

    private fun candidates(c: Context): List<String> {
        val p = prefs(c)
        val id = p.getString("active_profile_id", "") ?: ""
        return listOf(
            p.getString("lan_host_$id", "") ?: "",
            p.getString("host", "") ?: "",
            p.getString("ts_host_$id", "") ?: ""
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /** Первый адрес, по которому ПК отвечает (дома, затем сохранённый, затем Tailscale). */
    suspend fun reach(c: Context): String? = withContext(Dispatchers.IO) {
        val token = prefs(c).getString("token", "")?.trim() ?: ""
        if (token.isEmpty()) return@withContext null
        for (h in candidates(c)) {
            val ok = try { http(h, token, "/ping", "GET", timeoutMs = 1800).code == 200 } catch (_: Exception) { false }
            if (ok) return@withContext h
        }
        null
    }

    suspend fun isOnline(c: Context): Boolean = reach(c) != null

    /** Включение по сети (Wake-on-LAN). */
    suspend fun wake(c: Context): String {
        val mac = prefs(c).getString("mac", "")?.trim() ?: ""
        if (mac.isEmpty()) return "MAC не задан — откройте приложение дома"
        if (isOnline(c)) return "ПК уже включён"
        sendMagicPacket(mac)
        return "Сигнал включения отправлен"
    }

    /** Команда питания/сессии: /lock, /monitor_off, /sleep ... */
    suspend fun command(c: Context, path: String, okText: String): String {
        val token = prefs(c).getString("token", "")?.trim() ?: ""
        val h = reach(c) ?: return "ПК не отвечает"
        return withContext(Dispatchers.IO) {
            try {
                val r = http(h, token, path, "POST", timeoutMs = 4000)
                if (r.code == 200) okText else "Ошибка ${r.code}"
            } catch (e: Exception) {
                "Нет связи: ${friendlyNetError(e.localizedMessage)}"
            }
        }
    }
}


/** Просит систему добавить плитку в шторку (Android 13+). На старых версиях — подсказка, как добавить вручную. */
fun requestAddTile(context: Context, cls: Class<*>, label: String, onResult: (String) -> Unit) {
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        try {
            val sbm = context.getSystemService(android.app.StatusBarManager::class.java)
            sbm.requestAddTileService(
                android.content.ComponentName(context, cls), label,
                android.graphics.drawable.Icon.createWithResource(context, R.mipmap.ic_launcher),
                { it.run() }
            ) { code ->
                onResult(
                    when (code) {
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Плитка «$label» добавлена ✔"
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Плитка «$label» уже в шторке"
                        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Плитка не добавлена"
                        else -> "Не удалось добавить плитку (код $code)"
                    }
                )
            }
        } catch (e: Exception) {
            onResult("Не удалось добавить плитку: ${e.localizedMessage}")
        }
    } else {
        onResult("Потяните шторку вниз → значок карандаша → перетащите «$label» в быстрые настройки")
    }
}

