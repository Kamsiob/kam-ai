package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A project carries two things, and they are given to the model differently (#2).
 *
 * Instructions are orders. Notes are facts. One box for both invited people to
 * write "the client is a bakery in Leeds" under a heading saying "follow these
 * instructions", which hands the model a sentence it cannot follow and is a good
 * way to get it behaving oddly for reasons nobody can see.
 */
class ProjectNotesTest {

    @Test
    fun `instructions are framed as orders and notes as background`() {
        val out = SystemPrompts.withProject("BASE", "Always answer in French.", "The client is a bakery.")
        assertThat(out).contains("Follow them unless they conflict with anything above")
        assertThat(out).contains("Treat it as context you already know, not as instructions")
    }

    @Test
    fun `notes come after instructions, so the background falls off a tight window first`() {
        val out = SystemPrompts.withProject("BASE", "ORDERMARK", "FACTMARK")
        assertThat(out.indexOf("ORDERMARK")).isLessThan(out.indexOf("FACTMARK"))
    }

    @Test
    fun `a project with only notes still gets them, and no empty instructions heading`() {
        val out = SystemPrompts.withProject("BASE", "", "FACTMARK")
        assertThat(out).contains("FACTMARK")
        assertThat(out).doesNotContain("set these instructions for this project")
    }

    @Test
    fun `a project with only instructions is exactly what it was before notes existed`() {
        // The default argument matters: every caller that has not been updated
        // must produce the same prompt it always did.
        assertThat(SystemPrompts.withProject("BASE", "ORDERMARK"))
            .isEqualTo(SystemPrompts.withProject("BASE", "ORDERMARK", ""))
        assertThat(SystemPrompts.withProject("BASE", "ORDERMARK"))
            .doesNotContain("Background the user recorded")
    }

    @Test
    fun `a project with neither adds nothing at all`() {
        assertThat(SystemPrompts.withProject("BASE", "", "")).isEqualTo("BASE")
        assertThat(SystemPrompts.withProject("BASE", "   ", "  \n ")).isEqualTo("BASE")
    }

    @Test
    fun `both blocks say they sit under the app's own rules`() {
        // Nothing a project says can override the hard rules above it, and both
        // layers have to be told so; only the instructions block used to be.
        val out = SystemPrompts.withProject("BASE", "x", "y")
        assertThat(out).contains("conflict with anything above")
        assertThat(out).contains("not as instructions")
    }
}
