package com.kamsiob.kamai.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The way into the archive has to survive the empty state.
 *
 * The "Archived (n)" link lived only inside the conversation list, and the list is
 * replaced wholesale by the empty state when there are no active conversations. So
 * with everything archived and nothing active, every archived chat was still on the
 * device with no way to reach any of it: the app looked as though it had lost the
 * lot.
 *
 * That is reachable without doing anything unusual. "Archive old chats" exists, and
 * a long enough gap can archive everything.
 *
 * Found while clearing the list for #113: 813 conversations were deleted, the list
 * went empty, and the 41 archived ones became unreachable in the same moment.
 *
 * Structural rather than rendered, matching how the rest of the interface is held
 * here (see GoldRuleTest). It reads the branch and asserts the link is inside it,
 * which is exactly the thing that was missing.
 */
class ArchiveReachableTest {

    private fun repoFile(path: String): java.io.File =
        java.io.File("../$path").takeIf { it.exists() } ?: java.io.File(path)

    private val source: String =
        repoFile("app/src/main/java/com/kamsiob/kamai/ui/chats/ChatsScreen.kt").readText()

    /** The `conversations.isEmpty()` arm of the `when`, up to the next arm. */
    private fun emptyStateBranch(): String {
        val start = source.indexOf("conversations.isEmpty() ->")
        assertThat(start).isGreaterThan(-1)
        val end = source.indexOf("filtered.isEmpty() ->", start)
        assertThat(end).isGreaterThan(start)
        return source.substring(start, end)
    }

    @Test
    fun `the empty state offers a way into the archive`() {
        assertThat(emptyStateBranch()).contains("ArchivedLink")
    }

    @Test
    fun `and only when something is actually archived`() {
        // An "Archived (0)" link under "Nothing here yet" would be worse than no
        // link: it would say there is somewhere to go and then go nowhere.
        assertThat(emptyStateBranch()).contains("archivedCount > 0")
    }

    @Test
    fun `the link is still in the list itself`() {
        // The empty state is the case that was missing, not a replacement for the
        // ordinary one. Both have to carry it.
        assertThat(source).contains("item(key = \"archived-link\")")
    }
}
