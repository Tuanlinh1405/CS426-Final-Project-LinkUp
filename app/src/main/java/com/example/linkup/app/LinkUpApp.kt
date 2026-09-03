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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.linkup.core.navigation.AppNavigator
import com.example.linkup.core.navigation.AppRoute
import com.example.linkup.core.ui.LinkUpBottomBar
import com.example.linkup.data.feed.PostRepositoryImpl
import com.example.linkup.data.feed.FeedPost
import com.example.linkup.data.repository.AuthRepositoryImpl
import com.example.linkup.data.repository.FakeLinkUpRepository
import com.example.linkup.data.network.AuthSession
import com.example.linkup.data.reels.ReelRepositoryImpl
import com.example.linkup.data.search.SearchRepositoryImpl
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import com.example.linkup.feature.ai.AiChatScreen
import com.example.linkup.feature.ai.AiConversationsScreen
import com.example.linkup.feature.auth.LoginScreen
import com.example.linkup.feature.auth.RegisterScreen
import com.example.linkup.feature.auth.SplashScreen
import com.example.linkup.feature.chat.ChatDetailScreen
import com.example.linkup.feature.chat.ChatListScreen
import com.example.linkup.feature.dating.DatingDiscoverScreen
import com.example.linkup.feature.dating.DatingMatchScreen
import com.example.linkup.feature.dating.DatingMatchesScreen
import com.example.linkup.feature.dating.DatingProfileScreen
import com.example.linkup.feature.feed.CreatePostScreen
import com.example.linkup.feature.feed.FeedScreen
import com.example.linkup.feature.feed.PostDetailScreen
import com.example.linkup.feature.more.NotificationsScreen
import com.example.linkup.feature.more.SearchScreen
import com.example.linkup.feature.more.SettingsScreen
import com.example.linkup.feature.profile.EditProfileScreen
import com.example.linkup.feature.profile.ProfileScreen
import com.example.linkup.feature.reels.ReelsScreen
import com.example.linkup.feature.reels.UploadReelScreen
import com.example.linkup.feature.reels.warmStartupReels
import kotlinx.coroutines.delay

private val bottomDestinations = setOf(
    AppRoute.FEED, AppRoute.REELS, AppRoute.DATING_DISCOVER, AppRoute.CHAT_LIST, AppRoute.PROFILE
)

/** Temporary composition root. Only the integration owner should edit this routing file. */
@Composable
fun LinkUpApp() {
    val context = LocalContext.current
    val repository = remember { FakeLinkUpRepository() }
    val authRepository = remember { AuthRepositoryImpl() }
    val reelsRepository = remember { ReelRepositoryImpl() }
    val postRepository = remember { PostRepositoryImpl() }
    val searchRepository = remember { SearchRepositoryImpl() }
    val session by AuthSession.state.collectAsState()
    DisposableEffect(reelsRepository) { onDispose { reelsRepository.close() } }
    val navigator = remember { AppNavigator() }
    var current by remember { mutableStateOf(navigator.current) }
    var messages by remember { mutableStateOf(repository.messages()) }
    var selectedPostId by remember { mutableStateOf<String?>(null) }
    var selectedPost by remember { mutableStateOf<FeedPost?>(null) }
    var selectedReelId by remember { mutableStateOf<String?>(null) }

    fun goTo(route: AppRoute) { navigator.goTo(route); current = navigator.current }
    fun replace(route: AppRoute) { navigator.replace(route); current = navigator.current }
    fun reset(route: AppRoute) { navigator.reset(route); current = navigator.current }
    fun back() { if (navigator.back()) current = navigator.current }

    LaunchedEffect(Unit) { delay(650); replace(AppRoute.LOGIN) }
    LaunchedEffect(session?.user?.id) {
        if (session?.user != null) warmStartupReels(context, reelsRepository)
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
                LinkUpBottomBar(current) { destination -> if (destination != current) {
                    if (destination == AppRoute.REELS) selectedReelId = null
                    reset(destination)
                } }
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
                AppRoute.LOGIN -> LoginScreen(authRepository, { reset(AppRoute.FEED) }, { goTo(AppRoute.REGISTER) })
                AppRoute.REGISTER -> RegisterScreen(authRepository, ::back) { reset(AppRoute.FEED) }
                AppRoute.FEED -> FeedScreen(
                    session?.user, postRepository,
                    onCreatePost = { goTo(AppRoute.CREATE_POST) },
                    onOpenPost = { selectedPost = it; selectedPostId = it.id; goTo(AppRoute.POST_DETAIL) },
                    onProfile = { reset(AppRoute.PROFILE) },
                    onSearch = { goTo(AppRoute.SEARCH) },
                    onNotifications = { goTo(AppRoute.NOTIFICATIONS) },
                    onAi = { goTo(AppRoute.AI_CHAT) },
                    onSignIn = { AuthSession.clear(); reset(AppRoute.LOGIN) },
                )
                AppRoute.CREATE_POST -> CreatePostScreen(session?.user, postRepository, ::back) { reset(AppRoute.FEED) }
                AppRoute.POST_DETAIL -> PostDetailScreen(selectedPostId, selectedPost, session?.user, postRepository, ::back) { reset(AppRoute.FEED) }
                AppRoute.REELS -> ReelsScreen(reelsRepository, session?.user, { goTo(AppRoute.UPLOAD_REEL) }, { AuthSession.clear(); reset(AppRoute.LOGIN) }, selectedReelId)
                AppRoute.UPLOAD_REEL -> UploadReelScreen(session?.user, reelsRepository, ::back) { selectedReelId = null; reset(AppRoute.REELS) }
                AppRoute.PROFILE -> ProfileScreen(repository.currentUser(), { goTo(AppRoute.EDIT_PROFILE) }, { goTo(AppRoute.SETTINGS) })
                AppRoute.EDIT_PROFILE -> EditProfileScreen(repository.currentUser(), ::back, ::back)
                AppRoute.SEARCH -> SearchScreen(
                    searchRepository,
                    onBack = ::back,
                    onOpenPost = { selectedPost = null; selectedPostId = it; goTo(AppRoute.POST_DETAIL) },
                    onOpenReel = { selectedReelId = it; reset(AppRoute.REELS) },
                )
                AppRoute.NOTIFICATIONS -> NotificationsScreen(repository.notifications(), ::back) { goTo(AppRoute.POST_DETAIL) }
                AppRoute.CHAT_LIST -> ChatListScreen(repository.conversations()) { goTo(AppRoute.CHAT_DETAIL) }
                AppRoute.CHAT_DETAIL -> ChatDetailScreen(messages, ::back) { messages = repository.sendMessage(it) }
                AppRoute.AI_CHAT -> AiChatScreen(::back) { goTo(AppRoute.AI_CONVERSATIONS) }
                AppRoute.AI_CONVERSATIONS -> AiConversationsScreen(::back) { replace(AppRoute.AI_CHAT) }
                AppRoute.DATING_PROFILE -> DatingProfileScreen(repository.currentUser(), ::back) { reset(AppRoute.DATING_DISCOVER) }
                AppRoute.DATING_DISCOVER -> DatingDiscoverScreen(
                    { goTo(AppRoute.DATING_PROFILE) }, { goTo(AppRoute.DATING_MATCHES) }, { goTo(AppRoute.DATING_MATCH) }
                )
                AppRoute.DATING_MATCH -> DatingMatchScreen({ reset(AppRoute.CHAT_DETAIL) }, { reset(AppRoute.DATING_DISCOVER) })
                AppRoute.DATING_MATCHES -> DatingMatchesScreen(::back) { reset(AppRoute.CHAT_DETAIL) }
                AppRoute.SETTINGS -> SettingsScreen(::back, { AuthSession.clear(); reset(AppRoute.LOGIN) }, { goTo(AppRoute.DATING_PROFILE) })
            }
        }
    }
}
