package com.example.linkup.core.navigation

/** Every destination has one stable key, so navigation can later move to Navigation Compose. */
enum class AppRoute {
    SPLASH,
    LOGIN,
    REGISTER,
    FEED,
    CREATE_POST,
    POST_DETAIL,
    REELS,
    UPLOAD_REEL,
    PROFILE,
    EDIT_PROFILE,
    SEARCH,
    NOTIFICATIONS,
    CHAT_LIST,
    CHAT_DETAIL,
    AI_CHAT,
    AI_CONVERSATIONS,
    DATING_PROFILE,
    DATING_DISCOVER,
    DATING_MATCH,
    DATING_MATCHES,
    SETTINGS
}

class AppNavigator(start: AppRoute = AppRoute.SPLASH) {
    private val history = ArrayDeque<AppRoute>()
    var current: AppRoute = start
        private set

    fun goTo(destination: AppRoute) {
        if (destination == current) return
        history.addLast(current)
        current = destination
    }

    fun replace(destination: AppRoute) {
        current = destination
    }

    fun back(): Boolean {
        if (history.isEmpty()) return false
        current = history.removeLast()
        return true
    }

    fun reset(destination: AppRoute) {
        history.clear()
        current = destination
    }
}
