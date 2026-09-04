package com.example.linkup.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.UserSummary
import com.example.linkup.data.repository.ChatRepository
import com.example.linkup.data.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val friendRepository: FriendRepository,
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val typingConversationIds: StateFlow<Set<String>> = chatRepository.typingConversationIds

    val conversations: StateFlow<List<Conversation>> = combine(
        chatRepository.conversationsState,
        searchQuery
    ) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.user.name.contains(query, ignoreCase = true) ||
                        it.preview.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val onlineUserIds: StateFlow<Set<String>> = chatRepository.onlineUserIds

    val onlineConversations: StateFlow<List<Conversation>> = combine(
        chatRepository.conversationsState,
        onlineUserIds
    ) { list, onlineIds ->
        list.filter { !it.isGroup && it.user.id in onlineIds }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _friends = MutableStateFlow<List<UserSummary>>(emptyList())
    val friends: StateFlow<List<UserSummary>> = _friends.asStateFlow()

    private val _showGroupDialog = MutableStateFlow(false)
    val showGroupDialog: StateFlow<Boolean> = _showGroupDialog.asStateFlow()

    private val _isCreatingGroup = MutableStateFlow(false)
    val isCreatingGroup: StateFlow<Boolean> = _isCreatingGroup.asStateFlow()

    /** Feedback strip text; the boolean marks it as an error. */
    private val _banner = MutableStateFlow<Pair<String, Boolean>?>(null)
    val banner: StateFlow<Pair<String, Boolean>?> = _banner.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            chatRepository.refreshConversations().onSuccess { list ->
                list.filter { !it.isGroup }.forEach { conv ->
                    chatRepository.loadPresence(conv.id)
                }
            }
            _isLoading.value = false
        }
    }

    fun createDirectConversation(targetUserId: String, onSuccess: (Conversation) -> Unit) {
        viewModelScope.launch {
            chatRepository.createDirectConversation(targetUserId)
                .onSuccess { onSuccess(it) }
                .onFailure { _banner.value = (it.message ?: "Failed to create conversation") to true }
        }
    }

    fun openGroupDialog() {
        _showGroupDialog.value = true
        loadFriends()
    }

    fun dismissGroupDialog() {
        _showGroupDialog.value = false
    }

    fun consumeBanner() {
        _banner.value = null
    }

    private fun loadFriends() {
        viewModelScope.launch {
            friendRepository.friends(null, null)
                .onSuccess { page -> _friends.value = page.items }
                .onFailure { _banner.value = (it.message ?: "Failed to load friends list") to true }
        }
    }

    fun createGroupConversation(name: String, memberIds: List<String>, onSuccess: (Conversation) -> Unit) {
        if (_isCreatingGroup.value) return
        viewModelScope.launch {
            _isCreatingGroup.value = true
            chatRepository.createGroupConversation(name, memberIds)
                .onSuccess { conv ->
                    _showGroupDialog.value = false
                    _banner.value = "Created group \"$name\" with ${memberIds.size} members" to false
                    onSuccess(conv)
                }
                .onFailure { _banner.value = (it.message ?: "Failed to create group") to true }
            _isCreatingGroup.value = false
        }
    }
}
