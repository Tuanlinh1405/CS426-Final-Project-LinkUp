package com.linkup.service

import kotlinx.datetime.LocalDate

/** A single rejected field, surfaced to the client as `{"field": "message"}`. */
data class FieldError(val field: String, val message: String)

/**
 * Pure validation and normalisation for profile edits.
 *
 * Kept free of Ktor and Exposed types so it can be unit tested on the JVM
 * (see `ProfileValidatorTest`) without a database or a running server.
 */
object ProfileValidator {

    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9._]{3,30}$")
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PHONE_ALLOWED = Regex("^\\+?[0-9 ()\\-.]{6,24}$")
    private val DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    const val MAX_BIO = 300
    const val MAX_FULL_NAME = 100
    const val MAX_LOCATION = 120
    const val MAX_WEBSITE = 255
    const val MIN_AGE_YEARS = 13

    val ALLOWED_GENDERS = setOf("MALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY")

    /**
     * Validates a username.
     *
     * @return the normalised (lower-cased, trimmed) value, or a [FieldError].
     */
    fun username(raw: String): Result<String> {
        val value = raw.trim().lowercase()
        return when {
            value.isEmpty() -> fail("username", "Username is required")
            !USERNAME_REGEX.matches(value) ->
                fail("username", "Use 3-30 characters: letters, numbers, dots or underscores")
            value.startsWith(".") || value.endsWith(".") ->
                fail("username", "Username cannot start or end with a dot")
            value.contains("..") -> fail("username", "Username cannot contain two dots in a row")
            else -> Result.success(value)
        }
    }

    fun email(raw: String): Result<String> {
        val value = raw.trim().lowercase()
        return when {
            value.isEmpty() -> fail("email", "Email is required")
            value.length > 255 -> fail("email", "Email is too long")
            !EMAIL_REGEX.matches(value) -> fail("email", "Enter a valid email address")
            else -> Result.success(value)
        }
    }

    /** Phone is optional: a blank value clears it. Digits are kept, formatting is dropped. */
    fun phone(raw: String): Result<String?> {
        val value = raw.trim()
        if (value.isEmpty()) return Result.success(null)
        if (!PHONE_ALLOWED.matches(value)) {
            return fail("phone", "Enter a valid phone number, e.g. +84 912 345 678")
        }
        val digits = value.filter { it.isDigit() }
        if (digits.length !in 7..15) {
            return fail("phone", "Phone number must have between 7 and 15 digits")
        }
        val normalised = if (value.startsWith("+")) "+$digits" else digits
        return Result.success(normalised)
    }

    fun fullName(raw: String): Result<String?> {
        val value = raw.trim()
        if (value.isEmpty()) return Result.success(null)
        if (value.length > MAX_FULL_NAME) {
            return fail("fullName", "Name must be $MAX_FULL_NAME characters or fewer")
        }
        return Result.success(value)
    }

    fun bio(raw: String): Result<String?> {
        val value = raw.trim()
        if (value.isEmpty()) return Result.success(null)
        if (value.length > MAX_BIO) return fail("bio", "Bio must be $MAX_BIO characters or fewer")
        return Result.success(value)
    }

    fun location(raw: String): Result<String?> {
        val value = raw.trim()
        if (value.isEmpty()) return Result.success(null)
        if (value.length > MAX_LOCATION) {
            return fail("location", "Location must be $MAX_LOCATION characters or fewer")
        }
        return Result.success(value)
    }

    /** Accepts `example.com` as well as a full URL, and always stores a scheme. */
    fun website(raw: String): Result<String?> {
        val value = raw.trim()
        if (value.isEmpty()) return Result.success(null)
        val withScheme = if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "https://$value"
        }
        if (withScheme.length > MAX_WEBSITE) {
            return fail("website", "Link must be $MAX_WEBSITE characters or fewer")
        }
        val host = withScheme.substringAfter("://").substringBefore('/').substringBefore('?')
        if (host.isEmpty() || !host.contains('.') || host.startsWith('.') || host.endsWith('.')) {
            return fail("website", "Enter a valid link, e.g. linkup.dev")
        }
        if (host.any { it.isWhitespace() }) return fail("website", "Links cannot contain spaces")
        return Result.success(withScheme)
    }

    /** Blank clears the birthdate. Otherwise `yyyy-MM-dd`, in the past, and at least 13 years ago. */
    fun birthdate(raw: String, today: LocalDate): Result<LocalDate?> {
        val value = raw.trim()
        if (value.isEmpty()) return Result.success(null)
        if (!DATE_REGEX.matches(value)) return fail("birthdate", "Use the format YYYY-MM-DD")

        val parsed = try {
            LocalDate.parse(value)
        } catch (e: IllegalArgumentException) {
            return fail("birthdate", "That date does not exist")
        }
        if (parsed > today) return fail("birthdate", "Birthdate cannot be in the future")

        val thirteenthBirthday = LocalDate(parsed.year + MIN_AGE_YEARS, parsed.monthNumber, parsed.dayOfMonth)
        if (thirteenthBirthday > today) {
            return fail("birthdate", "You must be at least $MIN_AGE_YEARS years old")
        }
        return Result.success(parsed)
    }

    fun gender(raw: String): Result<String?> {
        val value = raw.trim().uppercase().replace(' ', '_')
        if (value.isEmpty()) return Result.success(null)
        if (value !in ALLOWED_GENDERS) {
            return fail("gender", "Choose one of: ${ALLOWED_GENDERS.joinToString(", ")}")
        }
        return Result.success(value)
    }

    private fun <T> fail(field: String, message: String): Result<T> =
        Result.failure(ProfileValidationException(FieldError(field, message)))
}

/** Thrown by [ProfileValidator] so routes can map a rejection back to its field. */
class ProfileValidationException(val error: FieldError) :
    IllegalArgumentException("${error.field}: ${error.message}")
