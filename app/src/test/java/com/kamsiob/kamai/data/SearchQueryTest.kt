package com.kamsiob.kamai.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Search text is put inside a SQL `LIKE` pattern, where `%` and `_` are
 * wildcards. They were passed through unescaped, so somebody searching for a
 * percentage or an underscored name got wrong results with nothing to indicate
 * it.
 */
class SearchQueryTest {

    @Test
    fun aPercentIsLookedForRatherThanMatchingEverything() {
        // "50%" used to match every conversation, because the percent sign is
        // "any run of characters" in LIKE.
        assertThat(SearchQuery.escapeForLike("50%")).isEqualTo("50\\%")
    }

    @Test
    fun anUnderscoreIsLookedForRatherThanMatchingAnyCharacter() {
        // "snake_case" used to match "snakeXcase", which matters in an app people
        // paste code into.
        assertThat(SearchQuery.escapeForLike("snake_case")).isEqualTo("snake\\_case")
    }

    @Test
    fun aBackslashIsEscapedFirst() {
        // Order matters. Escaping the backslash after the wildcards would double
        // the backslashes this function had just added, and the pattern would
        // then look for a literal backslash followed by a percent.
        assertThat(SearchQuery.escapeForLike("\\")).isEqualTo("\\\\")
        assertThat(SearchQuery.escapeForLike("\\%")).isEqualTo("\\\\\\%")
    }

    @Test
    fun ordinaryTextIsUntouched() {
        // The overwhelmingly common case has to cost nothing and change nothing.
        listOf(
            "deadlines",
            "What time does the library close?",
            "a phrase with, punctuation. and 'quotes'",
        ).forEach { assertThat(SearchQuery.escapeForLike(it)).isEqualTo(it) }
    }
}
