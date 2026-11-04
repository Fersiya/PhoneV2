package com.example.pccontrol

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.N)
class PcControlTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Пульт ПК"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("pc_control", Context.MODE_PRIVATE)
        val mac = prefs.getString("mac", "") ?: ""

        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "WoL сигнал…"
        tile.updateTile()

        scope.launch {
            if (mac.isNotBlank()) {
                sendMagicPacket(mac)
            }
            delay(1200)
            withContext(Dispatchers.Main) {
                val t = qsTile ?: return@withContext
                t.state = Tile.STATE_INACTIVE
                t.label = "Пульт ПК"
                t.updateTile()
            }
        }
    }
}
