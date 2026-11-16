package com.folioscan.feature.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folioscan.core.designsystem.FolioSpacing
import com.folioscan.core.model.CompressionPreset
import com.folioscan.core.model.ExportFormat
import com.folioscan.core.model.PageFilter
import com.folioscan.core.model.ThemePreference

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenVault: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let(viewModel::exportBackup)
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::restoreBackup)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageUsage()
        viewModel.effects.collect { eff ->
            when (eff) {
                is SettingsEffect.ShowMessage -> snackbarHostState.showSnackbar(eff.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FolioSpacing.md)
        ) {
            // 1. Appearance
            SectionHeader("Appearance")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)) {
                ThemePreference.entries.forEach { pref ->
                    FilterChip(
                        selected = state.settings.themePreference == pref,
                        onClick = { viewModel.setTheme(pref) },
                        label = { Text(pref.label) }
                    )
                }
            }
            SettingsSwitchRow(
                title = "Material You dynamic color",
                checked = state.settings.dynamicColorEnabled,
                onCheckedChange = viewModel::setDynamicColor
            )
            HorizontalDivider(Modifier.padding(vertical = FolioSpacing.xs))

            // 2. Scanning defaults
            SectionHeader("Scanning defaults")
            Text(
                text = "Document page limit: ${state.settings.defaultPageLimit}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = state.settings.defaultPageLimit.toFloat(),
                onValueChange = { viewModel.setPageLimit(it.toInt()) },
                valueRange = 5f..100f
            )
            Text("Default filter", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)) {
                PageFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.settings.defaultScanFilter == filter,
                        onClick = { viewModel.setDefaultFilter(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }
            SettingsSwitchRow(
                title = "Auto-capture when steady (600ms)",
                checked = state.settings.autoCaptureEnabled,
                onCheckedChange = viewModel::setAutoCapture
            )
            SettingsSwitchRow(
                title = "Haptic feedback on capture",
                checked = state.settings.hapticOnCapture,
                onCheckedChange = viewModel::setHaptic
            )
            Text(
                text = "Other ID frame ratio: ${"%.2f".format(state.settings.otherIdAspectRatio)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = state.settings.otherIdAspectRatio,
                onValueChange = viewModel::setOtherIdRatio,
                valueRange = 1.25f..1.85f
            )
            HorizontalDivider(Modifier.padding(vertical = FolioSpacing.xs))

            // 3. Export defaults
            SectionHeader("Export defaults")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)) {
                ExportFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = state.settings.defaultExportFormat == fmt,
                        onClick = { viewModel.setDefaultExportFormat(fmt) },
                        label = { Text(fmt.name) }
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)) {
                CompressionPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.settings.defaultCompressionPreset == preset,
                        onClick = { viewModel.setDefaultCompression(preset) },
                        label = { Text(preset.label) }
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = FolioSpacing.xs))

            // 4. Security
            SectionHeader("Security")
            SettingsSwitchRow(
                title = "App lock on launch",
                checked = state.settings.appLockEnabled,
                onCheckedChange = viewModel::setAppLock
            )
            SettingsSwitchRow(
                title = "Biometric unlock for Vault",
                checked = state.settings.vaultBiometricEnabled,
                onCheckedChange = viewModel::setVaultBiometric
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)
            ) {
                OutlinedButton(
                    onClick = { showPinDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.settings.vaultPinConfigured) "Change Vault PIN" else "Set Vault PIN")
                }
                Button(
                    onClick = onOpenVault,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_open_vault_btn")
                ) {
                    Text("Open Vault")
                }
            }
            Spacer(Modifier.height(FolioSpacing.xs))
            Text(
                text = "Auto-clear copied sensitive fields: ${state.settings.clipboardAutoClearSeconds}s",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = state.settings.clipboardAutoClearSeconds.toFloat(),
                onValueChange = { viewModel.setClipboardClearSeconds(it.toInt()) },
                valueRange = 10f..90f
            )
            HorizontalDivider(Modifier.padding(vertical = FolioSpacing.xs))

            // 5. Storage and backup
            SectionHeader("Storage and backup")
            Text(
                text = "Local scan storage used: ${Formatter.formatShortFileSize(context, state.storageUsedBytes)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(FolioSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)
            ) {
                OutlinedButton(
                    onClick = { backupLauncher.launch("folioscan_backup.zip") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_backup_btn")
                ) {
                    Text("Local Backup")
                }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/zip")) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings_restore_btn")
                ) {
                    Text("Restore ZIP")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = FolioSpacing.xs))

            // 6. About
            SectionHeader("About")
            Text(
                text = "FolioScan v1.0 • 100% Offline Document & Identity Scanner",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "No INTERNET permission. Standard Document Scanner uses Google Play Services ML Kit when available, with automatic fallback to offline CameraX + OpenCV on GMS-free devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(FolioSpacing.lg))

            // Destructive action in red at the bottom with confirm dialog
            Button(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("settings_clear_data_btn")
            ) {
                Text("Clear all data")
            }

            Spacer(Modifier.height(FolioSpacing.xl))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all scans & settings?") },
            text = { Text("Every scanned document, Vault item, folder, and setting will be permanently erased from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDialog = false
                    },
                    modifier = Modifier.testTag("settings_clear_confirm_btn")
                ) {
                    Text("Erase everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Vault PIN") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("4–8 digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateVaultPin(newPin)
                        showPinDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = FolioSpacing.sm, bottom = FolioSpacing.xs)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
