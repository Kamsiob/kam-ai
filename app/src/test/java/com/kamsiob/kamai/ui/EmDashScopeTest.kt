package com.kamsiob.kamai.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The no-em-dash rule, and the scope it actually has.
 *
 * The rule is about what a person reads: interface copy, empty states, notices,
 * onboarding, help, error messages, and the documents a human opens. It is not
 * about code. Source, identifiers, regular expressions, parsing logic, test
 * fixtures and sample data are all outside it, and nothing functional should ever
 * be distorted to satisfy it.
 *
 * That distinction is worth a test rather than a note, because it was applied too
 * broadly once already: doc comments were rewritten to avoid a character no user
 * would ever see. This checks the rule where it applies and, by only looking at
 * string literals and documents, records where it does not.
 */
class EmDashScopeTest {

    private fun repoFile(path: String): java.io.File =
        java.io.File("../$path").takeIf { it.exists() } ?: java.io.File(path)

    private val emDash = '\u2014'

    @Test
    fun noUserFacingStringUsesAnEmDash() {
        val offenders = mutableListOf<String>()
        val literal = Regex("\"[^\"\\n]*\"")

        repoFile("app/src/main/java/com/kamsiob/kamai").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readText().lineSequence().forEachIndexed { i, line ->
                    // Comments are not user-facing and are deliberately skipped.
                    // Rewriting them to satisfy a copy rule is the over-application
                    // this test exists to prevent as much as to catch violations.
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("*") || trimmed.startsWith("//")) return@forEachIndexed
                    literal.findAll(line)
                        .filter { it.value.contains(emDash) }
                        .forEach { offenders += "${file.name}:${i + 1} ${it.value.take(60)}" }
                }
            }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun theDocumentsAPersonReadsUseNoEmDashes() {
        val offenders = listOf(
            "README.md", "MASTER_SPEC.md", "DESIGN.md", "HANDOFF.md", "WORKLIST.md",
        ).filter { name ->
            repoFile(name).let { it.exists() && it.readText().contains(emDash) }
        }

        assertThat(offenders).isEmpty()
    }

    @Test
    fun theRuleDoesNotReachIntoCode() {
        // A standing reminder in executable form. If a future version of this
        // test starts scanning comments or identifiers, this fails and says why.
        val thisTest = repoFile(
            "app/src/test/java/com/kamsiob/kamai/ui/EmDashScopeTest.kt",
        )
        val source = thisTest.readText()
        assertThat(source).contains("Comments are not user-facing")
        // The scan must be limited to quoted strings, never whole lines.
        assertThat(source).contains("literal.findAll(line)")
    }
}
