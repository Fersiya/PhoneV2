package com.example.pccontrol

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Виджет рабочего стола: статус ПК и кнопки «Включить», «Блок», «Экран», «Сон». */
class PcWidget : AppWidgetProvider() {
    companion object {
        const val ACTION = "com.example.pccontrol.WIDGET_ACTION"
        const val EXTRA = "what"

        private fun pi(c: Context, what: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                c, code, Intent(c, PcWidget::class.java).setAction(ACTION).putExtra(EXTRA, what),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun render(c: Context, mgr: AppWidgetManager, ids: IntArray, status: String) {
            val v = RemoteViews(c.packageName, R.layout.widget_pc)
            v.setTextViewText(R.id.w_status, status)
            v.setOnClickPendingIntent(R.id.w_wake, pi(c, "wake", 1))
            v.setOnClickPendingIntent(R.id.w_lock, pi(c, "lock", 2))
            v.setOnClickPendingIntent(R.id.w_screen, pi(c, "screen", 3))
            v.setOnClickPendingIntent(R.id.w_sleep, pi(c, "sleep", 4))
            v.setOnClickPendingIntent(R.id.w_status, pi(c, "refresh", 5))
            for (id in ids) mgr.updateAppWidget(id, v)
        }

        fun refreshAll(c: Context, status: String? = null) {
            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(ComponentName(c, PcWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val text = status ?: if (QuickActions.isOnline(c)) "🟢 ПК онлайн" else "⚪ ПК не отвечает"
                render(c, mgr, ids, text)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetManager, appWidgetIds, "Проверяю…")
        refreshAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION) return
        val what = intent.getStringExtra(EXTRA) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refreshAll(context, "⏳ Отправляю…")
                val msg = when (what) {
                    "wake" -> QuickActions.wake(context)
                    "lock" -> QuickActions.command(context, "/lock", "ПК заблокирован")
                    "screen" -> QuickActions.command(context, "/monitor_off", "Экран ПК выключен")
                    "sleep" -> QuickActions.command(context, "/sleep", "ПК засыпает")
                    else -> ""
                }
                refreshAll(context, if (msg.isNotEmpty()) msg else null)
                kotlinx.coroutines.delay(2500)
                refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
