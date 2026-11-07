package com.example.pccontrol

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Общая основа плиток шторки: короткое действие по нажатию + сообщение. */
@RequiresApi(Build.VERSION_CODES.N)
abstract class BaseTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    abstract val idleLabel: String
    abstract suspend fun action(): String

    open suspend fun idleActive(): Boolean = false

    override fun onStartListening() {
        super.onStartListening()
        setTile(idleLabel, Tile.STATE_INACTIVE)
        scope.launch {
            val on = idleActive()
            withContext(Dispatchers.Main) { setTile(idleLabel, if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE) }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun setTile(label: String, state: Int) {
        val t = qsTile ?: return
        t.label = label
        t.state = state
        t.updateTile()
    }

    override fun onClick() {
        super.onClick()
        setTile("Отправляю…", Tile.STATE_ACTIVE)
        scope.launch {
            val msg = try { action() } catch (e: Exception) { "Ошибка: ${e.localizedMessage}" }
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                setTile(idleLabel, Tile.STATE_INACTIVE)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N)
class PcControlTileService : BaseTile() {
    override val idleLabel = "Включить ПК"
    override suspend fun idleActive() = QuickActions.isOnline(applicationContext)
    override suspend fun action() = QuickActions.wake(applicationContext)
}

@RequiresApi(Build.VERSION_CODES.N)
class PcLockTileService : BaseTile() {
    override val idleLabel = "Блокировка ПК"
    override suspend fun action() = QuickActions.command(applicationContext, "/lock", "ПК заблокирован")
}

@RequiresApi(Build.VERSION_CODES.N)
class PcScreenOffTileService : BaseTile() {
    override val idleLabel = "Экран ПК выкл."
    override suspend fun action() = QuickActions.command(applicationContext, "/monitor_off", "Экран ПК выключен")
}
