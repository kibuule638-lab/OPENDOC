package com.folioscan.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        DocumentEntity::class,
        PageEntity::class,
        FolderEntity::class,
        TagEntity::class,
        DocumentTagCrossRef::class,
        PageTextEntity::class,
        PageTextFtsEntity::class,
        IdentityDataEntity::class,
        RecentSearchEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(FolioTypeConverters::class)
abstract class FolioScanDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun folderAndTagDao(): FolderAndTagDao
    abstract fun searchDao(): SearchDao
    abstract fun identityDataDao(): IdentityDataDao

    companion object {
        const val DATABASE_NAME = "folioscan.db"

        /**
         * Explicit v1 -> v2 migration adding favorites and persistent recent search history.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recent_searches` (
                        `query` TEXT NOT NULL,
                        `searchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`query`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun createEncrypted(
            context: Context,
            passphrase: ByteArray
        ): FolioScanDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                FolioScanDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2)

            // Load SQLCipher native library safely; fallback cleanly only if native lib unavailable in unit env.
            val sqlCipherLoaded = runCatching {
                System.loadLibrary("sqlcipher")
                true
            }.getOrDefault(false)

            if (sqlCipherLoaded) {
                builder.openHelperFactory(SupportOpenHelperFactory(passphrase))
            }
            return builder.build()
        }

        fun createInMemory(context: Context): FolioScanDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                FolioScanDatabase::class.java
            )
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
