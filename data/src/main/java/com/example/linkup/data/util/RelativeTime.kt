package com.example.linkup.data.util

import java.util.Calendar
import java.util.TimeZone

/** Which heading a notification belongs under. */
enum class TimeBucket { NEW, THIS_WEEK, EARLIER }

/**
 * Relative timestamps for feed-style lists.
 *
 * Written against [Calendar] rather than `java.time`, which needs API 26 or core
 * library desugaring while these modules target minSdk 24. Pure functions with an
 * injected `now`, so they are unit testable without freezing the clock.
 */
object RelativeTime {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY

    private val ISO = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})")

    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    /**
     * Parses the UTC ISO-8601 instant the backend sends.
     *
     * Fractional seconds are ignored — they never change a relative label — and any
     * unparseable value returns null rather than throwing into a list row.
     */
    fun parseIsoToMillis(iso: String): Long? {
        val match = ISO.find(iso) ?: return null
        val (y, mo, d, h, mi, s) = match.destructured
        return try {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(y.toInt(), mo.toInt() - 1, d.toInt(), h.toInt(), mi.toInt(), s.toInt())
            }.timeInMillis
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** "now", "5m", "3h", "2d", "3w", then an absolute date. */
    fun format(iso: String, nowMillis: Long): String {
        val then = parseIsoToMillis(iso) ?: return ""
        val elapsed = nowMillis - then

        // A clock skew between device and server must not render as "in 3 hours".
        if (elapsed < MINUTE) return "now"

        return when {
            elapsed < HOUR -> "${elapsed / MINUTE}m"
            elapsed < DAY -> "${elapsed / HOUR}h"
            elapsed < WEEK -> "${elapsed / DAY}d"
            elapsed < 5 * WEEK -> "${elapsed / WEEK}w"
            else -> absoluteDate(then, nowMillis)
        }
    }

    fun bucket(iso: String, nowMillis: Long): TimeBucket {
        val then = parseIsoToMillis(iso) ?: return TimeBucket.EARLIER
        val elapsed = nowMillis - then
        return when {
            elapsed < DAY -> TimeBucket.NEW
            elapsed < WEEK -> TimeBucket.THIS_WEEK
            else -> TimeBucket.EARLIER
        }
    }

    fun bucketLabel(bucket: TimeBucket): String = when (bucket) {
        TimeBucket.NEW -> "New"
        TimeBucket.THIS_WEEK -> "This week"
        TimeBucket.EARLIER -> "Earlier"
    }

    /** "2 Sep", or "2 Sep 2025" once the year differs from today's. */
    private fun absoluteDate(millis: Long, nowMillis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = MONTHS[calendar.get(Calendar.MONTH)]
        val year = calendar.get(Calendar.YEAR)
        return if (year == now.get(Calendar.YEAR)) "$day $month" else "$day $month $year"
    }
}
