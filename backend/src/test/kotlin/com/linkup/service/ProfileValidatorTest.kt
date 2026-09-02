package com.linkup.service

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs on the JVM only: no database, no emulator, no running server. */
class ProfileValidatorTest {

    private val today = LocalDate(2026, 9, 2)

    private fun errorField(result: Result<*>): String? =
        (result.exceptionOrNull() as? ProfileValidationException)?.error?.field

    // ---- username --------------------------------------------------------

    @Test
    fun `username is lower-cased and trimmed`() {
        assertEquals("sarah.j", ProfileValidator.username("  Sarah.J  ").getOrThrow())
    }

    @Test
    fun `username rejects short, spaced, and edge-dotted values`() {
        listOf("ab", "sarah jones", ".sarah", "sarah.", "sa..rah", "", "a".repeat(31))
            .forEach { assertEquals("username", errorField(ProfileValidator.username(it)), "for input '$it'") }
    }

    @Test
    fun `username allows underscores digits and dots`() {
        assertEquals("link_up.26", ProfileValidator.username("link_up.26").getOrThrow())
    }

    // ---- email -----------------------------------------------------------

    @Test
    fun `email is normalised`() {
        assertEquals("me@linkup.dev", ProfileValidator.email(" ME@LinkUp.dev ").getOrThrow())
    }

    @Test
    fun `email rejects malformed values`() {
        listOf("me", "me@", "@linkup.dev", "me@linkup", "me @linkup.dev", "")
            .forEach { assertEquals("email", errorField(ProfileValidator.email(it)), "for input '$it'") }
    }

    // ---- phone -----------------------------------------------------------

    @Test
    fun `blank phone clears the field`() {
        assertNull(ProfileValidator.phone("   ").getOrThrow())
    }

    @Test
    fun `phone keeps the plus and strips formatting`() {
        assertEquals("+84912345678", ProfileValidator.phone("+84 (912) 345-678").getOrThrow())
        assertEquals("0912345678", ProfileValidator.phone("091 234 5678").getOrThrow())
    }

    @Test
    fun `phone rejects letters and out-of-range digit counts`() {
        listOf("call me", "12345", "1".repeat(16))
            .forEach { assertEquals("phone", errorField(ProfileValidator.phone(it)), "for input '$it'") }
    }

    // ---- website ---------------------------------------------------------

    @Test
    fun `website gains a scheme when missing`() {
        assertEquals("https://linkup.dev", ProfileValidator.website("linkup.dev").getOrThrow())
    }

    @Test
    fun `website keeps an explicit scheme and path`() {
        assertEquals(
            "http://linkup.dev/team",
            ProfileValidator.website("http://linkup.dev/team").getOrThrow()
        )
    }

    @Test
    fun `website rejects hosts without a dot or with spaces`() {
        listOf("localhost", "my site.com", "https://.dev")
            .forEach { assertEquals("website", errorField(ProfileValidator.website(it)), "for input '$it'") }
    }

    // ---- bio, name, location --------------------------------------------

    @Test
    fun `bio is capped at its documented limit`() {
        assertTrue(ProfileValidator.bio("a".repeat(ProfileValidator.MAX_BIO)).isSuccess)
        assertEquals("bio", errorField(ProfileValidator.bio("a".repeat(ProfileValidator.MAX_BIO + 1))))
    }

    @Test
    fun `blank optional text fields clear rather than fail`() {
        assertNull(ProfileValidator.bio("").getOrThrow())
        assertNull(ProfileValidator.fullName("  ").getOrThrow())
        assertNull(ProfileValidator.location("").getOrThrow())
    }

    // ---- birthdate -------------------------------------------------------

    @Test
    fun `birthdate accepts a valid past date`() {
        assertEquals(LocalDate(2000, 4, 29), ProfileValidator.birthdate("2000-04-29", today).getOrThrow())
    }

    @Test
    fun `birthdate rejects the future, bad formats, and under-13`() {
        listOf("2030-01-01", "29-04-2000", "2026-02-30", "2020-01-01")
            .forEach { assertEquals("birthdate", errorField(ProfileValidator.birthdate(it, today)), "for input '$it'") }
    }

    @Test
    fun `birthdate accepts exactly the thirteenth birthday`() {
        assertTrue(ProfileValidator.birthdate("2013-09-02", today).isSuccess)
        assertEquals("birthdate", errorField(ProfileValidator.birthdate("2013-09-03", today)))
    }

    // ---- gender ----------------------------------------------------------

    @Test
    fun `gender is normalised to the allowed set`() {
        assertEquals("PREFER_NOT_TO_SAY", ProfileValidator.gender("prefer not to say").getOrThrow())
        assertEquals("MALE", ProfileValidator.gender(" male ").getOrThrow())
        assertNull(ProfileValidator.gender("").getOrThrow())
        assertEquals("gender", errorField(ProfileValidator.gender("robot")))
    }
}
