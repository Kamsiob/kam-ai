package com.kamsiob.kamai.llm

/**
 * Decides which stored facts a new one replaces (#16).
 *
 * Without this, memory only ever grows, and it grows contradictory. Move house
 * and the store holds both addresses; say "I no longer work there" and the store
 * holds the old job *and* a note saying you left it. Retrieval ranks the newer
 * one higher, which is a mitigation, not a fix: both still go in front of the
 * model, and a small model handed two answers to the same question will
 * confidently pick either.
 *
 * Deliberately narrow. The safe mistake here is keeping a stale fact; the unsafe
 * one is deleting a true fact somebody asked to be remembered, which they will
 * only discover the next time it fails to come up. So this fires on two clear
 * cases and stays out of the way otherwise:
 *
 * 1. **A single-valued fact restated.** Some things can only be true once at a
 *    time: where you live, what you are called, where you work. A new "lives in
 *    Manchester" replaces "lives in Leeds". A new "prefers short answers" does
 *    NOT replace "prefers plain language", because preferences are not
 *    single-valued and somebody can hold both.
 *
 * 2. **An explicit retraction.** "no longer learning Spanish" is not a durable
 *    fact worth keeping, it is an instruction to drop one. The retraction
 *    removes what it names and, when it finds it, is not itself stored.
 *
 * Everything else is added alongside what is already there, which is what memory
 * did before this existed.
 */
object MemorySupersession {

    /**
     * Facts that can only hold one value at a time, longest first so "is based
     * in" is matched before anything shorter that shares its opening.
     *
     * Written out rather than derived. Every entry here is a claim that a person
     * has exactly one of these at a time, and that claim deserves to be visible
     * and arguable rather than emerging from a similarity threshold.
     */
    private val SINGLE_VALUED = listOf(
        "is based in",
        "was born in",
        "lives in",
        "is called",
        "name is",
        "works as",
        "works at",
        "is from",
    ).sortedByDescending { it.length }

    /** Openings that mean "drop this", not "remember this". */
    private val RETRACTIONS = listOf(
        "no longer",
        "does not",
        "doesn't",
        "is not",
        "isn't",
        "stopped",
        "has stopped",
        "not any more",
        "no more",
    ).sortedByDescending { it.length }

    /**
     * Words carrying no subject matter. Shorter than the retrieval stop list on
     * purpose: this one decides whether two facts are about the same thing, and
     * dropping too much makes unrelated facts look alike.
     */
    private val FILLER = setOf("the", "a", "an", "to", "of", "in", "on", "at", "is", "as", "and")

    /** What a new fact does to the store. */
    sealed interface Verdict {
        /** Store it, and delete these. */
        data class Store(val replaces: List<String>) : Verdict

        /** Do not store it; it only existed to remove these. */
        data class RetractOnly(val removes: List<String>) : Verdict
    }

    /**
     * What should happen when [fact] arrives and [existing] is already stored.
     *
     * [existing] is the stored text of each memory, and the strings returned are
     * drawn from it verbatim, so the caller can match them back without
     * re-normalising.
     */
    fun verdict(fact: String, existing: List<String>): Verdict {
        val normalised = MemoryExtractor.normalise(fact)

        retractionTarget(normalised)?.let { target ->
            val removes = existing.filter { isAbout(target, MemoryExtractor.normalise(it)) }
            // A retraction that matches nothing is kept as an ordinary fact. It
            // may well be true and worth knowing, and throwing it away because
            // this could not find its counterpart would lose it silently.
            if (removes.isNotEmpty()) return Verdict.RetractOnly(removes)
            return Verdict.Store(emptyList())
        }

        val predicate = SINGLE_VALUED.firstOrNull { normalised.startsWith("$it ") }
            ?: return Verdict.Store(emptyList())

        val replaces = existing.filter { other ->
            val otherNormalised = MemoryExtractor.normalise(other)
            otherNormalised.startsWith("$predicate ") && otherNormalised != normalised
        }
        return Verdict.Store(replaces)
    }

    /**
     * The fact a retraction is about, with the retraction words removed, or null
     * when this is not a retraction.
     *
     * "no longer learning spanish" becomes "learning spanish". Two significant
     * words are required afterwards, so a bare "no longer" or "is not sure"
     * cannot go looking for something to delete.
     */
    private fun retractionTarget(normalised: String): String? {
        val opening = RETRACTIONS.firstOrNull {
            normalised.startsWith("$it ") || normalised.contains(" $it ")
        } ?: return null
        val rest = normalised.substringAfter(opening).trim()
        return rest.takeIf { significantWords(it).size >= 2 }
    }

    /**
     * Whether [stored] is the fact [target] retracts.
     *
     * Every significant word of the retraction has to appear in the stored fact.
     * "no longer learning spanish" finds "is learning spanish" and leaves "is
     * learning french" alone. Requiring all of them rather than most is what
     * keeps this from reaching past the thing it was aimed at.
     */
    private fun isAbout(target: String, stored: String): Boolean {
        val wanted = significantWords(target)
        if (wanted.size < 2) return false
        val have = significantWords(stored).toSet()
        return wanted.all { it in have }
    }

    private fun significantWords(text: String): List<String> =
        text.split(' ').filter { it.isNotBlank() && it !in FILLER }.map(::stem)

    /**
     * Crudest possible stemming: drop a trailing -ing, -ed, -es or -s.
     *
     * Needed because a retraction and the fact it retracts are almost never in
     * the same tense. "stopped going to the Tuesday class" is about "goes to the
     * Tuesday class", and an exact word match sees nothing in common but the
     * class.
     *
     * Both sides get the same treatment, so the stems only have to agree with
     * each other, not with English. Never shortens a word below two characters,
     * which is what stops "sing" becoming "s" and matching everything.
     */
    private fun stem(word: String): String {
        for (suffix in listOf("ing", "ed", "es", "s")) {
            if (word.length - suffix.length >= 2 && word.endsWith(suffix)) {
                return word.dropLast(suffix.length)
            }
        }
        return word
    }
}
