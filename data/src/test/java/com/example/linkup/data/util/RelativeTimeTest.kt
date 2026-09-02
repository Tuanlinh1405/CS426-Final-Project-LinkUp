package com.example.linkup.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM tests for the notification timestamps — no emulator, no clock mocking. */
class RelativeTimeTest {

    private val nowIso = "2026-09-02T12:00:00Z"
    private val now = RelativeTime.parseIsoToMillis(nowIso)!!

    private fun ago(millis: Long): String {
        val instant = now - millis
        // Re-encode as the ISO shape the backend sends.
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = instant
        return "%04d-%02d-%02dT%02d:%02d:%02dZ".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND)
        )
    }

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val week = 7 * day

    @Test
    fun `parses the backend timestamp including microseconds`() {
        assertNotNull(RelativeTime.parseIsoToMillis("2026-09-02T07:43:06.298220Z"))
        assertEquals(
            RelativeTime.parseIsoToMillis("2026-09-02T07:43:06Z"),
            RelativeTime.parseIsoToMillis("2026-09-02T07:43:06.298220Z")
        )
    }

    @Test
    fun `rejects values it cannot parse instead of throwing`() {
        assertNull(RelativeTime.parseIsoToMillis(""))
        assertNull(RelativeTime.parseIsoToMillis("yesterday"))
        assertNull(RelativeTime.parseIsoToMillis("02-09-2026"))
        assertEquals("", RelativeTime.format("nonsense", now))
    }

    @Test
    fun `seconds read as now`() {
        assertEquals("now", RelativeTime.format(ago(0), now))
        assertEquals("now", RelativeTime.format(ago(59_000), now))
    }

    @Test
    fun `a server clock ahead of the device still reads as now`() {
        val future = now + 10 * minute
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = future
        val iso = "%04d-%02d-%02dT%02d:%02d:%02dZ".format(
            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE), cal.get(java.util.Calendar.SECOND)
        )
        assertEquals("now", RelativeTime.format(iso, now))
    }

    @Test
    fun `minutes hours days and weeks`() {
        assertEquals("1m", RelativeTime.format(ago(minute), now))
        assertEquals("59m", RelativeTime.format(ago(59 * minute), now))
        assertEquals("1h", RelativeTime.format(ago(hour), now))
        assertEquals("23h", RelativeTime.format(ago(23 * hour), now))
        assertEquals("1d", RelativeTime.format(ago(day), now))
        assertEquals("6d", RelativeTime.format(ago(6 * day), now))
        assertEquals("1w", RelativeTime.format(ago(week), now))
        assertEquals("4w", RelativeTime.format(ago(4 * week), now))
    }

    @Test
    fun `beyond five weeks falls back to an absolute date`() {
        val label = RelativeTime.format(ago(6 * week), now)
        assertEquals("22 Jul", label)
    }

    @Test
    fun `an older year keeps the year in the label`() {
        assertEquals("2 Sep 2025", RelativeTime.format("2025-09-02T12:00:00Z", now))
    }

    @Test
    fun `buckets split at one day and one week`() {
        assertEquals(TimeBucket.NEW, RelativeTime.bucket(ago(0), now))
        assertEquals(TimeBucket.NEW, RelativeTime.bucket(ago(23 * hour), now))
        assertEquals(TimeBucket.THIS_WEEK, RelativeTime.bucket(ago(day), now))
        assertEquals(TimeBucket.THIS_WEEK, RelativeTime.bucket(ago(6 * day), now))
        assertEquals(TimeBucket.EARLIER, RelativeTime.bucket(ago(week), now))
        assertEquals(TimeBucket.EARLIER, RelativeTime.bucket("not a date", now))
    }

    @Test
    fun `bucket labels are the headings the list renders`() {
        assertEquals("New", RelativeTime.bucketLabel(TimeBucket.NEW))
        assertEquals("This week", RelativeTime.bucketLabel(TimeBucket.THIS_WEEK))
        assertEquals("Earlier", RelativeTime.bucketLabel(TimeBucket.EARLIER))
    }
}
