package com.example.linkup.data.model

data class Participant(
    val id: String,
    val username: String,
    val fullName: String?,
    val avatarUrl: String? = null,
) {
    val displayName: String
        get() = fullName?.ifBlank { username } ?: username

    val initials: String
        get() = displayName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
            .ifEmpty { "U" }
}
