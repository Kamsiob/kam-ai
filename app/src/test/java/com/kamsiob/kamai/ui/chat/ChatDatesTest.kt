package com.kamsiob.kamai.ui.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Date separators inside a conversation (#39). */
class ChatDatesTest {

    // A fixed clock and a fixed zone, so none of this depends on when the suite
    // happens to run. Wednesday, 15 July 2026, half past two in the afternoon.
    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val locale = Locale.UK
    private val now = at(2026, 7, 15, 14, 30)

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun label(millis: Long) = ChatDates.label(millis, now, zone, locale)

    private fun separator(previous: Long?, current: Long) =
        ChatDates.separatorBefore(previous, current, now, zone, locale)

    @Test
    fun `today and yesterday are named`() {
        assertThat(label(at(2026, 7, 15, 9, 0))).isEqualTo("Today")
        assertThat(label(at(2026, 7, 14, 23, 59))).isEqualTo("Yesterday")
    }

    @Test
    fun `the rest of the last week is a weekday name`() {
        assertThat(label(at(2026, 7, 13))).isEqualTo("Monday")
        assertThat(label(at(2026, 7, 10))).isEqualTo("Friday")
    }

    @Test
    fun `the weekday window stops before the name becomes ambiguous`() {
        // Exactly seven days back is the same weekday as today, and "Wednesday"
        // would read as this morning.
        assertThat(label(at(2026, 7, 8))).isEqualTo("8 July")
    }

    @Test
    fun `older dates in this year drop the year`() {
        assertThat(label(at(2026, 3, 2))).isEqualTo("2 March")
    }

    @Test
    fun `dates in another year keep it`() {
        assertThat(label(at(2025, 12, 31))).isEqualTo("31 December 2025")
    }

    @Test
    fun `a conversation that all happened today has no separators`() {
        val first = at(2026, 7, 15, 9, 0)
        val second = at(2026, 7, 15, 9, 5)
        assertThat(separator(null, first)).isNull()
        assertThat(separator(first, second)).isNull()
    }

    @Test
    fun `a conversation that started earlier is dated at the top`() {
        val first = at(2026, 7, 11, 20, 0)
        assertThat(separator(null, first)).isEqualTo("Saturday")
    }

    @Test
    fun `picking a conversation up the next day separates the two days`() {
        val yesterday = at(2026, 7, 14, 22, 0)
        val today = at(2026, 7, 15, 8, 0)
        assertThat(separator(yesterday, today)).isEqualTo("Today")
    }

    @Test
    fun `two messages a minute apart across midnight still separate`() {
        // The gap is tiny and the day still changed, which is the thing being
        // reported. Comparing elapsed time instead of calendar days would miss it.
        val before = at(2026, 7, 14, 23, 59)
        val after = at(2026, 7, 15, 0, 0)
        assertThat(separator(before, after)).isEqualTo("Today")
    }

    @Test
    fun `a long gap inside one day does not separate`() {
        // The mirror image: fourteen hours apart, same day, nothing to say.
        val morning = at(2026, 7, 15, 0, 30)
        val night = at(2026, 7, 15, 14, 30)
        assertThat(separator(morning, night)).isNull()
    }

    @Test
    fun `days are counted in the reader's zone, not UTC`() {
        // Half eight yesterday evening in New York was already today in UTC. The
        // reader's own calendar is the one that decides what "Yesterday" means.
        val lastEvening = at(2026, 7, 14, 20, 30)
        assertThat(ChatDates.label(lastEvening, now, zone, locale)).isEqualTo("Yesterday")
        assertThat(ChatDates.label(lastEvening, now, ZoneId.of("UTC"), locale)).isEqualTo("Today")
    }
}
