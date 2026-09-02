package com.example.linkup.feature.profile.people

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
import com.example.linkup.core.designsystem.component.PersonRow
import com.example.linkup.core.designsystem.component.PersonRowSkeleton
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft

/**
 * Followers or following for one account.
 *
 * @param userId whose list to show; null means the signed-in user.
 */
@Composable
fun UserListScreen(
    mode: UserListMode,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    userId: String? = null,
    viewModel: UserListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val target = userId ?: "me"

    LaunchedEffect(target, mode) { viewModel.load(target, mode) }

    val title = when (mode) {
        UserListMode.FOLLOWERS -> "Followers"
        UserListMode.FOLLOWING -> "Following"
    }

    Column(modifier.fillMaxSize().background(Color.White)) {
        ScreenHeader(if (state.total > 0) "$title (${state.total})" else title, onBack)

        state.message?.let { message ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFDECEF))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(message, color = Color(0xFFB3261E), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    "✕",
                    color = Color(0xFFB3261E),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = viewModel::consumeMessage).padding(start = 8.dp)
                )
            }
        }

        when {
            state.isLoading -> PersonRowSkeleton()

            state.error != null -> ListErrorState(
                message = state.error.orEmpty(),
                onRetry = { viewModel.load(target, mode, force = true) }
            )

            state.isEmpty -> ListEmptyState(mode)

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { person ->
                    PersonRow(
                        displayName = person.displayName,
                        handle = person.handle,
                        initials = person.initials,
                        avatarUrl = person.avatarUrl,
                        subtitle = person.bio,
                        isMe = person.isMe,
                        isFollowing = person.isFollowing,
                        isBusy = person.id in state.busyIds,
                        onClick = { onOpenProfile(person.id) },
                        onToggleFollow = { viewModel.toggleFollow(person) }
                    )
                    HorizontalDivider(color = LinkDivider.copy(alpha = 0.6f))
                }

                if (state.hasMore) {
                    item(key = "more-${state.nextCursor}") {
                        LaunchedEffect(state.nextCursor) { viewModel.loadMore(target, mode) }
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
private fun ListEmptyState(mode: UserListMode) {
    val (title, body) = when (mode) {
        UserListMode.FOLLOWERS ->
            "No followers yet" to "When someone follows this account, they'll be listed here."
        UserListMode.FOLLOWING ->
            "Not following anyone" to "Use Search to find people, then tap Follow to see them here."
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(LinkPurpleSoft),
            contentAlignment = Alignment.Center
        ) {
            Text("☺", color = LinkPurple, fontSize = 28.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text(body, color = LinkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ListErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(LinkPurpleSoft),
            contentAlignment = Alignment.Center
        ) {
            Text("!", color = LinkPurple, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text("Couldn't load this list", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
