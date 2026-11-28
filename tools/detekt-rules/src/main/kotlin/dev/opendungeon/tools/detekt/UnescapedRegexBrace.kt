package dev.opendungeon.tools.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Reports a regular expression, written as a string literal, with a brace that Android's ICU engine
 * rejects although the JVM accepts it (see [RegexBraces]). Such a pattern passes every JVM test and
 * throws on the device, and when it is a property of an object, the throw fails the class for good.
 *
 * It looks at the patterns given to `Regex(…)`, `….toRegex()`, `….toPattern()` and `Pattern.compile(…)`.
 * Interpolated parts are read as plain text. A pattern held in a constant is not followed.
 */
public class UnescapedRegexBrace(config: Config) :
    Rule(
        config,
        "A brace in a regex must be escaped unless it is a quantifier: Android rejects a lone `}` the JVM accepts.",
    ) {

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val pattern = patternOf(expression) ?: return
        val value = pattern.value()
        val at = RegexBraces.firstUnportable(value) ?: return
        report(
            Finding(
                Entity.from(pattern),
                "Escape the '${value[at]}' at index $at of this regex as '\\${value[at]}': " +
                    "Android's ICU engine cannot compile it, though the JVM can.",
            ),
        )
    }

    /** The string literal this call compiles as a regex, if it is one. */
    private fun patternOf(call: KtCallExpression): KtStringTemplateExpression? {
        val name = call.calleeExpression?.text ?: return null
        val qualified = call.parent as? KtDotQualifiedExpression
        val receiver = qualified?.receiverExpression.takeIf { qualified?.selectorExpression == call }
        val pattern: KtExpression? = when {
            name == REGEX && receiver == null -> call.firstArgument()
            name == COMPILE && receiver?.text in PATTERN_CLASSES -> call.firstArgument()
            name in RECEIVER_CONVERSIONS -> receiver
            else -> null
        }
        return pattern as? KtStringTemplateExpression
    }

    private fun KtCallExpression.firstArgument(): KtExpression? = valueArguments.firstOrNull()?.getArgumentExpression()

    /** The literal's text as the program sees it, each interpolated part read as one plain character. */
    private fun KtStringTemplateExpression.value(): String = entries.joinToString("") { entry ->
        when (entry) {
            is KtEscapeStringTemplateEntry -> entry.unescapedValue
            is KtLiteralStringTemplateEntry -> entry.text
            else -> INTERPOLATED
        }
    }

    private companion object {
        const val REGEX = "Regex"
        const val COMPILE = "compile"
        const val INTERPOLATED = "a"
        val PATTERN_CLASSES = setOf("Pattern", "java.util.regex.Pattern")
        val RECEIVER_CONVERSIONS = setOf("toRegex", "toPattern")
    }
}
