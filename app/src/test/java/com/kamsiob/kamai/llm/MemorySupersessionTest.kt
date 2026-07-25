package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which stored facts a new one replaces (#16).
 *
 * Memory only ever grew, and grew contradictory: move house and both addresses
 * are stored, and retrieval ranking the newer one higher still puts both in
 * front of the model. A small model handed two answers to the same question
 * picks one confidently.
 *
 * The tests are weighted towards what must NOT happen. Keeping a stale fact is
 * the safe failure; deleting a true one is not, and somebody only finds out
 * about it the next time it fails to come up.
 */
class MemorySupersessionTest {

    private fun replaces(fact: String, vararg existing: String): List<String> =
        when (val v = MemorySupersession.verdict(fact, existing.toList())) {
            is MemorySupersession.Verdict.Store -> v.replaces
            is MemorySupersession.Verdict.RetractOnly -> error("expected Store, got retraction")
        }

    private fun removes(fact: String, vararg existing: String): List<String> =
        when (val v = MemorySupersession.verdict(fact, existing.toList())) {
            is MemorySupersession.Verdict.RetractOnly -> v.removes
            is MemorySupersession.Verdict.Store -> error("expected RetractOnly, got a store")
        }

    // --- single-valued facts ---

    @Test
    fun `moving house replaces the old address`() {
        assertThat(replaces("lives in Manchester", "lives in Leeds"))
            .containsExactly("lives in Leeds")
    }

    @Test
    fun `a new job replaces the old one`() {
        assertThat(replaces("works at a bakery", "works at a bank"))
            .containsExactly("works at a bank")
    }

    @Test
    fun `restating the same single-valued fact replaces nothing`() {
        // Nothing has changed, so there is nothing to remove. Storage dedup
        // handles the duplicate separately.
        assertThat(replaces("lives in Leeds", "lives in Leeds")).isEmpty()
    }

    @Test
    fun `punctuation and case do not stop a replacement`() {
        assertThat(replaces("Lives in Manchester.", "lives in Leeds"))
            .containsExactly("lives in Leeds")
    }

    @Test
    fun `unrelated facts are left alone`() {
        assertThat(replaces("lives in Manchester", "is learning Spanish", "prefers tea"))
            .isEmpty()
    }

    @Test
    fun `two preferences can both be true, so neither replaces the other`() {
        // The mistake this whole object exists to avoid. "prefers" is not
        // single-valued and somebody can hold both of these at once.
        assertThat(replaces("prefers short answers", "prefers plain language")).isEmpty()
    }

    @Test
    fun `two projects can both be true`() {
        assertThat(replaces("is learning Spanish", "is starting a weekend bread bakery"))
            .isEmpty()
    }

    @Test
    fun `a fact that merely mentions a single-valued phrase mid-sentence is not a restatement`() {
        // Only a fact that opens with the predicate is a claim about it. This one
        // is about a book.
        assertThat(replaces("is reading a novel that lives in Leeds", "lives in Leeds")).isEmpty()
    }

    // --- retractions ---

    @Test
    fun `no longer removes the fact it names and is not itself stored`() {
        assertThat(removes("no longer learning Spanish", "is learning Spanish"))
            .containsExactly("is learning Spanish")
    }

    @Test
    fun `a retraction does not reach past the fact it names`() {
        assertThat(removes("no longer learning Spanish", "is learning Spanish", "is learning French"))
            .containsExactly("is learning Spanish")
    }

    @Test
    fun `stopped is a retraction too`() {
        assertThat(removes("stopped going to the Tuesday class", "goes to the Tuesday class"))
            .containsExactly("goes to the Tuesday class")
    }

    @Test
    fun `a retraction matching nothing is kept as an ordinary fact`() {
        // It may be true and worth knowing. Dropping it because its counterpart
        // could not be found would lose it without saying so.
        assertThat(replaces("no longer eats dairy", "lives in Leeds")).isEmpty()
    }

    @Test
    fun `a bare negation with nothing specific goes looking for nothing`() {
        // "is not sure" must not delete anything, however many stored facts
        // happen to contain "sure".
        assertThat(replaces("is not sure", "is learning Spanish")).isEmpty()
    }

    @Test
    fun `a retraction needs every one of its words, not most of them`() {
        // "no longer learning advanced Spanish" is about the advanced class, and
        // the stored fact does not say advanced.
        assertThat(replaces("no longer learning advanced Spanish", "is learning Spanish"))
            .isEmpty()
    }

    // --- nothing at all ---

    @Test
    fun `an empty store is never a problem`() {
        assertThat(replaces("lives in Leeds")).isEmpty()
        assertThat(replaces("no longer learning Spanish")).isEmpty()
    }

    @Test
    fun `an ordinary new fact replaces nothing`() {
        assertThat(replaces("is allergic to shellfish", "lives in Leeds", "prefers tea")).isEmpty()
    }
}
