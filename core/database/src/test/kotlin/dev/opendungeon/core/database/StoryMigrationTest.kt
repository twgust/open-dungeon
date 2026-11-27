package dev.opendungeon.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

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

    private companion object {
        const val NAME = "story-migration-test.db"
    }
}
