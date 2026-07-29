package com.kamsiob.kamai.ui.settings

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.KamRepository
import org.junit.Test

/**
 * The memory note toggle controls display and nothing else (#144).
 *
 * **Why this is a test and not a comment.** The setting sits in the memory group,
 * one row below a control whose Off segment genuinely does stop memory being used.
 * Two things a row apart, one of which changes how a user's data is handled and one
 * of which changes what is drawn, is exactly the pair a later change couples by
 * accident: somebody tidying "the memory settings" wires the note through to
 * retrieval, and from then on a user who turned off a footnote has silently changed
 * what is sent to the model. Nothing would fail. The app would simply be doing
 * something other than what its own description says.
 *
 * That is a privacy defect wearing a preference's clothes, and it is the same shape
 * as #123, where Off gated storing and never gated using, so somebody who turned
 * memory off still had everything already stored sent with every message while the
 * screen told them nothing was remembered.
 *
 * These are source-level assertions on purpose. The coupling being guarded against
 * is structural, so what has to be pinned is that the retrieval path does not
 * mention the display key, and that is a fact about the code rather than about any
 * one execution of it. Same technique and same reasoning as
 * `InstrumentationSafetyTest`.
 */
class MemoryNoteSettingTest {

    private fun repoFile(path: String): java.io.File =
        java.io.File("../$path").takeIf { it.exists() } ?: java.io.File(path)

    private fun source(path: String): String {
        val f = repoFile(path)
        assertThat(f.exists()).isTrue()
        return f.readText()
    }

    @Test
    fun `the display key and the retrieval key are different settings`() {
        // If these ever became the same string, everything below would pass while
        // the two were fully coupled.
        assertThat(KamRepository.Keys.MEMORY_NOTE)
            .isNotEqualTo(KamRepository.Keys.MEMORY_MODE)
    }

    @Test
    fun `retrieval does not read the display setting`() {
        // relevantMemory is the one entry point to retrieval, and MemoryMode.OFF is
        // enforced inside it rather than at the call site precisely so that no
        // caller can get this wrong (#123). So the check is that this function, and
        // the selection it delegates to, never mention the note.
        val repository = source("app/src/main/java/com/kamsiob/kamai/data/KamRepository.kt")
        val retrieval = repository
            .substringAfter("suspend fun relevantMemory(")
            .substringBefore("/** Every stored memory text")

        assertThat(retrieval).doesNotContain("MEMORY_NOTE")
        assertThat(retrieval).doesNotContain("memoryNoteShown")
    }

    @Test
    fun `the selection function knows nothing about display at all`() {
        val memory = source("app/src/main/java/com/kamsiob/kamai/llm/Memory.kt")
        assertThat(memory).doesNotContain("MEMORY_NOTE")
        assertThat(memory).doesNotContain("memoryNoteShown")
        assertThat(memory).doesNotContain("showMemoryNote")
    }

    @Test
    fun `the prompt is not built from the display setting`() {
        // The other direction of the same worry: not that display reads retrieval,
        // but that prompt assembly starts branching on a display preference.
        val vm = source("app/src/main/java/com/kamsiob/kamai/ui/chat/ChatViewModel.kt")
        assertThat(vm).doesNotContain("memoryNoteShown")
        assertThat(vm).doesNotContain("showMemoryNote")
        assertThat(vm).doesNotContain("MEMORY_NOTE")

        val prompts = source("app/src/main/java/com/kamsiob/kamai/llm/SystemPrompts.kt")
        assertThat(prompts).doesNotContain("MEMORY_NOTE")
        assertThat(prompts).doesNotContain("memoryNoteShown")
    }

    @Test
    fun `the note defaults to off`() {
        // Off because the line is noise on the majority of replies. Pinned because
        // "default it off" is a decision, and a decision that lives only in a
        // comment gets reversed by somebody who thinks a feature should be visible.
        //
        // Read as the absence of the stored value rather than as a literal: the
        // accessor treats anything other than "true" as off, so a fresh install,
        // a cleared setting and a corrupt one all agree.
        val repository = source("app/src/main/java/com/kamsiob/kamai/data/KamRepository.kt")
        assertThat(repository).contains("""setting(Keys.MEMORY_NOTE) == "true"""")

        // And the state it is read into starts false, so the first frame after a
        // cold start does not flash a note before the setting loads.
        val app = source("app/src/main/java/com/kamsiob/kamai/ui/AppViewModel.kt")
        assertThat(app).contains("_memoryNoteShown = MutableStateFlow(false)")
    }

    @Test
    fun `the composable that draws the note fails closed`() {
        // A call site that forgets the parameter must show no note rather than
        // print a claim about the user's data on a surface nobody wired up.
        val chat = source("app/src/main/java/com/kamsiob/kamai/ui/chat/ChatScreen.kt")
        assertThat(chat).contains("showMemoryNote: Boolean = false")
    }
}
