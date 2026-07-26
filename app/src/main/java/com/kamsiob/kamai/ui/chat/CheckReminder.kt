package com.kamsiob.kamai.ui.chat

/**
 * When to mention that anything worth checking can be bookmarked (#84).
 *
 * The honest-limits framing lives in onboarding, which is read once and
 * forgotten, so nothing connected a doubtful answer to the bookmark at the moment
 * it would have mattered.
 *
 * The whole difficulty is the trigger, and getting it wrong turns a helpful note
 * into a disclaimer stapled to every reply. The rules chosen, and why:
 *
 * - **Only in the first few sessions.** After a handful of days somebody either
 *   knows about the bookmark or has decided they do not want it. A reminder that
 *   keeps arriving in month three is nagging.
 * - **Never twice in one session.** One is a note. Two is a campaign.
 * - **Not on the first answer of a session**, because the first answer of the
 *   day is not usually the one somebody doubts, and arriving instantly makes it
 *   read as boilerplate attached to the product rather than to the answer.
 * - **Dismissible permanently**, and the dismissal is the end of it. Somebody
 *   who has said no has said no.
 *
 * Deliberately not tied to whether the answer looks doubtful. Judging its own
 * confidence is exactly what a small model is worst at, and a reminder that
 * appeared only on answers the app thought were shaky would be making a claim
 * about the others.
 */
object CheckReminder {

    /** Sessions, counted from first launch, in which this may appear at all. */
    const val SESSIONS = 5

    /** Answers into a session before it may appear. */
    const val MIN_ANSWERS_THIS_SESSION = 2

    /**
     * @param dismissedForever the user said no once, which settles it.
     * @param sessionNumber 1 for the first ever session.
     * @param answersThisSession how many answers have finished in this session.
     * @param alreadyShownThisSession one is a note, two is a campaign.
     */
    fun shouldShow(
        dismissedForever: Boolean,
        sessionNumber: Int,
        answersThisSession: Int,
        alreadyShownThisSession: Boolean,
    ): Boolean {
        if (dismissedForever) return false
        if (alreadyShownThisSession) return false
        if (sessionNumber > SESSIONS) return false
        return answersThisSession >= MIN_ANSWERS_THIS_SESSION
    }

    /**
     * The wording.
     *
     * A note about what the app can do, not a warning about what it might have
     * got wrong. "Worth checking" leaves the judgement with the reader, where it
     * belongs, instead of the app implying it has doubts it cannot actually have.
     */
    const val TEXT = "Anything here worth checking? The bookmark saves it to Follow-ups."
}
