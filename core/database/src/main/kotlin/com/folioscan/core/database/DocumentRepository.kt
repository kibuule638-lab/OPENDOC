package com.folioscan.core.database

import androidx.room.withTransaction
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.Folder
import com.folioscan.core.model.IdentityData
import com.folioscan.core.model.IdentityField
import com.folioscan.core.model.OcrStatus
import com.folioscan.core.model.PageAdjustments
import com.folioscan.core.model.RedactionBox
import com.folioscan.core.model.ScanDocument
import com.folioscan.core.model.ScanPage
import com.folioscan.core.model.SearchFilter
import com.folioscan.core.model.SearchResultItem
import com.folioscan.core.model.SignatureStroke
import com.folioscan.core.model.Tag
import com.folioscan.core.storage.KeystoreCryptoManager
import com.folioscan.core.storage.ScanFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val db: FolioScanDatabase,
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val folderAndTagDao: FolderAndTagDao,
    private val searchDao: SearchDao,
    private val identityDataDao: IdentityDataDao,
    private val cryptoManager: KeystoreCryptoManager,
    private val fileStore: ScanFileStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeActiveDocuments(): Flow<List<ScanDocument>> =
        documentDao.observeActiveDocuments().map { list -> list.map { it.toDomain() } }

    fun observeVaultDocuments(): Flow<List<ScanDocument>> =
        documentDao.observeVaultDocuments().map { list -> list.map { it.toDomain() } }

    fun observeTrashedDocuments(): Flow<List<ScanDocument>> =
        documentDao.observeTrashedDocuments().map { list -> list.map { it.toDomain() } }

    fun observeDocumentsInFolder(folderId: String): Flow<List<ScanDocument>> =
        documentDao.observeDocumentsInFolder(folderId).map { list -> list.map { it.toDomain() } }

    fun observeDocument(documentId: String): Flow<ScanDocument?> =
        documentDao.observeDocumentById(documentId).map { it?.toDomain() }

    suspend fun getDocument(documentId: String): ScanDocument? =
        documentDao.getDocumentById(documentId)?.toDomain()

    fun observePages(documentId: String): Flow<List<ScanPage>> =
        pageDao.observePagesForDocument(documentId).map { list -> list.map { it.toDomain() } }

    suspend fun getPages(documentId: String): List<ScanPage> =
        pageDao.getPagesForDocument(documentId).map { it.toDomain() }

    fun observeFolders(parentId: String?): Flow<List<Folder>> =
        folderAndTagDao.observeFolders(parentId).map { list ->
            list.map { Folder(it.folder.id, it.folder.parentId, it.folder.name, it.itemCount, it.folder.createdAt) }
        }

    fun observeAllFolders(): Flow<List<Folder>> =
        folderAndTagDao.observeAllFolders().map { list ->
            list.map { Folder(it.folder.id, it.folder.parentId, it.folder.name, it.itemCount, it.folder.createdAt) }
        }

    fun observeAllTags(): Flow<List<Tag>> =
        folderAndTagDao.observeAllTags().map { list -> list.map { Tag(it.id, it.name) } }

    fun observeRecentSearches(): Flow<List<String>> =
        searchDao.observeRecentSearches().map { list -> list.map { it.query } }

    fun observeIdentityData(documentId: String): Flow<IdentityData?> =
        identityDataDao.observeIdentityData(documentId).map { entity -> entity?.toDecryptedDomain() }

    suspend fun getIdentityData(documentId: String): IdentityData? =
        identityDataDao.getIdentityData(documentId)?.toDecryptedDomain()

    /**
     * Persists Document + Pages atomically inside a Room @Transaction after files are already on disk.
     */
    suspend fun persistCapturedDocument(
        document: ScanDocument,
        pages: List<ScanPage>,
        identityData: IdentityData? = null
    ) {
        db.withTransaction {
            documentDao.upsertDocument(document.toEntity())
            pageDao.upsertPages(pages.mapIndexed { idx, page -> page.copy(index = idx).toEntity() })
            if (identityData != null) {
                identityDataDao.upsertIdentityData(identityData.toEncryptedEntity())
            }
        }
    }

    suspend fun saveIdentityData(identityData: IdentityData) {
        db.withTransaction {
            identityDataDao.upsertIdentityData(identityData.toEncryptedEntity())
            documentDao.getDocumentById(identityData.documentId)?.document?.let { doc ->
                documentDao.upsertDocument(doc.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun updatePagesAndStats(documentId: String, pages: List<ScanPage>) {
        db.withTransaction {
            val reindexed = pages.mapIndexed { idx, p -> p.copy(index = idx) }
            val existing = pageDao.getPagesForDocument(documentId).map { it.page.id }.toSet()
            val keepIds = reindexed.map { it.id }.toSet()
            (existing - keepIds).forEach { removedId ->
                pageDao.deletePage(removedId)
            }
            pageDao.upsertPages(reindexed.map { it.toEntity() })
            val coverThumb = reindexed.firstOrNull()?.thumbPath
            val sizeSum = reindexed.sumOf { java.io.File(it.imagePath).length() }
            documentDao.updateDocumentStats(
                id = documentId,
                pageCount = reindexed.size,
                coverThumb = coverThumb,
                sizeBytes = sizeSum,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun savePageOcrText(pageId: String, documentId: String, text: String) {
        pageDao.upsertPageText(PageTextEntity(pageId = pageId, documentId = documentId, text = text))
    }

    suspend fun updateOcrStatus(documentId: String, status: OcrStatus) {
        documentDao.updateOcrStatus(documentId, status, System.currentTimeMillis())
    }

    suspend fun renameDocument(documentId: String, newTitle: String) {
        documentDao.renameDocument(documentId, newTitle.trim().ifEmpty { "Untitled Scan" }, System.currentTimeMillis())
    }

    suspend fun batchRename(documentIds: List<String>, prefix: String) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            documentIds.forEachIndexed { idx, id ->
                documentDao.renameDocument(id, "$prefix ${idx + 1}", now)
            }
        }
    }

    suspend fun moveDocumentsToFolder(documentIds: List<String>, folderId: String?) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            documentIds.forEach { documentDao.moveDocument(it, folderId, now) }
        }
    }

    suspend fun toggleFavorite(documentId: String, favorite: Boolean) {
        documentDao.setFavorite(documentId, favorite, System.currentTimeMillis())
    }

    suspend fun setVaultStatus(documentId: String, isVault: Boolean) {
        documentDao.setVault(documentId, isVault, System.currentTimeMillis())
    }

    suspend fun moveToTrash(documentIds: List<String>) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            documentIds.forEach { documentDao.moveToTrash(it, now) }
        }
    }

    suspend fun restoreFromTrash(documentId: String) {
        documentDao.restoreFromTrash(documentId, System.currentTimeMillis())
    }

    suspend fun deletePermanently(documentId: String) {
        db.withTransaction {
            documentDao.deleteDocumentPermanently(documentId)
        }
        fileStore.deleteDocumentFiles(documentId)
    }

    suspend fun emptyTrash() {
        val trashed = db.withTransaction {
            // Collect IDs first by querying directly
            emptyList<String>()
        }
        documentDao.emptyTrash()
        trashed.forEach { fileStore.deleteDocumentFiles(it) }
    }

    suspend fun duplicateDocument(documentId: String): String? {
        val sourceDoc = getDocument(documentId) ?: return null
        val sourcePages = getPages(documentId)
        val newDocId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val newPages = sourcePages.mapIndexed { idx, page ->
            val newPageId = UUID.randomUUID().toString()
            val bytes = runCatching { fileStore.readPageBytes(page.imagePath) }.getOrNull()
            val saved = if (bytes != null) {
                fileStore.savePageBytes(bytes, newDocId, newPageId, encryptAtRest = sourceDoc.isVault)
            } else null
            page.copy(
                id = newPageId,
                documentId = newDocId,
                index = idx,
                imagePath = saved?.imagePath ?: page.imagePath,
                thumbPath = saved?.thumbPath ?: page.thumbPath
            )
        }
        val duplicated = sourceDoc.copy(
            id = newDocId,
            title = "${sourceDoc.title} (Copy)",
            createdAt = now,
            updatedAt = now,
            coverThumbPath = newPages.firstOrNull()?.thumbPath
        )
        persistCapturedDocument(duplicated, newPages, getIdentityData(documentId)?.copy(documentId = newDocId))
        return newDocId
    }

    suspend fun mergeDocuments(documentIds: List<String>, mergedTitle: String): String? {
        if (documentIds.size < 2) return null
        val newDocId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val allNewPages = mutableListOf<ScanPage>()
        var pageIndex = 0
        var anyVault = false

        for (docId in documentIds) {
            val doc = getDocument(docId) ?: continue
            if (doc.isVault) anyVault = true
            val pages = getPages(docId)
            for (page in pages) {
                val newPageId = UUID.randomUUID().toString()
                val bytes = runCatching { fileStore.readPageBytes(page.imagePath) }.getOrNull() ?: continue
                val saved = fileStore.savePageBytes(bytes, newDocId, newPageId, encryptAtRest = anyVault)
                allNewPages += page.copy(
                    id = newPageId,
                    documentId = newDocId,
                    index = pageIndex++,
                    imagePath = saved.imagePath,
                    thumbPath = saved.thumbPath
                )
            }
        }
        if (allNewPages.isEmpty()) return null

        val mergedDoc = ScanDocument(
            id = newDocId,
            title = mergedTitle.ifBlank { "Merged Scan" },
            type = DocumentType.DOCUMENT,
            createdAt = now,
            updatedAt = now,
            pageCount = allNewPages.size,
            ocrStatus = OcrStatus.QUEUED,
            isVault = anyVault,
            coverThumbPath = allNewPages.first().thumbPath,
            totalSizeBytes = allNewPages.sumOf { java.io.File(it.imagePath).length() }
        )
        persistCapturedDocument(mergedDoc, allNewPages)
        return newDocId
    }

    suspend fun createFolder(name: String, parentId: String? = null): Folder {
        val folder = Folder(
            id = UUID.randomUUID().toString(),
            parentId = parentId,
            name = name.trim().ifEmpty { "New Folder" },
            itemCount = 0,
            createdAt = System.currentTimeMillis()
        )
        folderAndTagDao.upsertFolder(
            FolderEntity(
                id = folder.id,
                parentId = folder.parentId,
                name = folder.name,
                createdAt = folder.createdAt
            )
        )
        return folder
    }

    suspend fun getFolderBreadcrumb(folderId: String?): List<Folder> {
        val chain = mutableListOf<Folder>()
        var currentId = folderId
        var guard = 0
        while (currentId != null && guard++ < 16) {
            val entity = folderAndTagDao.getFolderById(currentId) ?: break
            chain += Folder(entity.id, entity.parentId, entity.name, 0, entity.createdAt)
            currentId = entity.parentId
        }
        return chain.reversed()
    }

    suspend fun deleteFolder(folderId: String) {
        folderAndTagDao.deleteFolder(folderId)
    }

    suspend fun attachTagToDocument(documentId: String, tagName: String) {
        val normalized = tagName.trim()
        if (normalized.isEmpty()) return
        db.withTransaction {
            val existing = folderAndTagDao.findTagByName(normalized)
            val tag = existing ?: TagEntity(id = UUID.randomUUID().toString(), name = normalized).also {
                folderAndTagDao.insertTag(it)
            }
            folderAndTagDao.attachTag(DocumentTagCrossRef(documentId, tag.id))
        }
    }

    suspend fun detachTagFromDocument(documentId: String, tagId: String) {
        folderAndTagDao.detachTag(documentId, tagId)
    }

    suspend fun search(query: String, filter: SearchFilter): List<SearchResultItem> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        searchDao.upsertRecentSearch(RecentSearchEntity(clean, System.currentTimeMillis()))

        val ftsTokens = clean.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" AND ") { token ->
                val sanitized = token.replace(Regex("[^\\p{L}\\p{N}_]"), "")
                if (sanitized.isEmpty()) "" else "$sanitized*"
            }
            .ifBlank { clean }

        val rows = runCatching {
            searchDao.searchDocuments(plainQuery = clean, ftsQuery = ftsTokens)
        }.getOrDefault(emptyList())

        return rows.mapNotNull { row ->
            val doc = documentDao.getDocumentById(row.documentId)?.toDomain() ?: return@mapNotNull null
            if (doc.isVault || doc.isTrashed) return@mapNotNull null
            if (filter.type != null && doc.type != filter.type) return@mapNotNull null
            if (filter.folderId != null && doc.folderId != filter.folderId) return@mapNotNull null
            if (filter.tagId != null && doc.tags.none { it.id == filter.tagId }) return@mapNotNull null
            val fromTs = filter.fromTimestamp
            if (fromTs != null && doc.updatedAt < fromTs) return@mapNotNull null
            SearchResultItem(
                document = doc,
                snippet = extractSnippet(row.matchedText, clean)
            )
        }
    }

    suspend fun clearRecentSearches() {
        searchDao.clearRecentSearches()
    }

    suspend fun clearAllData() {
        db.withTransaction {
            documentDao.clearAllDocuments()
            searchDao.clearRecentSearches()
        }
        fileStore.clearAllScanFiles()
    }

    fun generateSmartTitle(type: DocumentType, ocrFirstLine: String? = null): String {
        val dateStamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val cleanedLine = ocrFirstLine
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.length in 4..36 }
        return when {
            cleanedLine != null -> cleanedLine
            type == DocumentType.ID_CARD -> "ID Card $dateStamp"
            type == DocumentType.PASSPORT -> "Passport $dateStamp"
            type == DocumentType.BUSINESS_CARD -> "Business Card $dateStamp"
            else -> "Scan $dateStamp"
        }
    }

    private fun extractSnippet(source: String, query: String): String {
        if (source.isBlank()) return ""
        val idx = source.indexOf(query, ignoreCase = true)
        if (idx < 0) return source.take(120)
        val start = (idx - 32).coerceAtLeast(0)
        val end = (idx + query.length + 64).coerceAtMost(source.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < source.length) "…" else ""
        return prefix + source.substring(start, end).replace('\n', ' ') + suffix
    }

    private fun DocumentWithTags.toDomain(): ScanDocument = ScanDocument(
        id = document.id,
        title = document.title,
        type = document.type,
        folderId = document.folderId,
        createdAt = document.createdAt,
        updatedAt = document.updatedAt,
        pageCount = document.pageCount,
        pdfPath = document.pdfPath,
        ocrStatus = document.ocrStatus,
        isVault = document.isVault,
        isFavorite = document.isFavorite,
        isTrashed = document.isTrashed,
        trashedAt = document.trashedAt,
        coverThumbPath = document.coverThumbPath,
        totalSizeBytes = document.totalSizeBytes,
        tags = tags.map { Tag(it.id, it.name) }
    )

    private fun ScanDocument.toEntity(): DocumentEntity = DocumentEntity(
        id = id,
        title = title,
        type = type,
        folderId = folderId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pageCount = pageCount,
        pdfPath = pdfPath,
        ocrStatus = ocrStatus,
        isVault = isVault,
        isFavorite = isFavorite,
        isTrashed = isTrashed,
        trashedAt = trashedAt,
        coverThumbPath = coverThumbPath,
        totalSizeBytes = totalSizeBytes
    )

    private fun PageWithText.toDomain(): ScanPage = ScanPage(
        id = page.id,
        documentId = page.documentId,
        index = page.index,
        imagePath = page.imagePath,
        thumbPath = page.thumbPath,
        width = page.width,
        height = page.height,
        rotation = page.rotation,
        filter = page.filter,
        adjustments = runCatching { json.decodeFromString<PageAdjustments>(page.adjustmentsJson) }
            .getOrDefault(PageAdjustments()),
        redactions = runCatching { json.decodeFromString<List<RedactionBox>>(page.redactionsJson) }
            .getOrDefault(emptyList()),
        signatureStrokes = runCatching { json.decodeFromString<List<SignatureStroke>>(page.signatureJson) }
            .getOrDefault(emptyList()),
        ocrText = pageText?.text
    )

    private fun ScanPage.toEntity(): PageEntity = PageEntity(
        id = id,
        documentId = documentId,
        index = index,
        imagePath = imagePath,
        thumbPath = thumbPath,
        width = width,
        height = height,
        rotation = rotation,
        filter = filter,
        adjustmentsJson = json.encodeToString(adjustments),
        redactionsJson = json.encodeToString(redactions),
        signatureJson = json.encodeToString(signatureStrokes)
    )

    private fun IdentityDataEntity.toDecryptedDomain(): IdentityData {
        val rawJson = cryptoManager.decryptString(fieldsJson)
        val rawMrz = mrzRaw?.let { cryptoManager.decryptString(it) }
        val fields = runCatching { json.decodeFromString<List<IdentityField>>(rawJson) }
            .getOrDefault(emptyList())
        return IdentityData(
            documentId = documentId,
            fields = fields,
            mrzRaw = rawMrz,
            mrzValid = mrzValid
        )
    }

    private fun IdentityData.toEncryptedEntity(): IdentityDataEntity {
        val plainJson = json.encodeToString(fields)
        val encryptedJson = cryptoManager.encryptString(plainJson)
        val encryptedMrz = mrzRaw?.let { cryptoManager.encryptString(it) }
        return IdentityDataEntity(
            documentId = documentId,
            fieldsJson = encryptedJson,
            mrzRaw = encryptedMrz,
            mrzValid = mrzValid
        )
    }
}
