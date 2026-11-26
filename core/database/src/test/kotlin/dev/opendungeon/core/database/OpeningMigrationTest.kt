package dev.opendungeon.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class OpeningMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OpenDungeonDatabase::class.java)

    @Test
    fun `the first line becomes the opening, and a draft on a removed step resumes on the opening`() {
        helper.createDatabase(NAME, 2).use { db ->
            db.execSQL(draft(id = "on-takes", step = "first_turn", firstLine = "I come in through the nave."))
            db.execSQL(draft(id = "elsewhere", step = "cast", firstLine = ""))
            db.execSQL(
                "INSERT INTO draft_take (id, draft_id, position, model_id, text, state) " +
                    "VALUES ('t1', 'on-takes', 0, 'acme/storyteller', 'Water to the knee.', 'written')",
            )
        }

        helper.runMigrationsAndValidate(NAME, 3, true).use { db ->
            db.query("SELECT id, step, opening FROM story_draft ORDER BY id").use { rows ->
                val read = buildList {
                    while (rows.moveToNext()) add(Triple(rows.getString(0), rows.getString(1), rows.getString(2)))
                }
                assertEquals(
                    listOf(
                        Triple("elsewhere", "cast", ""),
                        Triple("on-takes", "opening", "I come in through the nave."),
                    ),
                    read,
                )
            }
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'draft_take'").use {
                assertFalse(it.moveToFirst(), "the takes are gone")
            }
        }
    }

    private fun draft(id: String, step: String, firstLine: String) =
        "INSERT INTO story_draft (id, step, genres, limit_desire, limit_violence, first_line) " +
            "VALUES ('$id', '$step', '[]', 'close', 'felt', '$firstLine')"

    private companion object {
        const val NAME = "migration-test.db"
    }
}
