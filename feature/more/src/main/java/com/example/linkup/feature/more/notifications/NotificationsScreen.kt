package com.example.linkup.feature.more.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.linkup.core.designsystem.component.ChoiceChip
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft
import com.example.linkup.data.model.Notification
import com.example.linkup.data.util.RelativeTime
import com.example.linkup.data.util.TimeBucket

/**
 * The notifications inbox.
 *
 * @param onOpenProfile invoked with a user id when a row leads to someone's profile.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    // A single timestamp for the whole pass keeps every row's label consistent.
    val now = remember(state.items) { System.currentTimeMillis() }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier.fillMaxSize().background(Color.White)) {

        NotificationsHeader(
            unreadCount = state.unreadCount,
            isRefreshing = state.isRefreshing,
            canMarkAllRead = state.canMarkAllRead,
            hasItems = state.items.isNotEmpty(),
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onMarkAllRead = viewModel::markAllRead,
            onClearAll = { showClearDialog = true }
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceChip("All", state.filter == NotificationFilter.ALL) {
                viewModel.setFilter(NotificationFilter.ALL)
            }
            ChoiceChip(
                text = if (state.unreadCount > 0) "Unread (${state.unreadCount})" else "Unread",
                selected = state.filter == NotificationFilter.UNREAD
            ) {
                viewModel.setFilter(NotificationFilter.UNREAD)
            }
        }

        state.message?.let { message ->
            FeedbackStrip(message, state.messageIsError, viewModel::consumeMessage)
        }

        when {
            state.isLoading -> NotificationSkeleton()

            state.error != null -> NotificationErrorState(
                message = state.error.orEmpty(),
                onRetry = { viewModel.load(force = true) }
            )

            state.isEmpty -> NotificationEmptyState(filter = state.filter)

            else -> NotificationList(
                state = state,
                now = now,
                onOpen = { notification ->
                    viewModel.onOpened(notification)
                    notification.destinationUserId?.let(onOpenProfile)
                },
                onToggleRead = viewModel::toggleRead,
                onDelete = viewModel::delete,
                onLoadMore = viewModel::loadMore
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all notifications?") },
            text = { Text("This removes every notification from your inbox. It can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAll()
                    }
                ) {
                    Text("Clear all", color = Color(0xFFB3261E))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NotificationsHeader(
    unreadCount: Int,
    isRefreshing: Boolean,
    canMarkAllRead: Boolean,
    hasItems: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(shadowElevation = 1.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = LinkUpIcons.ChevronLeft,
                    contentDescription = "Back",
                    modifier = Modifier.size(26.dp).clickable(onClick = onBack)
                )
                Spacer(Modifier.width(10.dp))
                Text("Notifications", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    NotificationBadge(unreadCount)
                }
                Spacer(Modifier.weight(1f))

                if (isRefreshing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = LinkPurple,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = LinkUpIcons.Refresh,
                        contentDescription = "Refresh",
                        tint = LinkPurple,
                        modifier = Modifier.clickable(onClick = onRefresh).padding(8.dp).size(20.dp)
                    )
                }

                Box {
                    Icon(
                        imageVector = LinkUpIcons.MoreHorizontal,
                        contentDescription = "More",
                        tint = LinkPurple,
                        modifier = Modifier.clickable { menuOpen = true }.padding(8.dp).size(20.dp)
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Mark all as read") },
                            enabled = canMarkAllRead,
                            onClick = { menuOpen = false; onMarkAllRead() }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear all", color = Color(0xFFB3261E)) },
                            enabled = hasItems,
                            onClick = { menuOpen = false; onClearAll() }
                        )
                    }
                }
            }
            HorizontalDivider(color = LinkDivider)
        }
    }
}

@Composable
private fun NotificationList(
    state: NotificationsUiState,
    now: Long,
    onOpen: (Notification) -> Unit,
    onToggleRead: (Notification) -> Unit,
    onDelete: (Notification) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()

    // Section headers are derived, not stored, so they stay correct as time passes.
    val sections = remember(state.items, now) {
        state.items.groupBy { RelativeTime.bucket(it.createdAt, now) }
            .toList()
            .sortedBy { (bucket, _) -> bucket.ordinal }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        sections.forEach { (bucket, items) ->
            item(key = "header-${bucket.name}") {
                SectionHeader(RelativeTime.bucketLabel(bucket))
            }
            items(items, key = { it.id }) { notification ->
                NotificationRow(
                    notification = notification,
                    now = now,
                    isBusy = notification.id in state.busyIds,
                    onOpen = { onOpen(notification) },
                    onToggleRead = { onToggleRead(notification) },
                    onDelete = { onDelete(notification) }
                )
                HorizontalDivider(color = LinkDivider.copy(alpha = 0.6f))
            }
        }

        if (state.hasMore) {
            item(key = "load-more-${state.nextCursor}") {
                // Composing this footer means it scrolled into view — that is the trigger.
                LaunchedEffect(state.nextCursor) { onLoadMore() }
                Box(
                    Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = LinkPurple, modifier = Modifier.size(22.dp))
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun NotificationRow(
    notification: Notification,
    now: Long,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val background = if (notification.isRead) Color.White else LinkPurpleSoft.copy(alpha = 0.45f)

    Row(
        Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(enabled = !isBusy, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NotificationAvatar(notification)

        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = notificationText(notification),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                RelativeTime.format(notification.createdAt, now),
                color = if (notification.isRead) LinkMuted else LinkPurple,
                fontSize = 11.sp,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold
            )
        }

        if (!notification.isRead) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(LinkPurple))
            Spacer(Modifier.width(6.dp))
        }

        Box {
            Icon(
                imageVector = LinkUpIcons.MoreHorizontal,
                contentDescription = "Options",
                tint = LinkMuted,
                modifier = Modifier.clickable(enabled = !isBusy) { menuOpen = true }.padding(6.dp).size(18.dp)
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (notification.isRead) "Mark as unread" else "Mark as read") },
                    onClick = { menuOpen = false; onToggleRead() }
                )
                DropdownMenuItem(
                    text = { Text("Remove", color = Color(0xFFB3261E)) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = LinkMuted,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun FeedbackStrip(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isError) Color(0xFFFDECEF) else Color(0xFFE9F7EF))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val foreground = if (isError) Color(0xFFB3261E) else Color(0xFF1B7A43)
        Text(message, color = foreground, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(
            imageVector = LinkUpIcons.Close,
            contentDescription = "Dismiss",
            tint = foreground,
            modifier = Modifier.clickable(onClick = onDismiss).padding(start = 8.dp).size(15.dp)
        )
    }
}

@Composable
private fun NotificationEmptyState(filter: NotificationFilter) {
    val (title, body) = if (filter == NotificationFilter.UNREAD) {
        "You're all caught up" to "Every notification has been read. Switch to All to see your history."
    } else {
        "No notifications yet" to
            "When someone follows you or reacts to what you share, it will show up here."
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(LinkPurpleSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(LinkUpIcons.Bell, null, tint = LinkPurple, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text(body, color = LinkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NotificationErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(LinkPurpleSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(LinkUpIcons.Info, null, tint = LinkPurple, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Couldn't load notifications", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text(message, color = LinkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
        ) {
            Text("Try again", fontWeight = FontWeight.Bold)
        }
    }
}

/** Placeholder rows shaped like the real ones, so loading does not jump. */
@Composable
private fun NotificationSkeleton() {
    Column(Modifier.fillMaxSize()) {
        repeat(7) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(LinkDivider))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Box(
                        Modifier.fillMaxWidth(0.85f).height(13.dp)
                            .clip(RoundedCornerShape(4.dp)).background(LinkDivider)
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier.width(56.dp).height(10.dp)
                            .clip(RoundedCornerShape(4.dp)).background(LinkDivider)
                    )
                }
            }
            HorizontalDivider(color = LinkDivider.copy(alpha = 0.6f))
        }
    }
}
