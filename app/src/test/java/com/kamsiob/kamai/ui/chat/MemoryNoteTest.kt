package com.kamsiob.kamai.ui.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What the line under an answer says about memory (#16).
 *
 * The Memory screen answers "what does this app know about me". It cannot answer
 * "did that change this answer", which is the question somebody has when a reply
 * knows something they never said in this conversation. This line answers it,
 * and being a claim about the app's own behaviour it has to be worded exactly.
 */
class MemoryNoteTest {

    @Test
    fun `one memory reads as one, not as a plural`() {
        assertThat(memoryNote(1)).isEqualTo("Used 1 thing it remembers about you")
    }

    @Test
    fun `more than one is plural`() {
        assertThat(memoryNote(2)).isEqualTo("Used 2 things it remembers about you")
        assertThat(memoryNote(7)).isEqualTo("Used 7 things it remembers about you")
    }

    @Test
    fun `it says used, not remembered`() {
        // The claim is about this answer, not about the store: the app remembers
        // those things whether or not they came near this reply. "Remembered 2
        // things" would be a statement about memory; this is about the answer.
        assertThat(memoryNote(2)).startsWith("Used")
        assertThat(memoryNote(2)).doesNotContain("Remembered")
    }
}
