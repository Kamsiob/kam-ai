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
        "When something matters, bookmark it. It lands in Follow-ups so you can check it " +
            "properly later."

    val slide3 = Slide(
        eyebrow = "Modes",
        title = "One AI, four modes",
        button = "Continue",
    )

    // The four modes, in the order the mode control shows them. Discover is not
    // among them on purpose: it is a source, with its own tab, not a mode. Listing
    // it here was part of issue #42.
    val slide3Modes = listOf(
        "General" to "Everyday questions and back-and-forth.",
        "Logic Partner" to "Argues the other side and pokes holes in your thinking.",
        "Brainstorm" to "Will not hand you ideas, it pulls them out of you.",
        "Workbench" to "Paste something in, get it rewritten, tightened, or reorganized.",
    )

    const val SLIDE3_CLOSING =
        "Modes are chosen when starting a chat and can be switched at any time. " +
            "Discover has its own tab."

    /**
     * The rest of the app, which onboarding never mentioned.
     *
     * The first three slides sold privacy, what it is good at, and the four
     * modes, and then went straight to picking a model. Somebody finishing
     * onboarding had not been told that Discover, Projects, Follow-ups, voice,
     * documents or the power-button panel exist, which is most of the product
     * (owner feedback).
     *
     * Six lines, each naming the thing and what it is for, in the order somebody
     * is likely to meet them.
     */
    val slideExtras = Slide(
        eyebrow = "The rest of it",
        title = "More than a chat box",
        button = "Continue",
    )

    val slideExtrasItems = listOf(
        "Hold the power button" to
            "Ask something without leaving what you are doing. Speak or type, and it answers " +
                "over the top.",
        "Talk instead of typing" to
            "Your voice becomes text on the phone itself, then the AI tidies the ramble into " +
                "notes or a draft.",
        "Attach a document" to
            "Give it a file and ask about what is in it. The file never leaves your phone.",
        "Discover" to
            "Short reads from Wikipedia, offline, with a quiz and a discussion held to the " +
                "passage.",
        "Projects" to
            "Keep related chats together, under instructions they all follow and notes " +
                "they all start from.",
        "Follow-ups" to
            "Bookmark anything worth checking or coming back to, from anywhere in the app.",
    )

    const val SLIDE_EXTRAS_CLOSING =
        "It also remembers the things worth remembering, and you can read or delete any of it " +
            "in Settings."

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

    /**
     * The optional additions, offered while the model downloads (#77).
     *
     * Two cards, each declinable on its own. Not stacked into one all-or-nothing
     * choice, and neither started automatically.
     */
    val slideOptional = Slide(
        eyebrow = "Optional",
        title = "Two things you can add",
        button = "Continue",
    )

    const val OPTIONAL_INTRO =
        "Both are optional and both can be added later in Settings. The model is " +
            "downloading in the background either way."

    const val VOICE_CARD_TITLE = "Speaking instead of typing"

    const val VOICE_CARD_BODY =
        "Talk and it turns your voice into text on the phone, and reads answers back " +
            "aloud. The voice can be changed later."

    const val PACKS_CARD_TITLE = "Something to read"

    const val PACKS_CARD_BODY =
        "Discover deals you a short read from an offline pack built from Wikipedia, " +
            "with a quiz and a discussion held to the passage. Packs are a few megabytes " +
            "each and live in Discover, where you can pick the topics you actually want."

    const val PACKS_CARD_ACTION = "Where to find them"

    const val SETTINGS_LINE =
        "Models, voices and packs all live in Settings if you want to change or add " +
            "anything later."

    const val SLIDE_COUNT = 7
}
