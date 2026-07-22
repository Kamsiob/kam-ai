package com.kamsiob.kamai.ui.onboarding

/**
 * The five onboarding slides, word for word from DESIGN.md section 9.
 *
 * The copy lives apart from the layout so it can be checked against the design
 * document without reading through Compose, and so the Settings entry "What Kam
 * AI is for" replays exactly the same text.
 */
object OnboardingCopy {

    const val SKIP = "Skip for now"

    data class Slide(
        val eyebrow: String,
        val title: String,
        val body: String? = null,
        val button: String,
    )

    val slide1 = Slide(
        eyebrow = "How it works",
        title = "Everything happens on your phone",
        body = "Most AI apps send what you type to a company's computers. Kam AI doesn't. " +
            "The AI is downloaded onto your phone and runs right there, so your " +
            "conversations never leave it. Turn on airplane mode and it still works.",
        button = "Continue",
    )

    val slide1Chips = listOf("No characters.", "No roleplay.", "No pretend friend.")

    val slide2 = Slide(
        eyebrow = "What it's for",
        title = "What you'd actually use it for",
        button = "Continue",
    )

    val slide2GoodFor = listOf(
        "Asking about whatever's on your mind, quick or not.",
        "Writing hard messages and cleaning up drafts.",
        "Talking out a voice note, getting it back organized.",
        "Getting real pushback on your ideas.",
    )

    val slide2NotFor = listOf(
        "Obscure facts. It will get some wrong.",
        "Making images.",
        "News, scores, live anything, unless you add search.",
        "Long research reports and heavy documents.",
    )

    const val SLIDE2_CLOSING =
        "When something matters, flag it. It lands in Follow-ups so you can check it " +
            "properly later."

    val slide3 = Slide(
        eyebrow = "Modes",
        title = "One AI, four modes",
        button = "Continue",
    )

    val slide3Modes = listOf(
        "Chat" to "Everyday questions and back-and-forth.",
        "Logic Partner" to "Argues the other side and pokes holes in your thinking.",
        "Workbench" to "Paste something in, get it rewritten, tightened, or reorganized.",
        "Discover" to "Deals you something interesting to read and talk about.",
    )

    const val SLIDE3_CLOSING =
        "Switch with the pills at the top of any chat. Discover has its own tab."

    val slide4 = Slide(
        eyebrow = "Setup",
        title = "Pick a model that fits",
        button = "Download",
    )

    const val SLIDE4_CLOSING =
        "A read-aloud voice can be picked later in Settings, male or female."

    val slide5 = Slide(
        eyebrow = "What it costs",
        title = "Nothing. No catch.",
        body = "Everything is included. No locked features, no subscription, no ads, no " +
            "account to make. The code is public, and the license means it has to stay " +
            "open. That's a rule, not a promise.",
        button = "Start using Kam AI",
    )

    /**
     * The canonical support framing, used here and on the About screen. Never
     * coffee cliches, never an amount, never an ask.
     */
    const val SUPPORT_LINE =
        "Kam AI is built and carried by one person. If software made this way matters " +
            "to you, there's a place to stand behind it. Either way, it's yours."

    const val SUPPORT_BUTTON = "Support this work"

    const val SLIDE_COUNT = 5
}
