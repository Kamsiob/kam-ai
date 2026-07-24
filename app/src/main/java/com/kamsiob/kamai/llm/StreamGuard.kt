package com.kamsiob.kamai.llm

/**
 * Watches streamed output for a chat-template control marker and stops the
 * answer at it, even when the marker arrives split across several tokens.
 *
 * The old check looked at one streamed piece at a time. A model that types its
 * own turn delimiter as ordinary text produces it in fragments ("<", "start",
 * "_of", "_turn", ">"), so no single piece ever contains the whole marker and
 * nothing stopped. The fragments were then shown to the user, which is issue
 * #49: template syntax appearing inside an answer.
 *
 * So this holds back any trailing text that could still turn into a marker, and
 * releases it once it plainly cannot. In the ordinary case, where nothing looks
 * like a marker, it holds nothing back and streaming is unchanged.
 *
 * It is deliberately pure and knows nothing about the engine, so its behaviour
 * is unit tested on any machine.
 */
class StreamGuard(private val markers: List<String> = PromptBuilder.controlMarkers()) {

    /** Text safe to show now, and whether the answer should stop here. */
    data class Step(val emit: String, val stop: Boolean)

    private val held = StringBuilder()

    fun accept(piece: String): Step {
        held.append(piece)
        val text = held.toString()

        // A complete marker ends the answer. Everything before it is real text.
        val hit = markers.mapNotNull { marker ->
            text.indexOf(marker).takeIf { it >= 0 }
        }.minOrNull()
        if (hit != null) {
            val emit = text.substring(0, hit)
            held.setLength(0)
            return Step(emit, stop = true)
        }

        // Otherwise hold back only the tail that could still become a marker.
        val holdFrom = text.length - longestMarkerPrefixSuffix(text)
        val emit = text.substring(0, holdFrom)
        held.setLength(0)
        held.append(text.substring(holdFrom))
        return Step(emit, stop = false)
    }

    /** Anything still held back when generation ends was ordinary text after
     *  all, so it is shown rather than quietly dropped. */
    fun flush(): String {
        val rest = held.toString()
        held.setLength(0)
        return rest
    }

    /** The length of the longest suffix of [text] that is a proper prefix of
     *  some marker. Zero when the tail cannot become one. */
    private fun longestMarkerPrefixSuffix(text: String): Int {
        val longest = markers.maxOf { it.length } - 1
        var length = minOf(longest, text.length)
        while (length > 0) {
            val tail = text.substring(text.length - length)
            if (markers.any { it.startsWith(tail) }) return length
            length--
        }
        return 0
    }
}
