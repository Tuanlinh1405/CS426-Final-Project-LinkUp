package com.example.linkup.data.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Server timestamps are UTC ISO-8601; reading `HH:mm` straight out of the string
 * shows UTC, which is 7 hours off for local users. These helpers parse to an
 * instant first and format in the device time zone.
 */
object ChatTime {

    private val isoPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
    )

    fun parseMillis(rawIso: String?): Long? {
        if (rawIso.isNullOrBlank()) return null
        val normalized = rawIso.replace(Regex("([+-]\\d{2}):?(\\d{2})$"), "Z").let {
            if (it.endsWith("Z")) it else "${it}Z"
        }
        for (pattern in isoPatterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US)
                    .apply { timeZone = UTC; isLenient = false }
                    .parse(normalized)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    /** `HH:mm` in local time; falls back to the raw string when it cannot be parsed. */
    fun clock(rawIso: String?): String {
        val millis = parseMillis(rawIso) ?: return rawIso?.takeIf { it.isNotBlank() } ?: "Now"
        return localClock.format(Date(millis))
    }

    /**
     * Chat-list stamp: time for today, "Yesterday", weekday within the last week,
     * then a short date — what a messaging app shows next to a conversation.
     */
    fun listStamp(rawIso: String?): String {
        val millis = parseMillis(rawIso) ?: return rawIso?.takeIf { it.isNotBlank() } ?: "Now"
        val date = Date(millis)
        val days = daysAgo(millis)
        return when {
            days <= 0L -> localClock.format(date)
            days == 1L -> "Yesterday"
            days < 7L -> localWeekday.format(date)
            else -> localShortDate.format(date)
        }
    }

    /**
     * Separator label between days inside a thread: "Hôm nay", "Hôm qua", then `dd/MM/yyyy`.
     *
     * Returns null when the stamp cannot be parsed, so the caller simply omits the chip
     * instead of printing a raw ISO string across the middle of the conversation.
     */
    fun dayLabel(rawIso: String?): String? {
        val millis = parseMillis(rawIso) ?: return null
        return when (daysAgo(millis)) {
            0L -> "Hôm nay"
            1L -> "Hôm qua"
            else -> localFullDate.format(Date(millis))
        }
    }

    /** Identifies the calendar day a stamp falls on, for grouping messages under one label. */
    fun dayKey(rawIso: String?): String? {
        val millis = parseMillis(rawIso) ?: return null
        return dayKeyFormat.format(Date(millis))
    }

    private fun daysAgo(millis: Long): Long {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (millis >= startOfToday) return 0L
        return (startOfToday - millis + DAY_MS - 1) / DAY_MS
    }

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private val localClock get() = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val localWeekday get() = SimpleDateFormat("EEE", Locale.getDefault())
    private val localShortDate get() = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    private val localFullDate get() = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dayKeyFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** UTC ISO-8601 stamp for optimistic messages, matching the server format. */
    fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = UTC }
        .format(Date())
}
