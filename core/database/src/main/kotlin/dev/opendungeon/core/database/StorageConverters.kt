package dev.opendungeon.core.database

import androidx.room.TypeConverter
import dev.opendungeon.core.database.story.PhraseColumns
import kotlinx.serialization.json.Json

/** Short lists are stored as JSON text: they are always read whole and never queried into. */
internal class StorageConverters {

    @TypeConverter
    fun stringsToJson(values: List<String>): String = json.encodeToString(values)

    @TypeConverter
    fun jsonToStrings(text: String): List<String> = json.decodeFromString(text)

    @TypeConverter
    fun phrasesToJson(values: List<PhraseColumns>): String = json.encodeToString(values)

    @TypeConverter
    fun jsonToPhrases(text: String): List<PhraseColumns> = json.decodeFromString(text)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
