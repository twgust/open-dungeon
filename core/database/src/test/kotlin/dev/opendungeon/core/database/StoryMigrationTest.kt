package dev.opendungeon.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class StoryMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OpenDungeonDatabase::class.java)

    @Test
    fun `stories arrive empty, and drafts are kept as they were`() {
        helper.createDatabase(NAME, 3).use { db ->
            db.execSQL(
                "INSERT INTO story_draft (id, step, genres, limit_desire, limit_violence, opening) " +
                    "VALUES ('d', 'opening', '[]', 'close', 'felt', 'Rain falls.')",
            )
        }

        helper.runMigrationsAndValidate(NAME, 4, true).use { db ->
            db.query("SELECT id, opening FROM story_draft").use { rows ->
                rows.moveToFirst()
                assertEquals("d" to "Rain falls.", rows.getString(0) to rows.getString(1))
            }
            for (table in listOf("story", "story_character", "story_beat")) {
                db.query("SELECT COUNT(*) FROM $table").use { rows ->
                    rows.moveToFirst()
                    assertEquals(0, rows.getInt(0), "$table starts empty")
                }
            }
        }
    }

    @Test
    fun `stories arrive untitled, with everything else kept`() {
        helper.createDatabase(NAME, 4).use { db ->
            db.execSQL(
                "INSERT INTO story (id, genres, limit_desire, limit_violence, unsent_turn, updated_at) " +
                    "VALUES ('s', '[]', 'close', 'felt', 'I wait.', 7)",
            )
        }

        helper.runMigrationsAndValidate(NAME, 5, true).use { db ->
            db.query("SELECT id, title, unsent_turn, updated_at FROM story").use { rows ->
                rows.moveToFirst()
                assertEquals("s", rows.getString(0))
                assertTrue(rows.isNull(1))
                assertEquals("I wait." to 7L, rows.getString(2) to rows.getLong(3))
            }
        }
    }

    private companion object {
        const val NAME = "story-migration-test.db"
    }
}
