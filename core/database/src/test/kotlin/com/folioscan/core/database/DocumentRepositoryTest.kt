package com.folioscan.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.IdentityData
import com.folioscan.core.model.IdentityField
import com.folioscan.core.model.OcrStatus
import com.folioscan.core.model.ScanDocument
import com.folioscan.core.model.ScanPage
import com.folioscan.core.model.SearchFilter
import com.folioscan.core.storage.KeystoreCryptoManager
import com.folioscan.core.storage.ScanFileStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentRepositoryTest {

    private lateinit var db: FolioScanDatabase
    private lateinit var cryptoManager: KeystoreCryptoManager
    private lateinit var fileStore: ScanFileStore
    private lateinit var repository: DocumentRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = FolioScanDatabase.createInMemory(context)
        cryptoManager = KeystoreCryptoManager(context)
        fileStore = ScanFileStore(context, cryptoManager)
        repository = DocumentRepository(
            db = db,
            documentDao = db.documentDao(),
            pageDao = db.pageDao(),
            folderAndTagDao = db.folderAndTagDao(),
            searchDao = db.searchDao(),
            identityDataDao = db.identityDataDao(),
            cryptoManager = cryptoManager,
            fileStore = fileStore
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `vault documents are excluded from active queries and FTS4 search`() = runTest {
        val normalDoc = ScanDocument(
            id = "doc-1",
            title = "Electricity Invoice",
            type = DocumentType.DOCUMENT,
            createdAt = 1000L,
            updatedAt = 1000L,
            pageCount = 1,
            ocrStatus = OcrStatus.DONE,
            isVault = false
        )
        val vaultDoc = ScanDocument(
            id = "doc-vault",
            title = "Secret Passport",
            type = DocumentType.PASSPORT,
            createdAt = 2000L,
            updatedAt = 2000L,
            pageCount = 1,
            ocrStatus = OcrStatus.DONE,
            isVault = true
        )
        repository.persistCapturedDocument(
            normalDoc,
            listOf(ScanPage("p1", "doc-1", 0, "/tmp/1.jpg", "/tmp/t1.jpg", 100, 100))
        )
        repository.persistCapturedDocument(
            vaultDoc,
            listOf(ScanPage("p2", "doc-vault", 0, "/tmp/2.jpg", "/tmp/t2.jpg", 100, 100))
        )
        repository.savePageOcrText("p1", "doc-1", "Kilowatt invoice total due 92 USD")
        repository.savePageOcrText("p2", "doc-vault", "Secret passport invoice keyword")

        repository.observeActiveDocuments().test {
            val active = awaitItem()
            assertEquals(1, active.size)
            assertEquals("doc-1", active.first().id)
            cancelAndIgnoreRemainingEvents()
        }

        val hits = repository.search("invoice", SearchFilter())
        assertEquals(1, hits.size)
        assertEquals("doc-1", hits.first().document.id)
    }

    @Test
    fun `identity data fields are encrypted at rest in Room`() = runTest {
        val doc = ScanDocument(
            id = "doc-id",
            title = "Passport",
            type = DocumentType.PASSPORT,
            createdAt = 1000L,
            updatedAt = 1000L,
            pageCount = 1,
            isVault = true
        )
        repository.persistCapturedDocument(
            doc,
            listOf(ScanPage("p-id", "doc-id", 0, "/tmp/id.jpg", "/tmp/idt.jpg", 100, 100)),
            IdentityData(
                documentId = "doc-id",
                fields = listOf(IdentityField("surname", "Surname", "ERIKSSON", true)),
                mrzRaw = "P<UTOERIKSSON",
                mrzValid = true
            )
        )

        val rawEntity = db.identityDataDao().getIdentityData("doc-id")!!
        assertFalse(rawEntity.fieldsJson.contains("ERIKSSON"))
        assertFalse(rawEntity.mrzRaw!!.contains("UTOERIKSSON"))

        val decrypted = repository.getIdentityData("doc-id")!!
        assertEquals("ERIKSSON", decrypted.fields.first().value)
        assertTrue(decrypted.mrzValid)
    }

    @Test
    fun `folders tags and trash restore lifecycle works transactionally`() = runTest {
        val parentFolder = repository.createFolder("Taxes", null)
        val childFolder = repository.createFolder("2025", parentFolder.id)
        val breadcrumb = repository.getFolderBreadcrumb(childFolder.id)
        assertEquals(listOf("Taxes", "2025"), breadcrumb.map { it.name })

        val doc = ScanDocument(
            id = "doc-org",
            title = "Receipt",
            type = DocumentType.DOCUMENT,
            createdAt = 1000L,
            updatedAt = 1000L,
            pageCount = 1
        )
        repository.persistCapturedDocument(
            doc,
            listOf(ScanPage("p-org", "doc-org", 0, "/tmp/r.jpg", "/tmp/rt.jpg", 100, 100))
        )
        repository.moveDocumentsToFolder(listOf("doc-org"), childFolder.id)
        repository.attachTagToDocument("doc-org", "deductible")

        val fetched = repository.getDocument("doc-org")!!
        assertEquals(childFolder.id, fetched.folderId)
        assertEquals(listOf("deductible"), fetched.tags.map { it.name })

        repository.moveToTrash(listOf("doc-org"))
        assertTrue(repository.getDocument("doc-org")!!.isTrashed)

        repository.restoreFromTrash("doc-org")
        assertFalse(repository.getDocument("doc-org")!!.isTrashed)
    }
}
