package dev.appconnect.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.appconnect.presentation.viewmodel.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AppConnect",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Connection status
        ConnectionStatusCard(connectionState = viewModel.connectionState.value)

        Spacer(modifier = Modifier.height(16.dp))

        // Clipboard items list
        ClipboardItemsList(items = viewModel.clipboardItems.value)
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

