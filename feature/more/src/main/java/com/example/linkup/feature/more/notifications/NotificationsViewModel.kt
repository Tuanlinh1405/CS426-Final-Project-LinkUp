package com.example.linkup.feature.more.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.model.Notification
import com.example.linkup.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private var loadedOnce = false

    /**
     * Loads the first page.
     *
     * Re-entering the screen refreshes in place rather than flashing the skeleton,
     * which also picks up notifications that arrived while the user was elsewhere.
     */
    fun load(force: Boolean = false) {
        if (loadedOnce && !force && _uiState.value.items.isNotEmpty()) {
            refresh()
            return
        }
        loadedOnce = true
        _uiState.update { it.copy(isLoading = true, error = null) }
        fetchFirstPage()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, error = null, message = null) }
        fetchFirstPage()
    }

    fun setFilter(filter: NotificationFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.update {
            it.copy(filter = filter, isLoading = true, error = null, items = emptyList(), nextCursor = null)
        }
        fetchFirstPage()
    }

    /** Appends the next page. Ignored when a page is already in flight. */
    fun loadMore() {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.isLoadingMore || state.isLoading) return

        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            notificationRepository.load(cursor, state.filter == NotificationFilter.UNREAD)
                .onSuccess { page ->
                    _uiState.update { current ->
                        // Guard against a filter switch that landed mid-request.
                        val existing = current.items.map { it.id }.toSet()
                        current.copy(
                            items = current.items + page.items.filterNot { it.id in existing },
                            nextCursor = page.nextCursor,
                            unreadCount = page.unreadCount,
                            isLoadingMore = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            message = error.message ?: "Could not load more",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    /**
     * Marks a notification read when it is opened.
     *
     * Applied optimistically and rolled back on failure — opening a row should feel
     * instant, and a failed write must not leave the badge lying.
     */
    fun onOpened(notification: Notification) {
        if (notification.isRead) return
        setRead(notification.id, read = true)
    }

    fun toggleRead(notification: Notification) {
        setRead(notification.id, read = !notification.isRead)
    }

    private fun setRead(id: String, read: Boolean) {
        val before = _uiState.value
        val target = before.items.firstOrNull { it.id == id } ?: return
        if (target.isRead == read) return

        _uiState.update { state ->
            state.copy(
                items = state.items.map { if (it.id == id) it.copy(isRead = read) else it },
                unreadCount = (state.unreadCount + if (read) -1 else 1).coerceAtLeast(0),
                busyIds = state.busyIds + id
            )
        }

        viewModelScope.launch {
            notificationRepository.setRead(id, read)
                .onSuccess { unread ->
                    _uiState.update { it.copy(unreadCount = unread, busyIds = it.busyIds - id) }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.map { if (it.id == id) it.copy(isRead = !read) else it },
                            unreadCount = before.unreadCount,
                            busyIds = state.busyIds - id,
                            message = error.message ?: "Could not update that notification",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    fun markAllRead() {
        val before = _uiState.value
        if (before.unreadCount == 0) return

        _uiState.update { state ->
            state.copy(items = state.items.map { it.copy(isRead = true) }, unreadCount = 0)
        }

        viewModelScope.launch {
            notificationRepository.markAllRead()
                .onSuccess { unread ->
                    _uiState.update { it.copy(unreadCount = unread) }
                    // The unread tab is now empty by definition; reload so it is not stale.
                    if (_uiState.value.filter == NotificationFilter.UNREAD) fetchFirstPage()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            items = before.items,
                            unreadCount = before.unreadCount,
                            message = error.message ?: "Could not mark everything read",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    fun delete(notification: Notification) {
        val before = _uiState.value
        _uiState.update { state ->
            state.copy(
                items = state.items.filterNot { it.id == notification.id },
                unreadCount = if (notification.isRead) {
                    state.unreadCount
                } else {
                    (state.unreadCount - 1).coerceAtLeast(0)
                },
                busyIds = state.busyIds + notification.id
            )
        }

        viewModelScope.launch {
            notificationRepository.delete(notification.id)
                .onSuccess { unread ->
                    _uiState.update {
                        it.copy(
                            unreadCount = unread,
                            busyIds = it.busyIds - notification.id,
                            message = "Notification removed",
                            messageIsError = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            items = before.items,
                            unreadCount = before.unreadCount,
                            busyIds = it.busyIds - notification.id,
                            message = error.message ?: "Could not remove that notification",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    fun clearAll() {
        val before = _uiState.value
        if (before.items.isEmpty()) return

        _uiState.update { it.copy(items = emptyList(), unreadCount = 0, nextCursor = null) }

        viewModelScope.launch {
            notificationRepository.clearAll()
                .onSuccess {
                    _uiState.update {
                        it.copy(message = "All notifications cleared", messageIsError = false)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            items = before.items,
                            unreadCount = before.unreadCount,
                            nextCursor = before.nextCursor,
                            message = error.message ?: "Could not clear notifications",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    /**
     * Refreshes just the badge figure.
     *
     * Cheap enough to call whenever the user lands on a screen showing the bell,
     * and it leaves the list untouched.
     */
    fun refreshUnreadCount() {
        viewModelScope.launch {
            notificationRepository.unreadCount()
                .onSuccess { unread -> _uiState.update { it.copy(unreadCount = unread) } }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Clears cached state on logout so the next account never sees the last one's inbox. */
    fun reset() {
        loadedOnce = false
        _uiState.value = NotificationsUiState()
    }

    private fun fetchFirstPage() {
        val unreadOnly = _uiState.value.filter == NotificationFilter.UNREAD
        viewModelScope.launch {
            notificationRepository.load(cursor = null, unreadOnly = unreadOnly)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            items = page.items,
                            nextCursor = page.nextCursor,
                            unreadCount = page.unreadCount
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        // Keep whatever is already listed rather than blanking the screen.
                        if (state.items.isNotEmpty()) {
                            state.copy(
                                isLoading = false,
                                isRefreshing = false,
                                message = error.message ?: "Could not refresh",
                                messageIsError = true
                            )
                        } else {
                            state.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = error.message ?: "Could not load your notifications"
                            )
                        }
                    }
                }
        }
    }
}
