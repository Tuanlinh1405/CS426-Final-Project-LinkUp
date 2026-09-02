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
import com.example.linkup.core.designsystem.component.LinkUpBottomBar
import com.example.linkup.core.navigation.AppNavigator
import com.example.linkup.core.navigation.AppRoute
import com.example.linkup.data.model.Post
import com.example.linkup.data.repository.FakeLinkUpRepository
import com.example.linkup.feature.ai.AiChatScreen
import com.example.linkup.feature.ai.AiConversationsScreen
import com.example.linkup.feature.auth.login.LoginScreen
import com.example.linkup.feature.auth.register.RegisterScreen
import com.example.linkup.feature.auth.splash.SplashScreen
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
import com.example.linkup.feature.profile.edit.EditProfileScreen
import com.example.linkup.feature.profile.view.ProfileScreen
import com.example.linkup.feature.reels.ReelsScreen
import com.example.linkup.feature.reels.UploadReelScreen
import kotlinx.coroutines.delay

private val bottomDestinations = setOf(
    AppRoute.FEED, AppRoute.REELS, AppRoute.DATING_DISCOVER, AppRoute.CHAT_LIST, AppRoute.PROFILE
)

/** Temporary composition root. Only the integration owner should edit this routing file. */
@Composable
fun LinkUpApp() {
    val repository = remember { FakeLinkUpRepository() }
    val navigator = remember { AppNavigator() }
    var current by remember { mutableStateOf(navigator.current) }
    var posts by remember { mutableStateOf(repository.feed()) }
    var messages by remember { mutableStateOf(repository.messages()) }
    var selectedPost by remember { mutableStateOf<Post?>(null) }

    fun goTo(route: AppRoute) { navigator.goTo(route); current = navigator.current }
    fun replace(route: AppRoute) { navigator.replace(route); current = navigator.current }
    fun reset(route: AppRoute) { navigator.reset(route); current = navigator.current }
    fun back() { if (navigator.back()) current = navigator.current }

    LaunchedEffect(Unit) { delay(650); replace(AppRoute.LOGIN) }
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
                LinkUpBottomBar(current) { destination -> if (destination != current) reset(destination) }
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
                AppRoute.LOGIN -> LoginScreen(onLoginSuccess = { reset(AppRoute.FEED) }, onRegister = { goTo(AppRoute.REGISTER) })
                AppRoute.REGISTER -> RegisterScreen(onBack = ::back, onRegistered = { reset(AppRoute.FEED) })
                AppRoute.FEED -> FeedScreen(
                    repository.currentUser(), posts,
                    onCreatePost = { goTo(AppRoute.CREATE_POST) },
                    onOpenPost = { selectedPost = it; goTo(AppRoute.POST_DETAIL) },
                    onLike = { posts = repository.toggleLike(it) },
                    onProfile = { reset(AppRoute.PROFILE) },
                    onSearch = { goTo(AppRoute.SEARCH) },
                    onNotifications = { goTo(AppRoute.NOTIFICATIONS) },
                    onAi = { goTo(AppRoute.AI_CHAT) }
                )
                AppRoute.CREATE_POST -> CreatePostScreen(repository.currentUser(), ::back) { content ->
                    repository.createPost(content); posts = repository.feed(); reset(AppRoute.FEED)
                }
                AppRoute.POST_DETAIL -> PostDetailScreen(selectedPost, ::back) { posts = repository.toggleLike(it) }
                AppRoute.REELS -> ReelsScreen({ goTo(AppRoute.UPLOAD_REEL) }, { reset(AppRoute.PROFILE) })
                AppRoute.UPLOAD_REEL -> UploadReelScreen(repository.currentUser(), ::back) { reset(AppRoute.REELS) }
                AppRoute.PROFILE -> ProfileScreen(
                    onEdit = { goTo(AppRoute.EDIT_PROFILE) },
                    onSettings = { goTo(AppRoute.SETTINGS) }
                )
                AppRoute.EDIT_PROFILE -> EditProfileScreen(onBack = ::back, onSaved = ::back)
                AppRoute.SEARCH -> SearchScreen(::back) { reset(AppRoute.PROFILE) }
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
                AppRoute.SETTINGS -> SettingsScreen(::back, { reset(AppRoute.LOGIN) }, { goTo(AppRoute.DATING_PROFILE) })
            }
        }
    }
}
