package com.example.linkup.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.repository.AuthRepository
import com.example.linkup.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private var connectedUserId: String? = null

    fun onSessionUserChanged(userId: String?) {
        if (connectedUserId == userId) return
        connectedUserId = userId
        if (userId == null) {
            chatRepository.disconnectWebSocket()
        } else {
            chatRepository.connectWebSocket()
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            chatRepository.disconnectWebSocket()
            authRepository.logout()
            onLoggedOut()
        }
    }

    override fun onCleared() {
        chatRepository.disconnectWebSocket()
        super.onCleared()
    }
}
