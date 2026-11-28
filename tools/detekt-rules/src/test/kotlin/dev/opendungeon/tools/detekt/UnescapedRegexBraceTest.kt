package dev.opendungeon.tools.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class UnescapedRegexBraceTest {

    private val rule = UnescapedRegexBrace(Config.empty)

    @Test
    fun `reports a lone brace in every way a literal becomes a regex`() {
        val code =
            """
            import java.util.regex.Pattern

            val a = Regex(${"\"\"\""}\{([a-z]+)}${"\"\"\""})
            val b = "\\{([a-z]+)}".toRegex()
            val c = "a}".toPattern()
            val d = Pattern.compile("{name}")
            val e = java.util.regex.Pattern.compile("x}")
            val f = Regex("x}", RegexOption.IGNORE_CASE)
            """.trimIndent()

        assertEquals(6, rule.lint(code).size)
    }

    @Test
    fun `accepts escaped braces, quantifiers and code it cannot read as a regex`() {
        val code =
            """
            const val PATTERN = "x}"

            val a = Regex(${"\"\"\""}\{([a-z]+)\}${"\"\"\""})
            val b = Regex("^sk-or-v1-[0-9a-f]{64}$")
            val c = Regex(PATTERN)
            val d = "{who}".replace("{", "")
            val e = Other.compile("x}")
            val f = listOf("}").first()
            """.trimIndent()

        assertEquals(emptyList(), rule.lint(code).map { it.message })
    }

    @Test
    fun `reads an interpolated part as plain text`() {
        val code =
            """
            fun f(key: String) = Regex("\\{${'$'}key\\}") to Regex("{${'$'}key}")
            """.trimIndent()

        assertEquals(1, rule.lint(code).size)
    }
}
