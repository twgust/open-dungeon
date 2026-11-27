package dev.opendungeon.data.story

import dev.opendungeon.core.database.story.PhraseColumns
import dev.opendungeon.domain.story.ContentLimits
import dev.opendungeon.domain.story.Phrase
import dev.opendungeon.domain.story.Secret

/*
 * The storage forms drafts and stories share. Enum values are written as fixed storage names rather
 * than `name`, so renaming a Kotlin constant never changes what is on disk; an unknown name falls back to
 * the safest reading.
 */

internal fun Phrase.toColumns() = PhraseColumns(text = text, own = this is Phrase.Own)

/** Blank own words cannot be built, so a blank stored phrase is read back as an offered one. */
internal fun PhraseColumns.toPhrase(): Phrase = if (own && text.isNotBlank()) Phrase.Own(text) else Phrase.Offered(text)

internal val ContentLimits.Desire.storageName: String
    get() = when (this) {
        ContentLimits.Desire.Unspoken -> "unspoken"
        ContentLimits.Desire.Close -> "close"
        ContentLimits.Desire.AllTheWay -> "all_the_way"
    }

internal val ContentLimits.Violence.storageName: String
    get() = when (this) {
        ContentLimits.Violence.Offstage -> "offstage"
        ContentLimits.Violence.Felt -> "felt"
        ContentLimits.Violence.Shown -> "shown"
    }

/** An unknown level reads as the most restrained one. */
internal fun desireOf(name: String): ContentLimits.Desire =
    ContentLimits.Desire.entries.firstOrNull { it.storageName == name } ?: ContentLimits.Desire.Unspoken

/** An unknown level reads as the most restrained one. */
internal fun violenceOf(name: String): ContentLimits.Violence =
    ContentLimits.Violence.entries.firstOrNull { it.storageName == name } ?: ContentLimits.Violence.Offstage

internal val Secret.storageName: String
    get() = when (this) {
        Secret.None -> "none"
        is Secret.Known -> KNOWN_SECRET
        is Secret.Sealed -> "sealed"
    }

internal val Secret.storedText: String?
    get() = when (this) {
        Secret.None -> null
        is Secret.Known -> text
        is Secret.Sealed -> text
    }

/** Anything but a known secret stays hidden: a secret must never leak through a misread. */
internal fun secretOf(kind: String, text: String?): Secret =
    text?.let { if (kind == KNOWN_SECRET) Secret.Known(it) else Secret.Sealed(it) } ?: Secret.None

private const val KNOWN_SECRET = "known"
