package com.example.linkup.feature.profile.friends

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.core.designsystem.component.AnimatedBanner
import com.example.linkup.core.designsystem.component.ChoiceChip
import com.example.linkup.core.designsystem.component.FriendControls
import com.example.linkup.core.designsystem.component.PersonRow
import com.example.linkup.core.designsystem.component.PersonRowSkeleton
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft
import com.example.linkup.data.model.UserSummary
import com.example.linkup.feature.profile.friendActionState

/** Friends, incoming requests and "people you may know". */
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier.fillMaxSize().background(Color.White)) {
        ScreenHeader(
            title = "Friends",
            onBack = onBack,
            action = "Refresh",
            onAction = viewModel::refresh
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceChip(
                text = if (state.friendCount > 0) "Friends (${state.friendCount})" else "Friends",
                selected = state.tab == FriendsTab.FRIENDS
            ) { viewModel.setTab(FriendsTab.FRIENDS) }

            ChoiceChip(
                text = if (state.requestCount > 0) "Requests (${state.requestCount})" else "Requests",
                selected = state.tab == FriendsTab.REQUESTS
            ) { viewModel.setTab(FriendsTab.REQUESTS) }

            ChoiceChip(
                text = "Suggestions",
                selected = state.tab == FriendsTab.SUGGESTIONS
            ) { viewModel.setTab(FriendsTab.SUGGESTIONS) }
        }

        AnimatedBanner(
            message = state.message,
            isError = state.messageIsError,
            onDismiss = viewModel::consumeMessage
        )

        when {
            state.isLoading -> PersonRowSkeleton()

            state.error != null -> FriendsErrorState(
                message = state.error.orEmpty(),
                onRetry = { viewModel.load(force = true) }
            )

            state.isEmpty -> FriendsEmptyState(state.tab)

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { person ->
                    Column(Modifier.animateItem()) {
                    FriendRow(
                        person = person,
                        isBusy = person.id in state.busyIds,
                        onOpenProfile = { onOpenProfile(person.id) },
                        viewModel = viewModel
                    )
                    HorizontalDivider(color = LinkDivider.copy(alpha = 0.6f))
                    }
                }

                if (state.hasMore) {
                    item(key = "more-${state.nextCursor}") {
                        LaunchedEffect(state.nextCursor) { viewModel.loadMore() }
                        Box(
                            Modifier.fillMaxWidth().padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = LinkPurple,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun FriendRow(
    person: UserSummary,
    isBusy: Boolean,
    onOpenProfile: () -> Unit,
    viewModel: FriendsViewModel
) {
    val mutualLine = when {
        person.mutualFriendCount == 1 -> "1 mutual friend"
        person.mutualFriendCount > 1 -> "${person.mutualFriendCount} mutual friends"
        else -> person.bio
    }

    PersonRow(
        displayName = person.displayName,
        handle = person.handle,
        initials = person.initials,
        avatarUrl = person.avatarUrl,
        subtitle = mutualLine,
        isMe = person.isMe,
        onClick = onOpenProfile,
        trailing = {
            FriendControls(
                state = person.friendship.friendActionState(),
                isBusy = isBusy,
                onAdd = { viewModel.sendRequest(person) },
                onCancel = { viewModel.cancelRequest(person) },
                onAccept = { viewModel.accept(person) },
                onDecline = { viewModel.decline(person) },
                onUnfriend = { viewModel.unfriend(person) }
            )
        }
    )
}

@Composable
private fun FriendsEmptyState(tab: FriendsTab) {
    val (icon, title, body) = when (tab) {
        FriendsTab.FRIENDS -> Triple(
            LinkUpIcons.People,
            "No friends yet",
            "Check Suggestions, or search for someone and send them a friend request."
        )
        FriendsTab.REQUESTS -> Triple(
            LinkUpIcons.Check,
            "No pending requests",
            "When someone asks to be friends, their request lands here."
        )
        FriendsTab.SUGGESTIONS -> Triple(
            LinkUpIcons.Sparkle,
            "No suggestions right now",
            "Add a few friends and we'll suggest people you have friends in common with."
        )
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
            Icon(icon, null, tint = LinkPurple, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text(body, color = LinkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FriendsErrorState(message: String, onRetry: () -> Unit) {
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
        Text("Couldn't load friends", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
