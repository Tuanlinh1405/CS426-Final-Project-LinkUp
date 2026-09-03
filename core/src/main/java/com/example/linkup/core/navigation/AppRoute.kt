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
    FOLLOWERS,
    FOLLOWING,
    FRIENDS,
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

/**
 * Which way the last navigation went, so a transition can slide the right way.
 *
 * Going deeper should push in from the right; going back should return to the left.
 * A screen swap that is neither — logging out, switching bottom tabs — reads better
 * as a cross-fade than as a slide in an arbitrary direction.
 */
enum class NavDirection { FORWARD, BACKWARD, REPLACE }

/**
 * Route history with one argument per entry.
 *
 * The argument is what a destination is *about* — whose profile, whose follower list.
 * It has to live in the history rather than in a single variable next to it: with one
 * shared variable, walking Followers(A) → Profile(B) → Followers(B) and then pressing
 * back twice restores the Followers route but leaves it pointing at B.
 */
class AppNavigator(start: AppRoute = AppRoute.SPLASH) {
    private val history = ArrayDeque<Pair<AppRoute, String?>>()

    var current: AppRoute = start
        private set

    /** Argument for [current], or null when the destination takes none. */
    var currentArg: String? = null
        private set

    /** How the current destination was reached. Drives the screen transition. */
    var direction: NavDirection = NavDirection.REPLACE
        private set

    fun goTo(destination: AppRoute, arg: String? = null) {
        if (destination == current && arg == currentArg) return
        history.addLast(current to currentArg)
        current = destination
        currentArg = arg
        direction = NavDirection.FORWARD
    }

    fun replace(destination: AppRoute, arg: String? = null) {
        current = destination
        currentArg = arg
        direction = NavDirection.REPLACE
    }

    fun back(): Boolean {
        if (history.isEmpty()) return false
        val (route, arg) = history.removeLast()
        current = route
        currentArg = arg
        direction = NavDirection.BACKWARD
        return true
    }

    fun reset(destination: AppRoute, arg: String? = null) {
        history.clear()
        current = destination
        currentArg = arg
        direction = NavDirection.REPLACE
    }
}
