package com.example.linkup.feature.more.search

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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.linkup.core.designsystem.component.ChoiceChip
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.PersonRow
import com.example.linkup.core.designsystem.component.PersonRowSkeleton
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft

private val TABS = listOf("People", "Posts", "Reels")

/**
 * Finds people to follow.
 *
 * @param onOpenProfile receives the id of the person tapped — this is how another
 *   user's profile becomes reachable at all.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var tab by remember { mutableStateOf(TABS.first()) }

    Column(modifier.fillMaxSize().imePadding().background(Color.White)) {
        ScreenHeader("Search", onBack)

        LinkUpField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = "Search LinkUp",
            placeholder = "Name or username",
            modifier = Modifier.padding(16.dp)
        )

        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TABS.forEach { entry ->
                val label = if (entry == "People" && state.total > 0) "People (${state.total})" else entry
                ChoiceChip(label, selected = tab == entry) { tab = entry }
            }
        }

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

        Spacer(Modifier.height(8.dp))

        if (tab != "People") {
            NotYetState(tab)
            return@Column
        }

        when {
            state.query.isBlank() -> SearchPrompt()

            state.isSearching -> PersonRowSkeleton(count = 6)

            state.error != null -> SearchErrorState(state.error.orEmpty(), viewModel::retry)

            state.showEmptyResult -> NoMatchesState(state.query)

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.results, key = { it.id }) { person ->
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
private fun SearchPrompt() {
    CenteredState(
        glyph = "⌕",
        title = "Find people on LinkUp",
        body = "Search by name or username, then follow anyone you want to keep up with."
    )
}

@Composable
private fun NoMatchesState(query: String) {
    CenteredState(
        glyph = "○",
        title = "No one found",
        body = "Nobody matches \"$query\". Check the spelling, or try their username instead."
    )
}

@Composable
private fun NotYetState(tab: String) {
    CenteredState(
        glyph = "◇",
        title = "$tab search is coming",
        body = "People search is live. $tab results arrive with the feed and reels APIs."
    )
}

@Composable
private fun SearchErrorState(message: String, onRetry: () -> Unit) {
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
        Text("Search failed", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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

@Composable
private fun CenteredState(glyph: String, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(LinkPurpleSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = LinkPurple, fontSize = 28.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text(body, color = LinkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
