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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.appconnect.presentation.viewmodel.MainViewModel

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
                .padding(16.dp)
        ) {
            // Header with QR scan button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AppConnect",
                    style = MaterialTheme.typography.headlineMedium
                )
                
                FloatingActionButton(
                    onClick = { showQrScanner = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR Code"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connection status
            ConnectionStatusCard(connectionState = connectionState)

            Spacer(modifier = Modifier.height(16.dp))

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
            Triple(Color(0xFF4CAF50), "Connected", "✓")
        dev.appconnect.network.WebSocketClient.ConnectionState.Connecting -> 
            Triple(Color(0xFFFFC107), "Connecting...", "⟳")
        dev.appconnect.network.WebSocketClient.ConnectionState.Disconnecting -> 
            Triple(Color(0xFFFF9800), "Disconnecting...", "⟳")
        dev.appconnect.network.WebSocketClient.ConnectionState.Disconnected -> 
            Triple(Color(0xFFF44336), "Disconnected", "✗")
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
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
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
                    .size(40.dp)
                    .background(
                        color = statusColor.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusIcon,
                    fontSize = 20.sp,
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
            text = "Clipboard History",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        items.forEach { item ->
            ClipboardItemCard(item = item)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ClipboardItemCard(item: dev.appconnect.domain.model.ClipboardItem) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = item.content.take(50),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Synced: ${item.synced}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

