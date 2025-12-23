package dev.appconnect.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import dev.appconnect.core.SyncManager
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardSyncTile : TileService() {

    @Inject
    lateinit var syncManager: SyncManager

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        // Trigger manual clipboard sync
        Timber.d("Quick Settings tile clicked")
        // Implementation would trigger sync from current clipboard
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Sync Clipboard"
        tile.updateTile()
    }
}

