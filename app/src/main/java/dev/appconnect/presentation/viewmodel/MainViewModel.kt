package dev.appconnect.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.appconnect.core.PairingManager
import dev.appconnect.data.repository.ClipboardRepository
import dev.appconnect.domain.model.ClipboardItem
import dev.appconnect.network.WebSocketClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ClipboardRepository,
    private val webSocketClient: WebSocketClient,
    private val pairingManager: PairingManager
) : ViewModel() {
    companion object {
        const val STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS = 5000L
    }

    val clipboardItems = repository.getAllClipboardItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val connectionState = webSocketClient.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_SUBSCRIPTION_TIMEOUT_MS),
            initialValue = WebSocketClient.ConnectionState.Disconnected
        )
    
    fun pairFromQrCode(qrJson: String) {
        viewModelScope.launch {
            val result = pairingManager.pairFromQrCode(qrJson)
            result.onSuccess {
                Timber.d("Successfully paired with PC")
            }.onFailure { error ->
                Timber.e(error, "Failed to pair with PC")
            }
        }
    }
}

