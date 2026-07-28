package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.MessageEntity
import com.kamsiob.kamai.data.Role
import org.junit.Test

/**
 * A title has to be about the conversation it names (#125).
 *
 * A conversation about the Roman empire, potato storage, a broken printer and a
 * dentist appointment was titled "Capital of Australia City Name". The existing
 * checks could not catch it: the invention is neither blank nor generic, it is
 * simply about something else.
 *
 * The bar is deliberately low. One substantial word shared with the conversation
 * is not a quality standard, it is the difference between a title about this
 * conversation and a title about a different one.
 */
class TitleIsAboutTest {

    private var n = 0
    private fun msg(text: String, role: Role = Role.USER) = MessageEntity(
        id = "m${n++}",
        conversationId = "c",
        role = role,
        content = text,
        createdAt = 0L,
    )

    private val roman = listOf(
        msg("?"),
        msg("What do you want to know?", Role.ASSISTANT),
        msg("Tell me about the history of the Roman empire and how to store potatoes"),
        msg("The Roman Empire grew from a city-state.", Role.ASSISTANT),
    )

    @Test
    fun theInventedTitleIsRejected() {
        assertThat(ConversationTitler.titleIsAbout("Capital of Australia City Name", roman)).isFalse()
    }

    @Test
    fun aTitleAboutTheConversationIsKept() {
        assertThat(ConversationTitler.titleIsAbout("Roman empire and potato storage", roman)).isTrue()
        assertThat(ConversationTitler.titleIsAbout("Storing potatoes", roman)).isTrue()
    }

    @Test
    fun aTitleOfOnlyCommonWordsIsRejected() {
        // "Some things about that" shares nothing substantial with anything, and
        // is the shape a model reaches for when it has nothing to name.
        assertThat(ConversationTitler.titleIsAbout("Some things about that", roman)).isFalse()
    }

    @Test
    fun aConversationWithNothingSubstantialAcceptsAnything() {
        // Refusing every title here would be wrong: there is nothing to check
        // against, and the excerpt fallback is no better informed than the model.
        val thin = listOf(msg("ok"), msg("Sure.", Role.ASSISTANT))
        assertThat(ConversationTitler.titleIsAbout("Kettle timings", thin)).isTrue()
    }

    @Test
    fun oneCoincidenceIsNotAboutness() {
        // The exact failure. "Capital of Australia City Name" shares one word
        // with the Roman conversation, "city", and it comes from "city-state" in
        // a sentence about something else. Requiring one shared word was not
        // enough; most of the title has to be about the conversation.
        assertThat(ConversationTitler.titleIsAbout("Capital of Australia City Name", roman)).isFalse()
    }

    @Test
    fun matchingIsNotCaseOrPunctuationSensitive() {
        assertThat(ConversationTitler.titleIsAbout("POTATOES, stored", roman)).isTrue()
    }
}
