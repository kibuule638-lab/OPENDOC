package com.folioscan.feature.vault

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folioscan.core.designsystem.DocCard
import com.folioscan.core.designsystem.EmptyState
import com.folioscan.core.designsystem.FolioSpacing
import com.folioscan.core.designsystem.R
import com.folioscan.core.model.CaptureMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onScanIntoVault: (CaptureMode) -> Unit,
    onSetWindowSecure: (Boolean) -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity

    // Always block screenshots & recents thumbnail on Vault screen
    LaunchedEffect(Unit) { onSetWindowSecure(true) }
    DisposableEffect(Unit) { onDispose { onSetWindowSecure(false) } }

    val launchBiometric: () -> Unit = remember(fragmentActivity) {
        {
            if (fragmentActivity != null) {
                val manager = BiometricManager.from(fragmentActivity)
                val canAuth = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                    BiometricManager.BIOMETRIC_SUCCESS
                if (canAuth) {
                    val prompt = BiometricPrompt(
                        fragmentActivity,
                        ContextCompat.getMainExecutor(fragmentActivity),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                viewModel.onBiometricSuccess()
                            }
                        }
                    )
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock Private Vault")
                        .setSubtitle("Authenticate to access encrypted IDs and passports")
                        .setNegativeButtonText("Use PIN")
                        .build()
                    prompt.authenticate(info)
                }
            }
        }
    }

    LaunchedEffect(state.unlocked, state.biometricEnabled, state.pinConfigured) {
        if (!state.unlocked && state.biometricEnabled && state.pinConfigured) {
            launchBiometric()
        }
    }

    if (!state.unlocked) {
        var pinInput by remember { mutableStateOf("") }
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("vault_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text("Private Vault") }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(FolioSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vault_shield),
                    contentDescription = "Private Vault Security Shield",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
                Spacer(Modifier.height(FolioSpacing.md))
                Text(
                    text = if (state.pinConfigured) "Vault is locked" else "Set your Vault PIN",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(FolioSpacing.xs))
                Text(
                    text = "Vault items are encrypted at rest with AES-256-GCM and excluded from recents & search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 340.dp)
                )
                Spacer(Modifier.height(FolioSpacing.md))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                    label = { Text(if (state.pinConfigured) "Enter PIN" else "Create 4–8 digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = state.pinError != null,
                    supportingText = state.pinError?.let { err -> { Text(err) } },
                    singleLine = true,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .fillMaxWidth()
                        .testTag("vault_pin_input")
                )
                Spacer(Modifier.height(FolioSpacing.sm))
                Button(
                    onClick = { viewModel.submitPin(pinInput) },
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("vault_unlock_btn")
                ) {
                    Text(if (state.pinConfigured) "Unlock with PIN" else "Save PIN & Unlock")
                }

                if (state.biometricEnabled && state.pinConfigured) {
                    Spacer(Modifier.height(FolioSpacing.sm))
                    OutlinedButton(
                        onClick = launchBiometric,
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("vault_biometric_btn")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Use Biometric")
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("vault_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Private Vault") },
                actions = {
                    IconButton(onClick = viewModel::lockVault, modifier = Modifier.testTag("vault_lock_btn")) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock Vault")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onScanIntoVault(CaptureMode.ID_CARD) },
                icon = { Icon(Icons.Default.Badge, contentDescription = null) },
                text = { Text("Scan ID to Vault") },
                modifier = Modifier.testTag("vault_scan_fab")
            )
        }
    ) { innerPadding ->
        if (state.vaultDocuments.isEmpty()) {
            EmptyState(
                title = "Your Private Vault is empty",
                subtitle = "Scan IDs or passports directly here, or move any document to the Vault from its menu.",
                icon = Icons.Default.Lock,
                ctaLabel = "Scan ID into Vault",
                onCtaClick = { onScanIntoVault(CaptureMode.ID_CARD) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(FolioSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(FolioSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(FolioSpacing.sm),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("vault_grid")
            ) {
                items(state.vaultDocuments, key = { it.id }) { doc ->
                    DocCard(
                        document = doc,
                        isSelected = false,
                        selectionMode = false,
                        onClick = { onOpenDocument(doc.id) },
                        onLongClick = {}
                    )
                }
            }
        }
    }
}
