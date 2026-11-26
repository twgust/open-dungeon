package dev.opendungeon.core.database

import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Version 3: creation ends on one opening instead of a first line and a choice between auditioned takes.
 * The first line was the start of the opening, so it becomes the opening; the takes and the choice
 * between them go. A draft that was on either removed step resumes on the opening.
 */
@DeleteTable(tableName = "draft_take")
@DeleteColumn(tableName = "story_draft", columnName = "chosen_take_id")
@RenameColumn(tableName = "story_draft", fromColumnName = "first_line", toColumnName = "opening")
internal class OpeningMigration : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("UPDATE story_draft SET step = 'opening' WHERE step IN ('first_line', 'first_turn')")
    }
}
