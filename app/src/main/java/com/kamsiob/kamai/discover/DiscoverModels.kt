package com.kamsiob.kamai.discover

/**
 * One moment: a cleaned Wikipedia article, read where it stands.
 *
 * [preview] is the introduction and is what a card and the reader show.
 * [passage] is the article, and is what a grounded discussion draws on, which is
 * why the two are separate fields rather than one (#13).
 */
data class Moment(
    val packId: String,
    val id: String,
    val title: String,
    val topic: String,
    /** A generous preview, several paragraphs' worth, never a teaser. */
    val preview: String,
    /** The full passage, used by the reader and to ground chat and quizzes. */
    val passage: String,
    val sourceTitle: String,
    val sourceUrl: String,
    val license: String,
)

/** A pack as described by the manifest published on the GitHub release. */
data class PackInfo(
    val id: String,
    val name: String,
    val description: String,
    val moments: Int,
    val sizeBytes: Long,
    val version: Int,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
) {
    val sizeLabel: String get() = com.kamsiob.kamai.model.formatBytes(sizeBytes)
}
