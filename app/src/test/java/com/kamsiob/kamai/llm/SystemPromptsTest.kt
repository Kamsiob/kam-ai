package com.kamsiob.kamai.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The date injection is small but it fixes a bug every local model has: it states
 * a confidently wrong date. These lock in that the current date rides along with
 * every request and does not disturb the base rules.
 */
class SystemPromptsTest {

    @Test
    fun `withDate appends the given date line`() {
        val out = SystemPrompts.withDate("BASE RULES", "Monday, 3 March 2025, 9:00 AM")
        assertTrue(out.startsWith("BASE RULES"))
        assertTrue(out.contains("Monday, 3 March 2025, 9:00 AM"))
    }

    @Test
    fun `withDate keeps the base rules intact`() {
        val base = SystemPrompts.forMode(com.kamsiob.kamai.data.Mode.GENERAL)
        val out = SystemPrompts.withDate(base, "Tuesday, 1 January 2030, 12:00 PM")
        assertTrue(out.contains("You are Kam AI"))
        // The instruction not to contradict the injected date is present, so the
        // model does not argue with the one true fact we hand it.
        assertTrue(out.contains("do not contradict"))
    }

    @Test
    fun `withDate does not itself introduce an em dash`() {
        // The whole app forbids em dashes, including any text we inject.
        val out = SystemPrompts.withDate("BASE", "Friday, 5 July 2024, 6:30 PM")
        assertFalse(out.contains("—"))
    }
}
