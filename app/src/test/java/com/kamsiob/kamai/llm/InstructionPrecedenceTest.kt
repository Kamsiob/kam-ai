package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Mode
import org.junit.Test

/**
 * Guards item 15's precedence: the composed system prompt must place the app's
 * fixed rules first (they always win), then the user's system-wide instructions,
 * then project instructions, then memory. Order in the assembled text is what the
 * model reads, and each later layer is told it may not override anything above.
 */
class InstructionPrecedenceTest {

    @Test
    fun `composition order is hard rules, user, project, memory`() {
        var s = SystemPrompts.forMode(Mode.CHAT)
        s = SystemPrompts.withUserInstructions(s, "USERMARK")
        s = SystemPrompts.withProject(s, "PROJECTMARK")
        s = SystemPrompts.withMemory(s, listOf("MEMORYMARK"))

        val hardRules = s.indexOf("You are Kam AI")
        val user = s.indexOf("USERMARK")
        val project = s.indexOf("PROJECTMARK")
        val memory = s.indexOf("MEMORYMARK")

        assertThat(hardRules).isAtLeast(0)
        assertThat(hardRules).isLessThan(user)
        assertThat(user).isLessThan(project)
        assertThat(project).isLessThan(memory)
    }

    @Test
    fun `each user-provided layer is told the app's rules above win`() {
        assertThat(SystemPrompts.withUserInstructions("BASE", "x")).contains("conflict with anything above")
        assertThat(SystemPrompts.withProject("BASE", "x")).contains("conflict with anything above")
    }

    @Test
    fun `blank instructions add nothing`() {
        assertThat(SystemPrompts.withUserInstructions("BASE", "")).isEqualTo("BASE")
        assertThat(SystemPrompts.withUserInstructions("BASE", "   ")).isEqualTo("BASE")
    }
}
