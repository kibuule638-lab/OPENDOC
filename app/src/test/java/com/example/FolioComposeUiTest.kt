package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.folioscan.core.database.DocumentRepository
import com.folioscan.core.database.FolioScanDatabase
import com.folioscan.core.designsystem.FolioScanTheme
import com.folioscan.core.model.CaptureMode
import com.folioscan.core.storage.KeystoreCryptoManager
import com.folioscan.core.storage.ScanFileStore
import com.folioscan.core.storage.SettingsRepository
import com.folioscan.feature.folders.FoldersScreen
import com.folioscan.feature.folders.FoldersViewModel
import com.folioscan.feature.home.HomeScreen
import com.folioscan.feature.home.HomeViewModel
import com.folioscan.feature.search.SearchScreen
import com.folioscan.feature.search.SearchViewModel
import com.folioscan.feature.vault.VaultScreen
import com.folioscan.feature.vault.VaultViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FolioComposeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: FolioScanDatabase
    private lateinit var repository: DocumentRepository
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = FolioScanDatabase.createInMemory(context)
        val crypto = KeystoreCryptoManager(context)
        val fileStore = ScanFileStore(context, crypto)
        repository = DocumentRepository(
            db = db,
            documentDao = db.documentDao(),
            pageDao = db.pageDao(),
            folderAndTagDao = db.folderAndTagDao(),
            searchDao = db.searchDao(),
            identityDataDao = db.identityDataDao(),
            cryptoManager = crypto,
            fileStore = fileStore
        )
        settingsRepository = SettingsRepository(context, crypto)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `home screen shows mode chips and invokes selected capture mode`() {
        val vm = HomeViewModel(repository, settingsRepository)
        var capturedMode: CaptureMode? = null

        composeRule.setContent {
            FolioScanTheme {
                HomeScreen(
                    onOpenSearch = {},
                    onOpenCapture = { capturedMode = it },
                    onOpenDocument = {},
                    onOpenVault = {},
                    onOpenExportForDocument = {},
                    viewModel = vm
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("FolioScan").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_chip_passport").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_scan_fab").performClick()
        composeRule.waitForIdle()

        assertEquals(CaptureMode.PASSPORT, capturedMode)
    }

    @Test
    fun `vault screen configures PIN unlocks and locks with FLAG_SECURE`() {
        val vm = VaultViewModel(repository, settingsRepository)
        val secureCalls = mutableListOf<Boolean>()

        composeRule.setContent {
            FolioScanTheme {
                VaultScreen(
                    onBack = {},
                    onOpenDocument = {},
                    onScanIntoVault = {},
                    onSetWindowSecure = { secureCalls += it },
                    viewModel = vm
                )
            }
        }
        composeRule.waitForIdle()

        assertTrue(secureCalls.contains(true))
        composeRule.onNodeWithTag("vault_pin_input").performTextInput("2468")
        composeRule.onNodeWithTag("vault_unlock_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("vault_scan_fab").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("vault_scan_fab").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_lock_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("vault_pin_input").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("vault_pin_input").assertIsDisplayed()
    }

    @Test
    fun `search screen renders search bar and filter chips`() {
        val vm = SearchViewModel(repository)

        composeRule.setContent {
            FolioScanTheme {
                SearchScreen(
                    onOpenDocument = {},
                    viewModel = vm
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("search_input").assertIsDisplayed()
        composeRule.onNodeWithTag("search_input").performTextInput("passport")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Last 30 days").assertIsDisplayed()
    }

    @Test
    fun `folders screen opens new folder dialog and creates folder`() {
        val vm = FoldersViewModel(repository)

        composeRule.setContent {
            FolioScanTheme {
                FoldersScreen(
                    onOpenDocument = {},
                    onOpenExportForDocument = {},
                    viewModel = vm
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("folders_new_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("new_folder_name_input").performTextInput("Taxes 2026")
        composeRule.onNodeWithTag("new_folder_confirm_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Taxes 2026").assertIsDisplayed()
    }
}
