package com.folioscan.core.database

import android.content.Context
import com.folioscan.core.storage.KeystoreCryptoManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        cryptoManager: KeystoreCryptoManager
    ): FolioScanDatabase {
        val passphrase = cryptoManager.getOrCreateDatabasePassphrase()
        return FolioScanDatabase.createEncrypted(context, passphrase)
    }

    @Provides
    fun provideDocumentDao(db: FolioScanDatabase): DocumentDao = db.documentDao()

    @Provides
    fun providePageDao(db: FolioScanDatabase): PageDao = db.pageDao()

    @Provides
    fun provideFolderAndTagDao(db: FolioScanDatabase): FolderAndTagDao = db.folderAndTagDao()

    @Provides
    fun provideSearchDao(db: FolioScanDatabase): SearchDao = db.searchDao()

    @Provides
    fun provideIdentityDataDao(db: FolioScanDatabase): IdentityDataDao = db.identityDataDao()
}
