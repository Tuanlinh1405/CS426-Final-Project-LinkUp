package com.example.linkup.data.validation

/**
 * Client-side mirror of the backend `ProfileValidator`.
 *
 * The server stays the authority: this exists so the edit form can disable Save and
 * mark a field the moment it goes wrong, instead of waiting for a round trip.
 * Pure Kotlin with no Android or network types, so it is unit testable on the JVM.
 */
object ProfileFormRules {

    const val MAX_BIO = 300
    const val MAX_FULL_NAME = 100
    const val MAX_LOCATION = 120

    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9._]{3,30}$")
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PHONE_ALLOWED = Regex("^\\+?[0-9 ()\\-.]{6,24}$")

    /** @return an error message, or null when the value is acceptable. */
    fun username(raw: String): String? {
        val value = raw.trim()
        return when {
            value.isEmpty() -> "Username is required"
            value.length < 3 -> "At least 3 characters"
            value.length > 30 -> "At most 30 characters"
            !USERNAME_REGEX.matches(value) -> "Letters, numbers, dots and underscores only"
            value.startsWith(".") || value.endsWith(".") -> "Cannot start or end with a dot"
            value.contains("..") -> "Cannot contain two dots in a row"
            else -> null
        }
    }

    fun email(raw: String): String? {
        val value = raw.trim()
        return when {
            value.isEmpty() -> "Email is required"
            !EMAIL_REGEX.matches(value) -> "Enter a valid email address"
            else -> null
        }
    }

    /** Phone is optional, so blank is valid. */
    fun phone(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (!PHONE_ALLOWED.matches(value)) return "Enter a valid phone number"
        val digits = value.count { it.isDigit() }
        return when {
            digits < 7 -> "Too few digits"
            digits > 15 -> "Too many digits"
            else -> null
        }
    }

    fun fullName(raw: String): String? =
        if (raw.trim().length > MAX_FULL_NAME) "At most $MAX_FULL_NAME characters" else null

    fun bio(raw: String): String? =
        if (raw.trim().length > MAX_BIO) "At most $MAX_BIO characters" else null

    fun location(raw: String): String? =
        if (raw.trim().length > MAX_LOCATION) "At most $MAX_LOCATION characters" else null

    fun website(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (value.any { it.isWhitespace() }) return "Links cannot contain spaces"
        val host = value.substringAfter("://").substringBefore('/').substringBefore('?')
        return if (host.isEmpty() || !host.contains('.') || host.startsWith('.') || host.endsWith('.')) {
            "Enter a valid link, e.g. linkup.dev"
        } else {
            null
        }
    }

    /** Blank clears the birthdate; otherwise it must be a real past date, 13+ years ago. */
    fun birthdate(raw: String, todayIso: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(value)
            ?: return "Use the format YYYY-MM-DD"
        val (y, m, d) = match.destructured.toList().map { it.toInt() }
        if (m !in 1..12) return "Month must be between 01 and 12"
        if (d !in 1..daysInMonth(y, m)) return "That date does not exist"
        if (value > todayIso) return "Birthdate cannot be in the future"

        val thirteenth = "%04d-%02d-%02d".format(y + 13, m, d)
        return if (thirteenth > todayIso) "You must be at least 13 years old" else null
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (isLeapYear(year)) 29 else 28
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}
