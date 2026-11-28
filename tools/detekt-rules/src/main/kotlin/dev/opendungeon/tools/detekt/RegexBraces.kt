package dev.opendungeon.tools.detekt

/**
 * Finds a brace in a regular expression that Android cannot compile.
 *
 * Android compiles `java.util.regex` patterns with ICU, which is stricter than the JVM about braces: the
 * JVM reads a lone `}` as itself, ICU rejects the whole pattern. A JVM test passes on such a pattern and
 * the app throws `PatternSyntaxException` the first time it builds it. Both engines agree on a brace that
 * is escaped, so every brace must be escaped unless it is part of the syntax: a counted quantifier such as
 * `{2}`, `{2,}` or `{2,5}`, or the braces of `\p{…}`, `\P{…}`, `\x{…}` and `\N{…}`. That holds inside a
 * character class too, where the two engines read braces differently again.
 *
 * Only braces are checked. They are the difference between the engines that a pattern meets in practice,
 * and checking them needs no regex engine at all.
 */
internal object RegexBraces {

    /** The index in [pattern] of the first brace Android cannot read, or `null` when there is none. */
    fun firstUnportable(pattern: String): Int? {
        var at = 0
        while (at < pattern.length) {
            when (pattern[at]) {
                '\\' -> at = afterEscape(pattern, at)
                '{' -> at = afterQuantifier(pattern, at) ?: return at
                '}' -> return at
                else -> at++
            }
        }
        return null
    }

    /** Where the escape starting at [at] ends: a quoted run, a braced name or code point, or one character. */
    private fun afterEscape(pattern: String, at: Int): Int {
        val kind = pattern.getOrNull(at + 1) ?: return pattern.length
        return when {
            kind == 'Q' -> pattern.indexOf("\\E", at + 2).let { if (it < 0) pattern.length else it + 2 }
            kind in BRACED_ESCAPES && pattern.getOrNull(at + 2) == '{' ->
                pattern.indexOf('}', at + 2).let { if (it < 0) pattern.length else it + 1 }
            else -> at + 2
        }
    }

    /** Where a counted quantifier opening at [at] ends, or `null` when the brace opens none. */
    private fun afterQuantifier(pattern: String, at: Int): Int? {
        var end = digitsFrom(pattern, at + 1).takeIf { it > at + 1 } ?: return null
        if (pattern.getOrNull(end) == ',') end = digitsFrom(pattern, end + 1)
        return if (pattern.getOrNull(end) == '}') end + 1 else null
    }

    private fun digitsFrom(pattern: String, at: Int): Int {
        var end = at
        while (pattern.getOrNull(end)?.isAsciiDigit() == true) end++
        return end
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    private const val BRACED_ESCAPES = "pPxN"
}
