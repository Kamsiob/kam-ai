package com.kamsiob.kamai.discover

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Whether an installed pack has been superseded (#13).
 *
 * Small enough to look obviously right and wrong in three different ways, all of
 * which would be invisible in use: a pack that never offers its improvement, a
 * pack that offers one every time the sheet opens, and version 10 being called
 * older than version 9.
 */
class PackUpdatesTest {

    private fun pack(id: String, version: Int) = PackInfo(
        id = id, name = id, description = "", moments = 10, sizeBytes = 1,
        version = version, fileName = "$id-v$version.kampack",
        downloadUrl = "https://example.invalid/$id", sha256 = "",
    )

    @Test
    fun aNewerVersionIsOffered() {
        val updatable = PackUpdates.updatable(listOf(pack("history", 2)), mapOf("history" to "1"))
        assertThat(updatable).containsExactly("history")
    }

    @Test
    fun theSameVersionIsNotOffered() {
        // The failure this prevents is a pack that re-downloads every time the
        // sheet is opened, which on a twenty megabyte pack is somebody's data.
        assertThat(PackUpdates.updatable(listOf(pack("history", 2)), mapOf("history" to "2")))
            .isEmpty()
    }

    @Test
    fun anOlderManifestNeverDowngrades() {
        assertThat(PackUpdates.updatable(listOf(pack("history", 1)), mapOf("history" to "2")))
            .isEmpty()
    }

    @Test
    fun aPackThatIsNotInstalledIsNotAnUpdate() {
        // It is a download, and the sheet already offers that. Reporting it as an
        // update would put "Update" on something the user has never had.
        assertThat(PackUpdates.updatable(listOf(pack("history", 2)), emptyMap())).isEmpty()
    }

    @Test
    fun versionsAreNumbersRatherThanText() {
        // "10" sorts before "9" as text, which would call the newest pack the
        // oldest and silently stop offering updates at version 10.
        assertThat(PackUpdates.updatable(listOf(pack("history", 10)), mapOf("history" to "9")))
            .containsExactly("history")
    }

    @Test
    fun anUnreadableInstalledVersionOffersNothing() {
        // Treated as not updatable rather than as zero. Reading it as zero would
        // make every refresh offer an update and re-download the pack forever.
        assertThat(PackUpdates.updatable(listOf(pack("history", 2)), mapOf("history" to "")))
            .isEmpty()
        assertThat(PackUpdates.updatable(listOf(pack("history", 2)), mapOf("history" to "v1")))
            .isEmpty()
    }
}
