package com.example.devtoggle

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class DevToggleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()

        if (!DevToggleController.isShizukuReady()) {
            Toast.makeText(this, "Open DevToggle app first to grant Shizuku permission", Toast.LENGTH_LONG).show()
            return
        }

        DevToggleController.toggle(
            context = this,
            onResult = { newState ->
                qsTile?.state = if (newState) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                qsTile?.updateTile()
            },
            onError = { message ->
                Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        if (!DevToggleController.isShizukuReady()) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.updateTile()
            return
        }

        DevToggleController.readState(
            context = this,
            onResult = { enabled ->
                tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.updateTile()
            },
            onError = {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.updateTile()
            }
        )
    }
}
