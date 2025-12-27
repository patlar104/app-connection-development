package dev.appconnect.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.appconnect.presentation.theme.ConnectionStatusConnected
import dev.appconnect.presentation.theme.ConnectionStatusConnecting
import dev.appconnect.presentation.theme.ConnectionStatusDisconnecting
import dev.appconnect.presentation.theme.ConnectionStatusDisconnected
import dev.appconnect.presentation.viewmodel.MainViewModel
import dev.appconnect.R

// UI constants
private const val PADDING_DEFAULT = 16
private const val SPACING_SMALL = 8
private const val SPACING_TINY = 4
private const val FAB_SIZE = 48
private const val STATUS_INDICATOR_SIZE = 40
private const val STATUS_ICON_FONT_SIZE = 20

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var showQrScanner by remember { mutableStateOf(false) }
    
    // Observe connection state changes
    val connectionState by viewModel.connectionState.collectAsState()
    
    // Show QR scanner if requested
    if (showQrScanner) {
        QrScannerScreen(
            onScanSuccess = { qrJson ->
                showQrScanner = false
                viewModel.pairFromQrCode(qrJson)
            },
            onDismiss = {
                showQrScanner = false
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(PADDING_DEFAULT.dp)
        ) {
            // Header with QR scan button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ui_app_connect),
                    style = MaterialTheme.typography.headlineMedium
                )
                
                FloatingActionButton(
                    onClick = { showQrScanner = true },
                    modifier = Modifier.size(FAB_SIZE.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = stringResource(R.string.ui_scan_qr_code)
                    )
                }
            }

            Spacer(modifier = Modifier.height(PADDING_DEFAULT.dp))

            // Connection status
            ConnectionStatusCard(connectionState = connectionState)

            Spacer(modifier = Modifier.height(PADDING_DEFAULT.dp))

            // Clipboard items list
            ClipboardItemsList(items = viewModel.clipboardItems.value)
        }
    }
}

@Composable
fun ConnectionStatusCard(connectionState: dev.appconnect.network.WebSocketClient.ConnectionState) {
    // Determine color and icon based on state
    val (statusColor, statusText, statusIcon) = when (connectionState) {
        dev.appconnect.network.WebSocketClient.ConnectionState.Connected -> 
            Triple(ConnectionStatusConnected, stringResource(R.string.ui_connected), "✓")
        dev.appconnect.network.WebSocketClient.ConnectionState.Connecting -> 
            Triple(ConnectionStatusConnecting, stringResource(R.string.ui_connecting), "⟳")
        dev.appconnect.network.WebSocketClient.ConnectionState.Disconnecting -> 
            Triple(ConnectionStatusDisconnecting, stringResource(R.string.ui_disconnecting), "⟳")
        dev.appconnect.network.WebSocketClient.ConnectionState.Disconnected -> 
            Triple(ConnectionStatusDisconnected, stringResource(R.string.ui_disconnected), "✗")
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PADDING_DEFAULT.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.ui_connection_status),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(SPACING_TINY.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Status indicator
            Box(
                modifier = Modifier
                    .size(STATUS_INDICATOR_SIZE.dp)
                    .background(
                        color = statusColor.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusIcon,
                    fontSize = STATUS_ICON_FONT_SIZE.sp,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
fun ClipboardItemsList(items: List<dev.appconnect.domain.model.ClipboardItem>) {
    Column {
        Text(
            text = stringResource(R.string.ui_clipboard_history),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(SPACING_SMALL.dp))
        items.forEach { item ->
            ClipboardItemCard(item = item)
            Spacer(modifier = Modifier.height(SPACING_SMALL.dp))
        }
    }
}

@Composable
fun ClipboardItemCard(item: dev.appconnect.domain.model.ClipboardItem) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(PADDING_DEFAULT.dp)
        ) {
            Text(
                text = item.content.take(dev.appconnect.core.SyncManager.PREVIEW_TEXT_LENGTH),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.ui_synced, item.synced.toString()),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

