package dev.appconnect.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.appconnect.presentation.viewmodel.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var showQrScanner by remember { mutableStateOf(false) }
    
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
            ConnectionStatusCard(connectionState = viewModel.connectionState.value)

            Spacer(modifier = Modifier.height(16.dp))

            // Clipboard items list
            ClipboardItemsList(items = viewModel.clipboardItems.value)
        }
    }
}

@Composable
fun ConnectionStatusCard(connectionState: dev.appconnect.network.WebSocketClient.ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Connection Status",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = connectionState.name,
                style = MaterialTheme.typography.bodyMedium
            )
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

