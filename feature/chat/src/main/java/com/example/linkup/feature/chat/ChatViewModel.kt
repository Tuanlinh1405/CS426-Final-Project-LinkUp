package com.example.linkup.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.model.Message
import com.example.linkup.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _isPeerTyping = MutableStateFlow(false)
    val isPeerTyping: StateFlow<Boolean> = _isPeerTyping.asStateFlow()

    private val _messagesState = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messagesState.asStateFlow()

    val connectionState = chatRepository.connectionState

    private var activeMessagesJob: Job? = null
    private var peerTypingResetJob: Job? = null

    init {
        viewModelScope.launch {
            chatRepository.typingEventFlow.collect { (convId, isTyping) ->
                if (convId == _currentConversationId.value) {
                    _isPeerTyping.value = isTyping
                    if (isTyping) {
                        peerTypingResetJob?.cancel()
                        peerTypingResetJob = viewModelScope.launch {
                            delay(3000)
                            _isPeerTyping.value = false
                        }
                    }
                }
            }
        }
    }

    fun setConversation(conversationId: String) {
        if (_currentConversationId.value == conversationId) return
        _currentConversationId.value = conversationId

        activeMessagesJob?.cancel()
        activeMessagesJob = viewModelScope.launch {
            chatRepository.getMessagesFlow(conversationId).collect { list ->
                _messagesState.value = list
            }
        }

        viewModelScope.launch {
            chatRepository.loadMessages(conversationId)
            chatRepository.markAsRead(conversationId)
        }
    }

    fun sendMessage(text: String) {
        val convId = _currentConversationId.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            chatRepository.sendMessage(convId, text.trim())
        }
    }

    fun sendTyping(isTyping: Boolean) {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch {
            chatRepository.sendTyping(convId, isTyping)
        }
    }

    fun markAsRead() {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch {
            chatRepository.markAsRead(convId)
        }
    }
}
