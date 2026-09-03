package com.example.linkup.data.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM tests — no emulator, no Robolectric.
 *
 * These guard the rules the edit form uses to disable Save, and they should stay in
 * step with the backend's `ProfileValidatorTest`.
 */
class ProfileFormRulesTest {

    private val today = "2026-09-02"

    @Test
    fun `valid usernames are accepted`() {
        listOf("sarah.j", "link_up26", "abc", "a".repeat(30))
            .forEach { assertNull("expected '$it' to pass", ProfileFormRules.username(it)) }
    }

    @Test
    fun `invalid usernames are rejected`() {
        listOf("ab", "a".repeat(31), "sarah jones", ".sarah", "sarah.", "sa..rah", "", "sarah!")
            .forEach { assertNotNull("expected '$it' to fail", ProfileFormRules.username(it)) }
    }

    @Test
    fun `email must look like an address`() {
        assertNull(ProfileFormRules.email("me@linkup.dev"))
        listOf("", "me", "me@", "@linkup.dev", "me@linkup", "a b@linkup.dev")
            .forEach { assertNotNull("expected '$it' to fail", ProfileFormRules.email(it)) }
    }

    @Test
    fun `phone is optional but validated when present`() {
        assertNull(ProfileFormRules.phone(""))
        assertNull(ProfileFormRules.phone("   "))
        assertNull(ProfileFormRules.phone("+84 (912) 345-678"))
        assertNull(ProfileFormRules.phone("0912345678"))
        assertNotNull(ProfileFormRules.phone("call me"))
        assertNotNull(ProfileFormRules.phone("12345"))
        assertNotNull(ProfileFormRules.phone("1".repeat(16)))
    }

    @Test
    fun `website accepts bare domains and full urls`() {
        assertNull(ProfileFormRules.website(""))
        assertNull(ProfileFormRules.website("linkup.dev"))
        assertNull(ProfileFormRules.website("https://linkup.dev/team"))
        assertNotNull(ProfileFormRules.website("localhost"))
        assertNotNull(ProfileFormRules.website("my site.com"))
    }

    @Test
    fun `length limits match the documented maximums`() {
        assertNull(ProfileFormRules.bio("a".repeat(ProfileFormRules.MAX_BIO)))
        assertNotNull(ProfileFormRules.bio("a".repeat(ProfileFormRules.MAX_BIO + 1)))
        assertNull(ProfileFormRules.fullName("a".repeat(ProfileFormRules.MAX_FULL_NAME)))
        assertNotNull(ProfileFormRules.fullName("a".repeat(ProfileFormRules.MAX_FULL_NAME + 1)))
        assertNull(ProfileFormRules.location("a".repeat(ProfileFormRules.MAX_LOCATION)))
        assertNotNull(ProfileFormRules.location("a".repeat(ProfileFormRules.MAX_LOCATION + 1)))
    }

    @Test
    fun `birthdate accepts a real past date`() {
        assertNull(ProfileFormRules.birthdate("", today))
        assertNull(ProfileFormRules.birthdate("2000-04-29", today))
        assertNull(ProfileFormRules.birthdate("2013-09-02", today))
    }

    @Test
    fun `birthdate rejects bad formats, impossible days, the future and under-13`() {
        assertNotNull(ProfileFormRules.birthdate("29-04-2000", today))
        assertNotNull(ProfileFormRules.birthdate("2026-13-01", today))
        assertNotNull(ProfileFormRules.birthdate("2026-02-30", today))
        assertNotNull(ProfileFormRules.birthdate("2099-01-01", today))
        assertNotNull(ProfileFormRules.birthdate("2013-09-03", today))
    }

    @Test
    fun `february 29 is valid only in leap years`() {
        assertNull(ProfileFormRules.birthdate("2000-02-29", today))
        assertNotNull(ProfileFormRules.birthdate("1900-02-29", today))
        assertNotNull(ProfileFormRules.birthdate("2001-02-29", today))
    }

    @Test
    fun `error messages are human readable`() {
        assertEquals("Username is required", ProfileFormRules.username(""))
        assertEquals("At least 3 characters", ProfileFormRules.username("ab"))
    }
}
