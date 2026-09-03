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
import com.example.linkup.feature.dating.DatingMatch
import com.example.linkup.feature.dating.DatingMatchScreen
import com.example.linkup.feature.dating.DatingMatchesScreen
import com.example.linkup.feature.dating.DatingProfileScreen
import com.example.linkup.feature.dating.CandidateProfileScreen
import com.example.linkup.feature.dating.PublicProfileScreen
import com.example.linkup.feature.dating.FakeDatingRepository
import com.example.linkup.feature.dating.DatingViewModel
import com.example.linkup.feature.dating.DatingEffect
import com.example.linkup.feature.dating.SwipeDecision
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
import kotlinx.coroutines.delay

private val bottomDestinations = setOf(
    AppRoute.FEED, AppRoute.REELS, AppRoute.DATING_DISCOVER, AppRoute.CHAT_LIST, AppRoute.PROFILE
)

/** Temporary composition root. Only the integration owner should edit this routing file. */
@Composable
fun LinkUpApp() {
    val repository = remember { FakeLinkUpRepository() }
    val datingViewModel = remember { DatingViewModel(FakeDatingRepository(), repository.currentUser()) }
    val datingUiState by datingViewModel.uiState.collectAsState()
    val navigator = remember { AppNavigator() }
    var current by remember { mutableStateOf(navigator.current) }
    var posts by remember { mutableStateOf(repository.feed()) }
    var messages by remember { mutableStateOf(repository.messages()) }
    var selectedPost by remember { mutableStateOf<Post?>(null) }
    var datingMatch by remember { mutableStateOf<DatingMatch?>(null) }
    var selectedDatingCandidate by remember { mutableStateOf<com.example.linkup.feature.dating.DatingCandidate?>(null) }

    fun goTo(route: AppRoute) { navigator.goTo(route); current = navigator.current }
    fun replace(route: AppRoute) { navigator.replace(route); current = navigator.current }
    fun reset(route: AppRoute) { navigator.reset(route); current = navigator.current }
    fun back() { if (navigator.back()) current = navigator.current }

    LaunchedEffect(Unit) { delay(650); replace(AppRoute.LOGIN) }
    LaunchedEffect(datingViewModel) {
        datingViewModel.effects.collect { effect ->
            when (effect) {
                is DatingEffect.MatchCreated -> {
                    datingMatch = effect.match
                    goTo(AppRoute.DATING_MATCH)
                }
            }
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
                AppRoute.PROFILE -> ProfileScreen(repository.currentUser(), { goTo(AppRoute.EDIT_PROFILE) }, { goTo(AppRoute.SETTINGS) })
                AppRoute.EDIT_PROFILE -> EditProfileScreen(repository.currentUser(), ::back, ::back)
                AppRoute.SEARCH -> SearchScreen(::back) { reset(AppRoute.PROFILE) }
                AppRoute.NOTIFICATIONS -> NotificationsScreen(repository.notifications(), ::back) { goTo(AppRoute.POST_DETAIL) }
                AppRoute.CHAT_LIST -> ChatListScreen(repository.conversations()) { goTo(AppRoute.CHAT_DETAIL) }
                AppRoute.CHAT_DETAIL -> ChatDetailScreen(messages, ::back) { messages = repository.sendMessage(it) }
                AppRoute.AI_CHAT -> AiChatScreen(::back) { goTo(AppRoute.AI_CONVERSATIONS) }
                AppRoute.AI_CONVERSATIONS -> AiConversationsScreen(::back) { replace(AppRoute.AI_CHAT) }
                AppRoute.DATING_PROFILE -> datingUiState.profile?.let { profile ->
                    DatingProfileScreen(
                        profile = profile,
                        me = repository.currentUser(),
                        onBack = ::back,
                        onSave = datingViewModel::saveProfile,
                        onExplore = { reset(AppRoute.DATING_DISCOVER) }
                    )
                }
                AppRoute.DATING_DISCOVER -> DatingDiscoverScreen(
                    candidate = datingUiState.candidates.firstOrNull(),
                    onProfile = { goTo(AppRoute.DATING_PROFILE) },
                    onMatches = { goTo(AppRoute.DATING_MATCHES) },
                    onOpenProfile = { candidate -> selectedDatingCandidate = candidate; goTo(AppRoute.DATING_CANDIDATE_PROFILE) },
                    onPass = { datingViewModel.swipe(SwipeDecision.PASS) },
                    onLike = { datingViewModel.swipe(SwipeDecision.LIKE) },
                    onReviewPassed = datingViewModel::reviewPassedCandidates
                )
                AppRoute.DATING_CANDIDATE_PROFILE -> selectedDatingCandidate?.let { candidate ->
                    CandidateProfileScreen(
                        candidate = candidate,
                        onBack = ::back,
                        onViewProfile = { goTo(AppRoute.DATING_PUBLIC_PROFILE) },
                        onPass = { datingViewModel.swipe(SwipeDecision.PASS); reset(AppRoute.DATING_DISCOVER) },
                        onLike = { datingViewModel.swipe(SwipeDecision.LIKE); reset(AppRoute.DATING_DISCOVER) }
                    )
                }
                AppRoute.DATING_PUBLIC_PROFILE -> selectedDatingCandidate?.let { candidate ->
                    PublicProfileScreen(candidate = candidate, onBack = ::back)
                }
                AppRoute.DATING_MATCH -> DatingMatchScreen(
                    repository.currentUser(), datingMatch,
                    { reset(AppRoute.CHAT_DETAIL) }, { reset(AppRoute.DATING_DISCOVER) }
                )
                AppRoute.DATING_MATCHES -> DatingMatchesScreen(datingUiState.matches, ::back) { reset(AppRoute.CHAT_DETAIL) }
                AppRoute.SETTINGS -> SettingsScreen(::back, { reset(AppRoute.LOGIN) }, { goTo(AppRoute.DATING_PROFILE) })
            }
        }
    }
}
