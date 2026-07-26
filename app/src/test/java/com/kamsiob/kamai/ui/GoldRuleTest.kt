package com.kamsiob.kamai.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * DESIGN section 2: gold is reserved for saved items, locked model tiers, the
 * Support this work button, and destructive-action labels, and "must never
 * appear anywhere else" (#61).
 *
 * It had appeared in seven other places: three recording buttons, every notice
 * in a conversation, a failed download, two lock-screen errors, and an
 * over-length character counter. None of those is one of the four, and each one
 * arrived on its own, in a different file, from somebody reaching for "the
 * color that means pay attention".
 *
 * The rule is about meaning, so no test can check it properly. What this checks
 * is the thing that actually went wrong: gold spreading into files nobody
 * thought about. Adding it to a new file fails here, and the fix is either to
 * not do that or to add the file and say in the commit which of the four uses it
 * is. That is the conversation the rule wants to force.
 */
class GoldRuleTest {

    /**
     * Every file allowed to reach for gold, and why. Written out rather than
     * counted, so the reason survives.
     */
    private val allowed = mapOf(
        "OnboardingScreen.kt" to "locked tier note, and the Support this work button",
        "SettingsScreens.kt" to "Delete and Forget labels, and the Support button",
        "ModelScreen.kt" to "locked tier warnings",
        "Confirm.kt" to "destructive confirmation labels",
        "Common.kt" to "the destructive row label, and the Support button's fill",
        "FollowUpsScreen.kt" to "saved items",
        "DiscoverScreen.kt" to "the bookmark when set",
        "ChatsScreen.kt" to "Delete labels, and the saved dot",
        "ChatScreen.kt" to "the Delete menu item, and the bookmark when set",
        "ProjectsScreen.kt" to "the Delete project label",
        "OverlayActivity.kt" to "the bookmark when set",
        "SupportSignpost.kt" to "the Support this work signpost at the top of Settings",
    )

    private fun repoFile(path: String): java.io.File =
        java.io.File("../$path").takeIf { it.exists() } ?: java.io.File(path)

    @Test
    fun goldAppearsOnlyWhereItIsAllowedTo() {
        val gold = Regex("flagAmber|goldText|amberFill")
        val users = repoFile("app/src/main/java/com/kamsiob/kamai").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // The theme defines the colors; naturally it names them.
            .filterNot { it.parentFile.name == "theme" }
            .filter { gold.containsMatchIn(it.readText()) }
            .map { it.name }
            .toSortedSet()

        assertThat(users).containsExactlyElementsIn(allowed.keys.toSortedSet())
    }

    @Test
    fun theRecordingButtonsAreNotGold() {
        // Three of them, in the chat composer, the Workbench, and the overlay.
        // They were written at different times and all three reached for the same
        // wrong color, so they are pinned together: whatever a listening state
        // looks like, the three must agree.
        // The overlay names its flag `surface` rather than `recording`, hence the
        // per-file expression instead of one string for all three.
        mapOf(
            "app/src/main/java/com/kamsiob/kamai/ui/chat/ChatScreen.kt" to "recording",
            "app/src/main/java/com/kamsiob/kamai/ui/workbench/WorkbenchScreen.kt" to "recording",
            "app/src/main/java/com/kamsiob/kamai/assist/OverlayActivity.kt" to "surface",
        ).forEach { (path, flag) ->
            val source = repoFile(path).readText()
            assertThat(source).doesNotContain("$flag) colors.flagAmber")
            assertThat(source).contains("$flag) colors.tonalFill")
        }
    }
}
