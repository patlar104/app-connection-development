package dev.appconnect.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.appconnect.data.repository.ClipboardRepository
import dev.appconnect.domain.model.ClipboardItem
import dev.appconnect.network.WebSocketClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ClipboardRepository,
    private val webSocketClient: WebSocketClient
) : ViewModel() {

    val clipboardItems = repository.getAllClipboardItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val connectionState = webSocketClient.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WebSocketClient.ConnectionState.Disconnected
        )
}

