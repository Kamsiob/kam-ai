package com.kamsiob.kamai.ui.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What the line under an answer says about memory (#16, reworded by #133).
 *
 * The Memory screen answers "what does this app know about me". It cannot answer
 * "did that change this answer", which is the question somebody has when a reply
 * knows something they never said in this conversation. This line answers it,
 * and being a claim about the app's own behavior it has to be worded exactly.
 */
class MemoryNoteTest {

    @Test
    fun `one memory reads as one, not as a plural`() {
        assertThat(memoryNote(1)).isEqualTo("Included 1 thing it remembers about you")
    }

    @Test
    fun `more than one is plural`() {
        assertThat(memoryNote(2)).isEqualTo("Included 2 things it remembers about you")
        assertThat(memoryNote(7)).isEqualTo("Included 7 things it remembers about you")
    }

    @Test
    fun `it does not claim the memory was used`() {
        // #133. The app knows which memories it put in front of the model. Whether
        // the model leaned on one is not observable, so "used" was a claim it could
        // not support, and it printed it under a reply to a bereavement where the
        // stored fact was about metric units. "Included" is what actually happened.
        assertThat(memoryNote(2)).startsWith("Included")
        assertThat(memoryNote(2)).doesNotContain("Used")
        assertThat(memoryNote(2)).doesNotContain("used")
    }

    @Test
    fun `it is about this answer and not about the store`() {
        // "Remembered 2 things" would be a statement about memory; this is about
        // what happened on this reply.
        assertThat(memoryNote(2)).doesNotContain("Remembered")
    }
}
