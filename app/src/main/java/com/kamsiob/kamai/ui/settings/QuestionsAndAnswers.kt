package com.kamsiob.kamai.ui.settings

/**
 * The launch content for Questions and answers, from DESIGN.md section 10.
 *
 * The screen is titled Questions and answers, never FAQ. Interface labels use
 * plain nouns.
 */
object QuestionsAndAnswers {

    data class Entry(val question: String, val answer: String)

    val entries = listOf(
        Entry(
            "Does anything I type leave my phone?",
            "No. The AI runs on your phone, not on a server. Kam AI only touches the " +
                "internet when you ask it to, like downloading a model or a content pack, " +
                "or if you set up web search yourself.",
        ),
        Entry(
            "Why does it sometimes get facts wrong?",
            "The model is small enough to fit on a phone, so it knows less than the big " +
                "cloud AIs and can misremember things like dates and names. When an answer " +
                "matters, flag it and check later. That is exactly what Follow-ups are for.",
        ),
        Entry(
            "Why can't it make images?",
            "Making images needs a different, much heavier kind of AI model. Kam AI " +
                "sticks to text on purpose so it can run well on your phone.",
        ),
        Entry(
            "What do the model choices mean?",
            "Bigger models give better answers but need more memory. Kam AI checks how " +
                "much memory your phone has and recommends the best fit. You can switch " +
                "any time in Settings.",
        ),
        Entry(
            "What are the modes?",
            "Four ways to use the same AI. Chat is everyday back-and-forth. Logic Partner " +
                "argues the other side of your ideas. Workbench rewrites and reorganizes " +
                "text you paste in. Discover deals you something interesting to read and " +
                "talk about. Switch with the pills at the top of a chat; Discover has its " +
                "own tab.",
        ),
        Entry(
            "How do I open it with the power button?",
            "Make Kam AI your phone's assistant app: open your phone's Settings, then " +
                "Apps, then Default apps, then Digital assistant app. After that, a long " +
                "press on the power button opens the quick panel.",
        ),
        Entry(
            "How do I change the voice?",
            "Settings, then Voice. Pick a voice you like, male or female. Voices are " +
                "downloads, so you'll see the size before anything happens.",
        ),
        Entry(
            "What is a follow-up?",
            "A small flag you can put on any answer you want to double-check or dig into " +
                "later. Flagged things collect in the Follow-ups tab so nothing gets lost.",
        ),
        Entry(
            "What are content packs?",
            "Small offline bundles of short reads for Discover, built from Wikipedia. " +
                "Download the topics you want, remove them whenever. Once downloaded, they " +
                "work without internet.",
        ),
        Entry(
            "How do I move everything to a new phone?",
            "Settings, then Backup and restore. Export puts everything into one file. " +
                "Bring that file to the new phone, import it, and you're back where you " +
                "left off.",
        ),
        Entry(
            "Will there be cloud sync or accounts?",
            "No, and that's a decision, not a gap. Your conversations stay yours, on your " +
                "device. Backup and restore covers moving between phones.",
        ),
    )

    const val EMPTY_STATE = "Nothing matches. Try fewer words."

    const val CLOSING =
        "Anything not answered here: open an issue on GitHub, or write to " +
            "hello@kamsiob.com. Everything gets read."

    /** Live filtering as the user types, across both question and answer. */
    fun filter(query: String): List<Entry> {
        if (query.isBlank()) return entries
        return entries.filter {
            it.question.contains(query, ignoreCase = true) ||
                it.answer.contains(query, ignoreCase = true)
        }
    }
}

/**
 * Being considered, and Not planned. Candidate features name their real
 * constraint in one line. Nothing here carries a date or a promise.
 */
object Roadmap {

    data class Item(val title: String, val constraint: String)

    val beingConsidered = listOf(
        Item(
            "Understanding images",
            "Needs a separate vision model for every tier, which is a real size and " +
                "memory cost on top of the text model.",
        ),
        Item(
            "Searching your conversations by meaning",
            "Needs an on-device embedding model. Keyword search works today and covers " +
                "most of what people actually look for.",
        ),
        Item(
            "Files attached to a whole project",
            "Waits on the same embedding model, because a project's worth of text does " +
                "not fit in a phone model's context.",
        ),
        Item(
            "Reading scanned PDFs",
            "Needs optical character recognition, which is another model and another " +
                "download.",
        ),
        Item(
            "Spreadsheets",
            "Small models handle tables poorly enough that shipping it would be a worse " +
                "answer delivered confidently.",
        ),
    )

    val notPlanned = listOf(
        Item(
            "Cloud sync",
            "Your conversations stay on your device. Backup and restore moves them " +
                "between phones.",
        ),
        Item(
            "Accounts",
            "There is nothing to sign in to, and there will not be.",
        ),
        Item(
            "Reading your screen",
            "A deliberate no. The app should not be able to see what you are doing.",
        ),
        Item(
            "Notification access",
            "Same reason.",
        ),
        Item(
            "Companionship or roleplay",
            "Kam AI is a tool, not a character. This is a design commitment, not a " +
                "feature gap.",
        ),
    )

    const val EXPECTATION =
        "One person builds this. Everything gets read, not everything gets a reply."
}
