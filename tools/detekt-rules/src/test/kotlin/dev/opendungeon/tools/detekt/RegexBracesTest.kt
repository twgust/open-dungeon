package dev.opendungeon.tools.detekt

import kotlin.test.Test
import kotlin.test.assertEquals

class RegexBracesTest {

    @Test
    fun `a lone closing brace is found, as Android rejects it`() {
        assertEquals(10, RegexBraces.firstUnportable("""\{([a-z]+)}"""))
        assertEquals(0, RegexBraces.firstUnportable("}"))
    }

    @Test
    fun `an opening brace that starts no quantifier is found`() {
        assertEquals(0, RegexBraces.firstUnportable("{name}"))
        assertEquals(1, RegexBraces.firstUnportable("a{,3}"))
        assertEquals(1, RegexBraces.firstUnportable("a{x}"))
        assertEquals(1, RegexBraces.firstUnportable("a{2"))
    }

    @Test
    fun `a brace in a character class is found too, unless it is escaped`() {
        assertEquals(1, RegexBraces.firstUnportable("[{}]"))
        assertEquals(null, RegexBraces.firstUnportable("""[\}]"""))
    }

    @Test
    fun `escaped braces, quantifiers and braced escapes are portable`() {
        listOf(
            """\{([a-z]+)\}""",
            "^sk-or-v1-[0-9a-f]{64}$",
            "a{2,}b{2,5}",
            """\p{L}+\P{Lu}\x{1F600}\N{SNOWMAN}""",
            """[\{\}]""",
            """\Q{}\E""",
            """\\\{""",
            """^\W*title\W*:\s*""",
        ).forEach { assertEquals(null, RegexBraces.firstUnportable(it), it) }
    }

    @Test
    fun `an escaped backslash does not escape the brace after it`() {
        assertEquals(2, RegexBraces.firstUnportable("""\\}"""))
    }
}
