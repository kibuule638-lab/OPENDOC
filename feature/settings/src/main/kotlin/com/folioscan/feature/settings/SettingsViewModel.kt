package com.folioscan.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folioscan.core.database.DocumentRepository
import com.folioscan.core.model.AppSettings
import com.folioscan.core.model.CompressionPreset
import com.folioscan.core.model.ExportFormat
import com.folioscan.core.model.PageFilter
import com.folioscan.core.model.ThemePreference
import com.folioscan.core.storage.ScanFileStore
import com.folioscan.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val storageUsedBytes: Long = 0L,
    val isBusy: Boolean = false
)

sealed interface SettingsEffect {
    data class ShowMessage(val message: String) : SettingsEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val documentRepository: DocumentRepository,
    private val fileStore: ScanFileStore
) : ViewModel() {

    private val storageBytes = MutableStateFlow(fileStore.calculateTotalStorageBytes())
    private val isBusy = MutableStateFlow(false)

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        storageBytes,
        isBusy
    ) { s, bytes, busy ->
        SettingsUiState(settings = s, storageUsedBytes = bytes, isBusy = busy)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun refreshStorageUsage() {
        storageBytes.value = fileStore.calculateTotalStorageBytes()
    }

    fun setTheme(pref: ThemePreference) = viewModelScope.launch { settingsRepository.setThemePreference(pref) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    fun setPageLimit(limit: Int) = viewModelScope.launch { settingsRepository.setDefaultPageLimit(limit) }
    fun setDefaultFilter(filter: PageFilter) = viewModelScope.launch { settingsRepository.setDefaultFilter(filter) }
    fun setAutoCapture(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoCapture(enabled) }
    fun setHaptic(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHapticOnCapture(enabled) }
    fun setOtherIdRatio(ratio: Float) = viewModelScope.launch { settingsRepository.setOtherIdAspectRatio(ratio) }
    fun setDefaultExportFormat(format: ExportFormat) = viewModelScope.launch { settingsRepository.setDefaultExportFormat(format) }
    fun setDefaultCompression(preset: CompressionPreset) = viewModelScope.launch { settingsRepository.setDefaultCompression(preset) }
    fun setAppLock(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAppLockEnabled(enabled) }
    fun setVaultBiometric(enabled: Boolean) = viewModelScope.launch { settingsRepository.setVaultBiometricEnabled(enabled) }
    fun setClipboardClearSeconds(sec: Int) = viewModelScope.launch { settingsRepository.setClipboardAutoClearSeconds(sec) }

    fun updateVaultPin(pin: String) {
        if (pin.length < 4) return
        viewModelScope.launch {
            settingsRepository.setVaultPin(pin)
            _effects.send(SettingsEffect.ShowMessage("Vault PIN updated"))
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            isBusy.value = true
            runCatching { fileStore.exportLocalBackupZip(uri) }
                .onSuccess { bytes ->
                    _effects.send(SettingsEffect.ShowMessage("Backup saved (${bytes / 1024} KB)"))
                }
                .onFailure {
                    _effects.send(SettingsEffect.ShowMessage("Backup failed: ${it.message}"))
                }
            isBusy.value = false
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            isBusy.value = true
            runCatching { fileStore.restoreLocalBackupZip(uri) }
                .onSuccess { count ->
                    refreshStorageUsage()
                    _effects.send(SettingsEffect.ShowMessage("Restored $count file(s). Restart app if DB replaced."))
                }
                .onFailure {
                    _effects.send(SettingsEffect.ShowMessage("Restore failed: ${it.message}"))
                }
            isBusy.value = false
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            isBusy.value = true
            documentRepository.clearAllData()
            settingsRepository.resetAllSettings()
            refreshStorageUsage()
            isBusy.value = false
            _effects.send(SettingsEffect.ShowMessage("All scans and settings cleared"))
        }
    }
}
