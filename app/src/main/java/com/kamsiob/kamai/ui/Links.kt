package com.kamsiob.kamai.ui

/**
 * Every outbound address the app knows, in one place.
 *
 * These are only ever opened when the user taps them. Nothing here is fetched
 * in the background, at launch, or on a schedule. See the network discipline
 * note in DECISIONS.md.
 */
object Links {

    /**
     * The canonical privacy policy. This exact address is what Google Play
     * points at, so the About row and the store listing can never drift apart.
     * The copy in the repository at PRIVACY.md is kept word for word identical
     * to this page, and the GitHub Pages address is only a pointer to it.
     */
    const val PRIVACY = "https://kamsiob.com/kam-ai-privacy.html"

    const val YOUTUBE = "https://youtube.com/@kamsiob"
    const val GITHUB = "https://github.com/kamsiob"
    const val REPOSITORY = "https://github.com/kamsiob/kam-ai"
    const val WEBSITE = "https://kamsiob.com"
    const val TELEGRAM = "https://t.me/kamsioblab"
    const val SUPPORT = "https://buymeacoffee.com/kamsiob"

    const val FEEDBACK_EMAIL = "hello@kamsiob.com"

    /** Where Report a response sends its prefilled draft. */
    const val REPORT_EMAIL = FEEDBACK_EMAIL
}
