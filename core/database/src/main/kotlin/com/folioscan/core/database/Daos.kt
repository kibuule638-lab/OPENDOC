package com.folioscan.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.folioscan.core.model.OcrStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Transaction
    @Query("SELECT * FROM documents WHERE isVault = 0 AND isTrashed = 0 ORDER BY updatedAt DESC")
    fun observeActiveDocuments(): Flow<List<DocumentWithTags>>

    @Transaction
    @Query("SELECT * FROM documents WHERE isVault = 1 AND isTrashed = 0 ORDER BY updatedAt DESC")
    fun observeVaultDocuments(): Flow<List<DocumentWithTags>>

    @Transaction
    @Query("SELECT * FROM documents WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun observeTrashedDocuments(): Flow<List<DocumentWithTags>>

    @Transaction
    @Query("SELECT * FROM documents WHERE isVault = 0 AND isTrashed = 0 AND folderId = :folderId ORDER BY updatedAt DESC")
    fun observeDocumentsInFolder(folderId: String): Flow<List<DocumentWithTags>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun observeDocumentById(documentId: String): Flow<DocumentWithTags?>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun getDocumentById(documentId: String): DocumentWithTags?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: DocumentEntity)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameDocument(id: String, title: String, updatedAt: Long)

    @Query("UPDATE documents SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveDocument(id: String, folderId: String?, updatedAt: Long)

    @Query("UPDATE documents SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE documents SET isVault = :isVault, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setVault(id: String, isVault: Boolean, updatedAt: Long)

    @Query("UPDATE documents SET isTrashed = 1, trashedAt = :trashedAt, updatedAt = :trashedAt WHERE id = :id")
    suspend fun moveToTrash(id: String, trashedAt: Long)

    @Query("UPDATE documents SET isTrashed = 0, trashedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreFromTrash(id: String, updatedAt: Long)

    @Query("UPDATE documents SET ocrStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOcrStatus(id: String, status: OcrStatus, updatedAt: Long)

    @Query("UPDATE documents SET pageCount = :pageCount, coverThumbPath = :coverThumb, totalSizeBytes = :sizeBytes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDocumentStats(id: String, pageCount: Int, coverThumb: String?, sizeBytes: Long, updatedAt: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentPermanently(id: String)

    @Query("DELETE FROM documents WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM documents")
    suspend fun clearAllDocuments()
}

@Dao
interface PageDao {
    @Transaction
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY `index` ASC")
    fun observePagesForDocument(documentId: String): Flow<List<PageWithText>>

    @Transaction
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY `index` ASC")
    suspend fun getPagesForDocument(documentId: String): List<PageWithText>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageById(pageId: String): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPages(pages: List<PageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: PageEntity)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePage(pageId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPageText(pageText: PageTextEntity)

    @Query("SELECT text FROM page_texts WHERE pageId = :pageId")
    suspend fun getPageText(pageId: String): String?
}

@Dao
interface FolderAndTagDao {
    @Query(
        """
        SELECT f.*, COALESCE((SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id AND d.isVault = 0 AND d.isTrashed = 0), 0) AS itemCount
        FROM folders f
        WHERE (:parentId IS NULL AND f.parentId IS NULL) OR f.parentId = :parentId
        ORDER BY f.name ASC
        """
    )
    fun observeFolders(parentId: String?): Flow<List<FolderWithCount>>

    @Query(
        """
        SELECT f.*, COALESCE((SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id AND d.isVault = 0 AND d.isTrashed = 0), 0) AS itemCount
        FROM folders f
        ORDER BY f.name ASC
        """
    )
    fun observeAllFolders(): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attachTag(crossRef: DocumentTagCrossRef)

    @Query("DELETE FROM document_tag_cross_ref WHERE documentId = :documentId AND tagId = :tagId")
    suspend fun detachTag(documentId: String, tagId: String)
}

@Dao
interface SearchDao {
    /**
     * Searches across FTS4 OCR text, document title, and tag names while strictly
     * excluding Vault documents (isVault = 0) and Trashed documents (isTrashed = 0).
     */
    @Query(
        """
        SELECT DISTINCT d.id AS documentId,
               COALESCE(
                   (SELECT pt.text FROM page_texts pt
                    JOIN page_texts_fts fts ON pt.rowid = fts.rowid
                    WHERE pt.documentId = d.id AND page_texts_fts MATCH :ftsQuery
                    LIMIT 1),
                   d.title
               ) AS matchedText
        FROM documents d
        LEFT JOIN page_texts pt2 ON pt2.documentId = d.id
        LEFT JOIN page_texts_fts fts2 ON pt2.rowid = fts2.rowid
        LEFT JOIN document_tag_cross_ref dt ON dt.documentId = d.id
        LEFT JOIN tags t ON t.id = dt.tagId
        WHERE d.isVault = 0 AND d.isTrashed = 0
          AND (
              d.title LIKE '%' || :plainQuery || '%'
              OR t.name LIKE '%' || :plainQuery || '%'
              OR (pt2.pageId IS NOT NULL AND page_texts_fts MATCH :ftsQuery)
          )
        ORDER BY d.updatedAt DESC
        """
    )
    suspend fun searchDocuments(plainQuery: String, ftsQuery: String): List<FtsSearchRow>

    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT 10")
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentSearch(entry: RecentSearchEntity)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()
}

@Dao
interface IdentityDataDao {
    @Query("SELECT * FROM identity_data WHERE documentId = :documentId")
    fun observeIdentityData(documentId: String): Flow<IdentityDataEntity?>

    @Query("SELECT * FROM identity_data WHERE documentId = :documentId")
    suspend fun getIdentityData(documentId: String): IdentityDataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIdentityData(entity: IdentityDataEntity)
}
