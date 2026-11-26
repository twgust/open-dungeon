package dev.opendungeon.core.database

import androidx.room.TypeConverter
import dev.opendungeon.core.database.model.TuningColumns
import dev.opendungeon.core.database.story.PhraseColumns
import kotlinx.serialization.json.Json

/** Short lists and a tuning are stored as JSON text: they are always read whole and never queried into. */
internal class StorageConverters {

    @TypeConverter
    fun stringsToJson(values: List<String>): String = json.encodeToString(values)

    @TypeConverter
    fun jsonToStrings(text: String): List<String> = json.decodeFromString(text)

    @TypeConverter
    fun phrasesToJson(values: List<PhraseColumns>): String = json.encodeToString(values)

    @TypeConverter
    fun jsonToPhrases(text: String): List<PhraseColumns> = json.decodeFromString(text)

    @TypeConverter
    fun tuningToJson(tuning: TuningColumns): String = json.encodeToString(tuning)

    @TypeConverter
    fun jsonToTuning(text: String): TuningColumns = json.decodeFromString(text)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
