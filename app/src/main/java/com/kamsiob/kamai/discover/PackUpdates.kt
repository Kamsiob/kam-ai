package com.kamsiob.kamai.discover

/**
 * Which installed packs have been superseded by the manifest.
 *
 * Packs are matched by id, so an improved pack published under the same id reads
 * as already installed and reaches nobody who has the old one. That means
 * whatever pack somebody downloads on the day they install is the pack they keep
 * permanently, and every later improvement to the content misses exactly the
 * people who liked it enough to install it.
 *
 * It cost nothing to leave unbuilt while no one had the app, and it becomes
 * permanent the day someone does, which is why it is here before release rather
 * than after.
 *
 * Pure so it can be tested. The comparison is small and every part of it is a way
 * to get this wrong.
 */
object PackUpdates {

    /**
     * @param manifest what is published now.
     * @param installed pack id to the version string recorded at install.
     */
    fun updatable(manifest: List<PackInfo>, installed: Map<String, String>): Set<String> =
        manifest.mapNotNull { pack ->
            // Parsed rather than compared as text, because "10" sorts before "9"
            // as a string and would call the newest pack the oldest.
            val have = installed[pack.id]?.trim()?.toIntOrNull() ?: return@mapNotNull null
            if (pack.version > have) pack.id else null
        }.toSet()
}
