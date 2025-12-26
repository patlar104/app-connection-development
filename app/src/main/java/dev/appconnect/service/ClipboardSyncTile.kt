package dev.appconnect.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import dev.appconnect.R
import dev.appconnect.core.SyncManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Quick Settings Tile for toggling clipboard sync.
 * 
 * TODO: This is an incomplete implementation.
 * The tile currently shows active state but doesn't fully implement toggle functionality.
 * 
 * Future implementation should:
 * - Properly reflect actual sync state (connected/disconnected)
 * - Toggle sync service start/stop on click
 * - Update tile state based on connection state changes
 * - Handle edge cases and service lifecycle properly
 */
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
        tile.label = getString(R.string.service_sync_clipboard_tile_label)
        tile.updateTile()
    }
}

