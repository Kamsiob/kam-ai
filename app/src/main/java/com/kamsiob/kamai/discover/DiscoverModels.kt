package com.kamsiob.kamai.discover

/** One moment: a cleaned Wikipedia introduction, read where it stands. */
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
