package com.kamsiob.kamai.ui.chat

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * When a conversation should say that a day has passed (#39).
 *
 * A conversation picked up again a week later read as one unbroken exchange: the
 * answer above and the question below looked like they happened together, and
 * nothing anywhere said otherwise. Chats shows a relative time per conversation,
 * but inside a conversation there was no sense of time at all.
 *
 * Pure and given its clock, so the awkward cases are testable rather than
 * something you wait until midnight or New Year to find out about.
 */
object ChatDates {

    /**
     * The separator to draw above a message, or null for no separator.
     *
     * [previous] is the message before it, or null when this is the first.
     *
     * A separator appears whenever the day changes. The first message is a
     * deliberate exception: a conversation that all happened today opens with no
     * separator at all, because "Today" at the top of today's conversation says
     * nothing. One that started earlier does get dated, which is the whole point.
     */
    fun separatorBefore(
        previous: Long?,
        current: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String? {
        val day = dayOf(current, zone)
        if (previous == null) {
            return if (day == dayOf(now, zone)) null else label(current, now, zone, locale)
        }
        if (day == dayOf(previous, zone)) return null
        return label(current, now, zone, locale)
    }

    /**
     * How a day is named.
     *
     * Today and Yesterday by name, because that is how people say them. Then the
     * weekday for the rest of the last week, which is easier to place than a
     * date. Then the date, with the year only when it is not this one.
     */
    fun label(
        millis: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val day = dayOf(millis, zone)
        val today = dayOf(now, zone)
        return when {
            day == today -> "Today"
            day == today.minusDays(1) -> "Yesterday"
            // Named weekdays only while the name is unambiguous. Seven days back
            // is last week's Friday and this week's Friday at once, so the window
            // stops one short of that.
            day.isAfter(today.minusDays(7)) && day.isBefore(today) ->
                day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            day.year == today.year -> DateTimeFormatter.ofPattern("d MMMM", locale).format(day)
            else -> DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(day)
        }
    }

    private fun dayOf(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
