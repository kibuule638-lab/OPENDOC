package com.folioscan.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folioscan.core.database.DocumentRepository
import com.folioscan.core.model.ScanDocument
import com.folioscan.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaultUiState(
    val unlocked: Boolean = false,
    val pinConfigured: Boolean = false,
    val biometricEnabled: Boolean = true,
    val pinError: String? = null,
    val vaultDocuments: List<ScanDocument> = emptyList()
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val unlocked = MutableStateFlow(false)
    private val pinError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<VaultUiState> = combine(
        unlocked,
        pinError,
        settingsRepository.settingsFlow,
        repository.observeVaultDocuments()
    ) { isUnlocked, err, settings, docs ->
        VaultUiState(
            unlocked = isUnlocked,
            pinConfigured = settings.vaultPinConfigured,
            biometricEnabled = settings.vaultBiometricEnabled,
            pinError = err,
            vaultDocuments = if (isUnlocked) docs else emptyList()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultUiState()
    )

    fun onBiometricSuccess() {
        pinError.value = null
        unlocked.value = true
    }

    fun submitPin(pin: String) {
        if (pin.length < 4) {
            pinError.value = "Enter at least 4 digits"
            return
        }
        viewModelScope.launch {
            if (!uiState.value.pinConfigured) {
                settingsRepository.setVaultPin(pin)
                pinError.value = null
                unlocked.value = true
            } else if (settingsRepository.verifyVaultPin(pin)) {
                pinError.value = null
                unlocked.value = true
            } else {
                pinError.value = "Incorrect Vault PIN"
            }
        }
    }

    fun lockVault() {
        unlocked.value = false
    }
}
