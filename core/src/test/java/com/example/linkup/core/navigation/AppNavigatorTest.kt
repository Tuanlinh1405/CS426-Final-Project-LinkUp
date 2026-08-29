package com.example.linkup.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
