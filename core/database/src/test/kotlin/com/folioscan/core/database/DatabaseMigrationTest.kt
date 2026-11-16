package com.folioscan.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationTest {

    private val testDbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FolioScanDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `migrate 1 to 2 preserves documents and adds isFavorite and recent_searches`() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO documents (
                    id, title, type, folderId, createdAt, updatedAt, pageCount,
                    pdfPath, ocrStatus, isVault, isTrashed, trashedAt, coverThumbPath, totalSizeBytes
                ) VALUES (
                    'doc-v1', 'Tax Return 2024', 'DOCUMENT', NULL, 1000, 1000, 2,
                    NULL, 'DONE', 0, 0, NULL, NULL, 4096
                )
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            testDbName,
            2,
            true,
            FolioScanDatabase.MIGRATION_1_2
        )

        migratedDb.query("SELECT id, title, isFavorite FROM documents WHERE id = 'doc-v1'").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("Tax Return 2024", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }

        migratedDb.execSQL("INSERT INTO recent_searches (query, searchedAt) VALUES ('tax', 2000)")
        migratedDb.query("SELECT query, searchedAt FROM recent_searches").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("tax", cursor.getString(0))
            assertEquals(2000L, cursor.getLong(1))
        }
        migratedDb.close()
    }
}
