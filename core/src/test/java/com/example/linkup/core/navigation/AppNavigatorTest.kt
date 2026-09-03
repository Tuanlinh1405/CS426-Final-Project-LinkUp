package com.example.linkup.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun `goTo and back preserve screen history`() {
        val navigator = AppNavigator(AppRoute.LOGIN)
        navigator.goTo(AppRoute.REGISTER)
        navigator.goTo(AppRoute.FEED)

        assertTrue(navigator.back())
        assertEquals(AppRoute.REGISTER, navigator.current)
        assertTrue(navigator.back())
        assertEquals(AppRoute.LOGIN, navigator.current)
        assertFalse(navigator.back())
    }

    @Test
    fun `reset clears authenticated history`() {
        val navigator = AppNavigator(AppRoute.FEED)
        navigator.goTo(AppRoute.PROFILE)
        navigator.reset(AppRoute.LOGIN)

        assertEquals(AppRoute.LOGIN, navigator.current)
        assertFalse(navigator.back())
    }
}

/** Guards the argument that rides along with each history entry. */
class AppNavigatorArgumentTest {

    @Test
    fun `back restores the argument, not just the route`() {
        val navigator = AppNavigator(AppRoute.FEED)
        navigator.goTo(AppRoute.FOLLOWERS, "user-a")
        navigator.goTo(AppRoute.PROFILE, "user-b")
        navigator.goTo(AppRoute.FOLLOWERS, "user-b")

        assertTrue(navigator.back())
        assertEquals(AppRoute.PROFILE, navigator.current)
        assertEquals("user-b", navigator.currentArg)

        // The regression this exists for: this used to come back as user-b.
        assertTrue(navigator.back())
        assertEquals(AppRoute.FOLLOWERS, navigator.current)
        assertEquals("user-a", navigator.currentArg)

        assertTrue(navigator.back())
        assertEquals(AppRoute.FEED, navigator.current)
        assertNull(navigator.currentArg)
    }

    @Test
    fun `the same route with a different argument is a new entry`() {
        val navigator = AppNavigator(AppRoute.FEED)
        navigator.goTo(AppRoute.PROFILE, "user-a")
        navigator.goTo(AppRoute.PROFILE, "user-b")

        assertTrue(navigator.back())
        assertEquals("user-a", navigator.currentArg)
    }

    @Test
    fun `navigating to the identical destination is ignored`() {
        val navigator = AppNavigator(AppRoute.FEED)
        navigator.goTo(AppRoute.PROFILE, "user-a")
        navigator.goTo(AppRoute.PROFILE, "user-a")

        assertTrue(navigator.back())
        assertEquals(AppRoute.FEED, navigator.current)
        assertFalse(navigator.back())
    }

    @Test
    fun `reset drops history and its arguments`() {
        val navigator = AppNavigator(AppRoute.FEED)
        navigator.goTo(AppRoute.PROFILE, "user-a")
        navigator.reset(AppRoute.PROFILE)

        assertNull(navigator.currentArg)
        assertFalse(navigator.back())
    }

    @Test
    fun `replace swaps the destination without growing history`() {
        val navigator = AppNavigator(AppRoute.SPLASH)
        navigator.replace(AppRoute.LOGIN)

        assertEquals(AppRoute.LOGIN, navigator.current)
        assertNull(navigator.currentArg)
        assertFalse(navigator.back())
    }
}

/** The direction a transition should animate in. */
class AppNavigatorDirectionTest {

    @Test
    fun `going deeper is forward, going back is backward`() {
        val navigator = AppNavigator(AppRoute.FEED)

        navigator.goTo(AppRoute.PROFILE, "user-a")
        assertEquals(NavDirection.FORWARD, navigator.direction)

        navigator.goTo(AppRoute.SETTINGS)
        assertEquals(NavDirection.FORWARD, navigator.direction)

        navigator.back()
        assertEquals(NavDirection.BACKWARD, navigator.direction)
    }

    @Test
    fun `replace and reset are neither, so they cross-fade`() {
        val navigator = AppNavigator(AppRoute.SPLASH)

        navigator.replace(AppRoute.LOGIN)
        assertEquals(NavDirection.REPLACE, navigator.direction)

        navigator.goTo(AppRoute.REGISTER)
        navigator.reset(AppRoute.FEED)
        assertEquals(NavDirection.REPLACE, navigator.direction)
    }

    @Test
    fun `a rejected navigation leaves the direction alone`() {
        val navigator = AppNavigator(AppRoute.FEED)
        navigator.reset(AppRoute.FEED)

        // Same route and argument: ignored, so nothing moved to animate.
        navigator.goTo(AppRoute.FEED)
        assertEquals(NavDirection.REPLACE, navigator.direction)

        // And back with an empty history changes nothing either.
        assertFalse(navigator.back())
        assertEquals(NavDirection.REPLACE, navigator.direction)
    }
}
