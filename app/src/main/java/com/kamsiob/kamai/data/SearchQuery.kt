package com.kamsiob.kamai.data

/**
 * Makes a user's search text safe to put inside a SQL `LIKE` pattern.
 *
 * Search is built on `LIKE '%' || :query || '%'`, and in `LIKE` a percent sign
 * means "any run of characters" and an underscore means "any one character". The
 * query was passed straight in, so those two characters in somebody's search were
 * silently treated as wildcards rather than as themselves.
 *
 * Searching for "50%" matched every conversation. Searching for "snake_case"
 * matched "snakeXcase". Nothing failed and no error was shown; the results were
 * simply wrong, which is the kind of defect that gets blamed on the search being
 * poor rather than reported.
 *
 * Not a security hole: Room binds the parameter, so this was never injection. It
 * is a correctness bug in the one feature whose whole promise is finding what you
 * wrote.
 */
object SearchQuery {

    /**
     * Escapes `%`, `_` and the escape character itself, for use with a query
     * ending `ESCAPE '\'`.
     *
     * The backslash goes first. Escaping it after the others would double the
     * backslashes this function had just added.
     */
    fun escapeForLike(query: String): String = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
