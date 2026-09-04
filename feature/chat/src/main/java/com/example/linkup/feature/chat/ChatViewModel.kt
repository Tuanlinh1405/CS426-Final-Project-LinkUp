package com.example.linkup.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.local.media.DeviceImageReader
import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.Message
import com.example.linkup.data.model.Participant
import com.example.linkup.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val imageReader: DeviceImageReader,
) : ViewModel() {

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _isPeerTyping = MutableStateFlow(false)
    val isPeerTyping: StateFlow<Boolean> = _isPeerTyping.asStateFlow()

    private val _messagesState = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messagesState.asStateFlow()

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    /** senderId -> (avatarUrl, initials), so a bubble can show who wrote it in a group. */
    val participantFaces: StateFlow<Map<String, Pair<String?, String>>> = _currentConversation
        .map { conv ->
            conv?.participants.orEmpty().associate { p: Participant ->
                p.id to (p.avatarUrl to p.initials)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** True when any other member of the open thread has a live socket. */
    val isPeerOnline: StateFlow<Boolean> = combine(
        chatRepository.onlineUserIds,
        _currentConversation
    ) { online, conv ->
        val others = conv?.others.orEmpty()
        others.isNotEmpty() && others.any { it.id in online }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val connectionState = chatRepository.connectionState

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()

    private val _canLoadOlder = MutableStateFlow(false)
    val canLoadOlder: StateFlow<Boolean> = _canLoadOlder.asStateFlow()

    private var activeMessagesJob: Job? = null
    private var activeConversationJob: Job? = null
    private var peerTypingResetJob: Job? = null
    private var selfTypingJob: Job? = null
    private var lastTypingSent: Boolean? = null
    private var loadOlderJob: Job? = null

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
        chatRepository.setActiveConversation(conversationId)

        // Re-entering the same thread: the flows are still collected, but anything that
        // arrived while the list was on screen needs clearing.
        if (_currentConversationId.value == conversationId) {
            viewModelScope.launch { chatRepository.markAsRead(conversationId) }
            return
        }
        _currentConversationId.value = conversationId

        _isPeerTyping.value = false
        peerTypingResetJob?.cancel()
        cancelTyping()

        // Seed with whatever the repository already has so the UI shows cached messages
        // immediately instead of flashing empty while the REST fetch completes.
        val existingMessages = chatRepository.getMessagesFlow(conversationId).value
        _messagesState.value = existingMessages

        activeMessagesJob?.cancel()
        activeMessagesJob = viewModelScope.launch {
            chatRepository.getMessagesFlow(conversationId).collect { list ->
                _messagesState.value = list
            }
        }

        activeConversationJob?.cancel()
        activeConversationJob = viewModelScope.launch {
            chatRepository.conversationFlow(conversationId).collect { conv ->
                _currentConversation.value = conv
            }
        }

        viewModelScope.launch {
            chatRepository.loadMessages(conversationId)
            chatRepository.markAsRead(conversationId)
            chatRepository.loadPresence(conversationId)
        }
    }

    /** Loads the next older page, guarding against overlapping requests. */
    fun loadOlder() {
        val convId = _currentConversationId.value ?: return
        if (_isLoadingOlder.value) return
        val currentCount = _messagesState.value.size
        if (currentCount == 0) return

        loadOlderJob?.cancel()
        loadOlderJob = viewModelScope.launch {
            _isLoadingOlder.value = true
            chatRepository.loadMessages(convId, limit = 50, offset = currentCount.toLong())
            _isLoadingOlder.value = false
            _canLoadOlder.value = false
        }
    }

    /** Tells the UI it can attempt to load older messages (scrolled to top). */
    fun onNearTop() {
        if (_canLoadOlder.value) return
        _canLoadOlder.value = true
    }

    fun sendMessage(text: String) {
        val convId = _currentConversationId.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            chatRepository.sendMessage(convId, text.trim())
        }
        cancelTyping()
    }

    /** Picks, decodes and uploads an image, then sends it as an IMAGE message. */
    fun sendImage(uri: Uri) {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch {
            val image = imageReader.read(uri, DeviceImageReader.CHAT_MAX_DIMENSION, "chat.jpg")
                ?: return@launch
            chatRepository.sendImageMessage(convId, image)
        }
        cancelTyping()
    }

    /** Unsends one of the user's own messages for everyone. */
    fun deleteMessage(messageId: String) {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch {
            chatRepository.deleteMessage(convId, messageId)
        }
    }

    /** Debounced: one TYPING=true burst, auto-false when the user stops typing. */
    fun onDraftChanged(isTyping: Boolean) {
        val convId = _currentConversationId.value ?: return
        selfTypingJob?.cancel()

        if (isTyping) {
            if (lastTypingSent != true) {
                lastTypingSent = true
                viewModelScope.launch { chatRepository.sendTyping(convId, true) }
            }
            selfTypingJob = viewModelScope.launch {
                delay(TYPING_STOP_DELAY_MS)
                lastTypingSent = false
                chatRepository.sendTyping(convId, false)
            }
        } else {
            cancelTyping()
        }
    }

    private fun cancelTyping() {
        selfTypingJob?.cancel()
        selfTypingJob = null
        if (lastTypingSent == true) {
            lastTypingSent = false
            val convId = _currentConversationId.value ?: return
            viewModelScope.launch { chatRepository.sendTyping(convId, false) }
        }
    }

    fun markAsRead() {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch {
            chatRepository.markAsRead(convId)
        }
    }

    /** Called when the thread leaves the screen, so later messages badge the list again. */
    fun onLeaveConversation() {
        chatRepository.setActiveConversation(null)
        cancelTyping()
    }

    override fun onCleared() {
        chatRepository.setActiveConversation(null)
        super.onCleared()
    }

    companion object {
        private const val TYPING_STOP_DELAY_MS = 2000L
    }
}
