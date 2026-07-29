package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The relevance floor (#133). Retrieval used to rank and never filter, so every
 * stored fact was injected whenever there was room, and the app reported that it
 * had used something it remembered about you under an answer about a coffee stain.
 *
 * Two halves are tested here, and the second half is the one that matters. It is
 * easy to write a condition that fires on the example you had in mind; #137 was
 * reopened because a fix was verified on one phrasing and failed on another. So
 * [isStanding] is tested against a list of things it must *not* match, assembled
 * from real memories seen during the #133 and #142 probes, and that list is longer
 * than the list of things it must match.
 */
class MemoryRelevanceFloorTest {

    private val now = 1_000_000_000_000L
    private fun item(text: String, ageDays: Long = 1) =
        MemoryRetrieval.Item(text, now - ageDays * 86_400_000L)

    // ---- the floor ----

    @Test
    fun `a memory that bears on nothing in the message is not injected`() {
        // The #113 screenshot case: an answer about a coffee stain carried two
        // personal facts and a line saying so.
        val items = listOf(
            item("has a cat called Mabel"),
            item("is learning Spanish"),
        )
        val chosen = MemoryRetrieval.select(
            items, "how do I get a coffee stain out of a rug?", now,
            budgetChars = 10_000, max = 12,
        )
        assertThat(chosen).isEmpty()
    }

    @Test
    fun `a standing fact rides along with no overlap at all`() {
        val items = listOf(
            item("works as a district nurse"),
            item("is learning Spanish"),
        )
        val chosen = MemoryRetrieval.select(
            items, "how do I get a coffee stain out of a rug?", now,
            budgetChars = 10_000, max = 12,
        )
        assertThat(chosen).containsExactly("works as a district nurse")
    }

    @Test
    fun `an overlapping memory is injected even though it is not standing`() {
        val items = listOf(item("is allergic to peanuts"))
        val chosen = MemoryRetrieval.select(
            items, "can I eat this peanut snack?", now, budgetChars = 10_000, max = 12,
        )
        assertThat(chosen).containsExactly("is allergic to peanuts")
    }

    @Test
    fun `relevant memories take the budget before standing ones`() {
        val items = listOf(
            item("lives in Leeds", ageDays = 0),            // standing, freshest
            item("is allergic to peanuts", ageDays = 90),   // relevant, oldest
        )
        val chosen = MemoryRetrieval.select(
            items, "can I eat this peanut snack?", now, budgetChars = 10_000, max = 1,
        )
        assertThat(chosen).containsExactly("is allergic to peanuts")
    }

    @Test
    fun `a standing fact that also overlaps is injected once`() {
        val items = listOf(item("lives in Leeds"))
        val chosen = MemoryRetrieval.select(
            items, "what is the weather in Leeds?", now, budgetChars = 10_000, max = 12,
        )
        assertThat(chosen).containsExactly("lives in Leeds")
    }

    @Test
    fun `the floor does not depend on the store being small`() {
        // Twelve facts, one message, one relevant fact. The old behavior injected
        // all twelve because MEMORY_LIMIT is 12 and the budget had room.
        val items = (1..11).map { item("owns a bicycle number $it") } + item("is allergic to peanuts")
        val chosen = MemoryRetrieval.select(
            items, "can I eat this peanut snack?", now, budgetChars = 10_000, max = 12,
        )
        assertThat(chosen).containsExactly("is allergic to peanuts")
    }

    // ---- what isStanding must match ----

    @Test
    fun `identity and address facts are standing`() {
        val standing = listOf(
            "name is Kam",
            "the user's name is Kam",
            "is named Kam",
            "goes by Kam",
            "prefers to be called Kam",
            "uses they/them pronouns",
            "pronouns are she/her",
            "is dyslexic",
            "uses a screen reader",
            "is hard of hearing",
            "is autistic",
        )
        standing.forEach {
            assertThat(MemoryRetrieval.isStanding(it)).isTrue()
        }
    }

    @Test
    fun `occupation residence and language are standing`() {
        val standing = listOf(
            "works as a district nurse",
            "works at a secondary school",
            "works in local government",
            "job is fixing boilers",
            "is retired",
            "is a student",
            "is studying civil engineering",
            "teaches year six",
            "lives in Leeds",
            "based in Cairo",
            "is from Alexandria",
            "speaks Arabic at home",
            "first language is Arabic",
        )
        standing.forEach {
            assertThat(MemoryRetrieval.isStanding(it)).isTrue()
        }
    }

    @Test
    fun `a preference about the answer is standing`() {
        val standing = listOf(
            "prefers plain language",
            "prefers short answers",
            "prefers metric units",
            "dislikes bullet lists",
            "does not like jargon",
            "likes concrete examples",
            "asks for direct answers without hedging",
            "prefers American spelling",
        )
        standing.forEach {
            assertThat(MemoryRetrieval.isStanding(it)).isTrue()
        }
    }

    // ---- what isStanding must NOT match, which is the point ----

    @Test
    fun `a topical fact containing a naming phrase is not standing`() {
        // The memory planted during the #133 leak probes. A contains-check on
        // "is called" would have made a rowing club into an identity fact and put
        // it in front of the model on every message forever.
        assertThat(MemoryRetrieval.isStanding("the user's rowing club is called Verity Quay")).isFalse()
        assertThat(MemoryRetrieval.isStanding("their cat is called Mabel")).isFalse()
        assertThat(MemoryRetrieval.isStanding("the street they live on is called Mill Lane")).isFalse()
        assertThat(MemoryRetrieval.isStanding("the project at work is named Halcyon")).isFalse()
    }

    @Test
    fun `a preference that is not about the answer is not standing`() {
        val topical = listOf(
            "prefers oat milk in coffee",
            "likes the colour blue",
            "dislikes coriander",
            "hates driving at night",
            "wants to visit Japan",
            "prefers cats to dogs",
            "needs new tyres before winter",
        )
        topical.forEach {
            assertThat(MemoryRetrieval.isStanding(it)).isFalse()
        }
    }

    @Test
    fun `ongoing projects and one-off facts are not standing`() {
        val topical = listOf(
            "is learning Spanish",
            "is allergic to peanuts",
            "is a vegetarian",
            "is a big cricket fan",
            "has a cat called Mabel",
            "had a coffee stain on the living room rug",
            "is renovating the kitchen",
            "owns a 2012 Golf",
            "father died in June",
            "is training for a half marathon",
        )
        topical.forEach {
            assertThat(MemoryRetrieval.isStanding(it)).isFalse()
        }
    }

    @Test
    fun `a fact about somebody else's job or home is not standing`() {
        // "works as" and "lives in" are anchored at the start of the fact for this
        // reason: the phrase appearing anywhere is not enough.
        assertThat(MemoryRetrieval.isStanding("their brother works as a electrician")).isFalse()
        assertThat(MemoryRetrieval.isStanding("the neighbour lives in the flat above")).isFalse()
        assertThat(MemoryRetrieval.isStanding("the character in the book works in advertising")).isFalse()
    }
}
