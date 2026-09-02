package com.example.linkup.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.linkup.core.designsystem.component.LinkUpBottomBar
import com.example.linkup.core.navigation.AppNavigator
import com.example.linkup.core.navigation.AppRoute
import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.Post
import com.example.linkup.data.repository.FakeLinkUpRepository
import com.example.linkup.feature.ai.AiChatScreen
import com.example.linkup.feature.ai.AiConversationsScreen
import com.example.linkup.feature.auth.login.LoginScreen
import com.example.linkup.feature.auth.register.RegisterScreen
import com.example.linkup.feature.auth.session.SessionState
import com.example.linkup.feature.auth.session.SessionViewModel
import com.example.linkup.feature.auth.splash.SplashScreen
import com.example.linkup.feature.chat.ChatDetailRoute
import com.example.linkup.feature.chat.ChatListRoute
import com.example.linkup.feature.dating.DatingDiscoverScreen
import com.example.linkup.feature.dating.DatingMatchScreen
import com.example.linkup.feature.dating.DatingMatchesScreen
import com.example.linkup.feature.dating.DatingProfileScreen
import com.example.linkup.feature.feed.CreatePostScreen
import com.example.linkup.feature.feed.FeedScreen
import com.example.linkup.feature.feed.PostDetailScreen
import com.example.linkup.feature.more.SettingsScreen
import com.example.linkup.feature.more.notifications.NotificationsScreen
import com.example.linkup.feature.more.notifications.NotificationsViewModel
import com.example.linkup.feature.more.search.SearchScreen
import com.example.linkup.feature.more.search.SearchViewModel
import com.example.linkup.feature.profile.edit.EditProfileScreen
import com.example.linkup.feature.profile.friends.FriendsScreen
import com.example.linkup.feature.profile.friends.FriendsViewModel
import com.example.linkup.feature.profile.people.UserListMode
import com.example.linkup.feature.profile.people.UserListScreen
import com.example.linkup.feature.profile.people.UserListViewModel
import com.example.linkup.feature.profile.view.ProfileScreen
import com.example.linkup.feature.profile.view.ProfileViewModel
import com.example.linkup.feature.reels.ReelsScreen
import com.example.linkup.feature.reels.UploadReelScreen
import kotlinx.coroutines.delay

private val bottomDestinations = setOf(
    AppRoute.FEED, AppRoute.REELS, AppRoute.DATING_DISCOVER, AppRoute.CHAT_LIST, AppRoute.PROFILE
)

/** Composition root. Configured with Hilt and AppNavigator. */
@Composable
fun LinkUpApp() {
    val repository = remember { FakeLinkUpRepository() }
    val navigator = remember { AppNavigator() }
    // Hoisted: the feed's bell badge and the notifications inbox read one instance,
    // so marking something read updates the badge without a refetch.
    val notificationsViewModel: NotificationsViewModel = hiltViewModel()
    val notificationsState by notificationsViewModel.uiState.collectAsState()
    // Hoisted so logout can clear it; otherwise this activity-scoped model would hand
    // the next account the previous user's profile.
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val friendsViewModel: FriendsViewModel = hiltViewModel()
    val friendsState by friendsViewModel.uiState.collectAsState()
    val searchViewModel: SearchViewModel = hiltViewModel()
    val userListViewModel: UserListViewModel = hiltViewModel()
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val sessionState by sessionViewModel.state.collectAsState()
    // Which user the current destination is about, restored correctly by back().
    var currentArg by remember { mutableStateOf(navigator.currentArg) }
    var current by remember { mutableStateOf(navigator.current) }
    var posts by remember { mutableStateOf(repository.feed()) }
    var selectedPost by remember { mutableStateOf<Post?>(null) }
    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }

    fun sync() { current = navigator.current; currentArg = navigator.currentArg }
    fun goTo(route: AppRoute, arg: String? = null) { navigator.goTo(route, arg); sync() }
    fun replace(route: AppRoute) { navigator.replace(route); sync() }
    fun reset(route: AppRoute) { navigator.reset(route); sync() }
    fun back() { if (navigator.back()) sync() }

    LaunchedEffect(Unit) {
        // Hold the splash briefly so a fast session check does not flash past.
        delay(650)
        sessionViewModel.check()
    }

    // A stored token that still works skips the login screen entirely.
    LaunchedEffect(sessionState) {
        if (current != AppRoute.SPLASH) return@LaunchedEffect
        when (sessionState) {
            SessionState.Checking -> Unit
            SessionState.SignedIn -> reset(AppRoute.FEED)
            SessionState.SignedOut -> replace(AppRoute.LOGIN)
        }
    }

    // Keep the badge honest whenever the user lands somewhere that shows it.
    LaunchedEffect(current) {
        if (current == AppRoute.FEED) {
            notificationsViewModel.refreshUnreadCount()
            friendsViewModel.refreshCounts()
        }
    }
    BackHandler(enabled = current !in setOf(AppRoute.SPLASH, AppRoute.LOGIN, AppRoute.FEED)) { back() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = when (current) {
            AppRoute.SPLASH -> Color(0xFF8B3DFF)
            AppRoute.REELS, AppRoute.DATING_MATCH -> Color.Black
            else -> Color(0xFFF8F7FB)
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (current in bottomDestinations) {
                // Bottom-nav Profile always means "mine", so it resets the argument.
                LinkUpBottomBar(current) { destination ->
                    if (destination != current || currentArg != null) reset(destination)
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            when (current) {
                AppRoute.SPLASH -> SplashScreen()
                AppRoute.LOGIN -> LoginScreen(
                    onLoginSuccess = { sessionViewModel.onSignedIn(); reset(AppRoute.FEED) },
                    onRegister = { goTo(AppRoute.REGISTER) }
                )
                AppRoute.REGISTER -> RegisterScreen(
                    onBack = ::back,
                    onRegistered = { sessionViewModel.onSignedIn(); reset(AppRoute.FEED) }
                )
                AppRoute.FEED -> FeedScreen(
                    repository.currentUser(), posts,
                    onCreatePost = { goTo(AppRoute.CREATE_POST) },
                    onOpenPost = { selectedPost = it; goTo(AppRoute.POST_DETAIL) },
                    onLike = { posts = repository.toggleLike(it) },
                    onProfile = { reset(AppRoute.PROFILE) },
                    onSearch = { goTo(AppRoute.SEARCH) },
                    onNotifications = { goTo(AppRoute.NOTIFICATIONS) },
                    onAi = { goTo(AppRoute.AI_CHAT) },
                    unreadNotifications = notificationsState.unreadCount,
                    onFriends = { goTo(AppRoute.FRIENDS) },
                    pendingFriendRequests = friendsState.requestCount
                )
                AppRoute.CREATE_POST -> CreatePostScreen(repository.currentUser(), ::back) { content ->
                    repository.createPost(content); posts = repository.feed(); reset(AppRoute.FEED)
                }
                AppRoute.POST_DETAIL -> PostDetailScreen(selectedPost, ::back) { posts = repository.toggleLike(it) }
                AppRoute.REELS -> ReelsScreen({ goTo(AppRoute.UPLOAD_REEL) }, { reset(AppRoute.PROFILE) })
                AppRoute.UPLOAD_REEL -> UploadReelScreen(repository.currentUser(), ::back) { reset(AppRoute.REELS) }
                AppRoute.PROFILE -> ProfileScreen(
                    onEdit = { goTo(AppRoute.EDIT_PROFILE) },
                    onSettings = { goTo(AppRoute.SETTINGS) },
                    userId = currentArg,
                    onBack = if (currentArg != null) ::back else null,
                    onOpenFollowers = { id -> goTo(AppRoute.FOLLOWERS, id) },
                    onOpenFollowing = { id -> goTo(AppRoute.FOLLOWING, id) },
                    onOpenFriends = { goTo(AppRoute.FRIENDS) },
                    viewModel = profileViewModel
                )
                AppRoute.FRIENDS -> FriendsScreen(
                    onBack = ::back,
                    onOpenProfile = { id -> goTo(AppRoute.PROFILE, id) },
                    viewModel = friendsViewModel
                )
                AppRoute.FOLLOWERS -> UserListScreen(
                    mode = UserListMode.FOLLOWERS,
                    onBack = ::back,
                    onOpenProfile = { id -> goTo(AppRoute.PROFILE, id) },
                    userId = currentArg,
                    viewModel = userListViewModel
                )
                AppRoute.FOLLOWING -> UserListScreen(
                    mode = UserListMode.FOLLOWING,
                    onBack = ::back,
                    onOpenProfile = { id -> goTo(AppRoute.PROFILE, id) },
                    userId = currentArg,
                    viewModel = userListViewModel
                )
                AppRoute.EDIT_PROFILE -> EditProfileScreen(onBack = ::back, onSaved = ::back)
                AppRoute.SEARCH -> SearchScreen(
                    onBack = ::back,
                    onOpenProfile = { id -> goTo(AppRoute.PROFILE, id) },
                    viewModel = searchViewModel
                )
                AppRoute.NOTIFICATIONS -> NotificationsScreen(
                    onBack = ::back,
                    onOpenProfile = { userId -> goTo(AppRoute.PROFILE, userId) },
                    viewModel = notificationsViewModel
                )
                AppRoute.CHAT_LIST -> ChatListRoute(
                    onOpenChat = { conv ->
                        selectedConversation = conv
                        goTo(AppRoute.CHAT_DETAIL)
                    }
                )
                AppRoute.CHAT_DETAIL -> ChatDetailRoute(
                    conversationId = selectedConversation?.id ?: "c1",
                    title = selectedConversation?.user?.name ?: "Chat",
                    onBack = ::back
                )
                AppRoute.AI_CHAT -> AiChatScreen(::back) { goTo(AppRoute.AI_CONVERSATIONS) }
                AppRoute.AI_CONVERSATIONS -> AiConversationsScreen(::back) { replace(AppRoute.AI_CHAT) }
                AppRoute.DATING_PROFILE -> DatingProfileScreen(repository.currentUser(), ::back) { reset(AppRoute.DATING_DISCOVER) }
                AppRoute.DATING_DISCOVER -> DatingDiscoverScreen(
                    { goTo(AppRoute.DATING_PROFILE) }, { goTo(AppRoute.DATING_MATCHES) }, { goTo(AppRoute.DATING_MATCH) }
                )
                AppRoute.DATING_MATCH -> DatingMatchScreen({ reset(AppRoute.CHAT_DETAIL) }, { reset(AppRoute.DATING_DISCOVER) })
                AppRoute.DATING_MATCHES -> DatingMatchesScreen(::back) { reset(AppRoute.CHAT_DETAIL) }
                AppRoute.SETTINGS -> SettingsScreen(
                    onBack = ::back,
                    onLogout = {
                        // Clear the session and every cached screen before leaving.
                        sessionViewModel.logout()
                        profileViewModel.reset()
                        notificationsViewModel.reset()
                        searchViewModel.reset()
                        userListViewModel.reset()
                        friendsViewModel.reset()
                        reset(AppRoute.LOGIN)
                    },
                    onDatingProfile = { goTo(AppRoute.DATING_PROFILE) }
                )
            }
        }
    }
}