package com.folioscan.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.folioscan.core.model.AppSettings
import com.folioscan.core.model.CompressionPreset
import com.folioscan.core.model.ExportFormat
import com.folioscan.core.model.PageFilter
import com.folioscan.core.model.ThemePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "folioscan_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: KeystoreCryptoManager
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_pref")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PAGE_LIMIT = intPreferencesKey("default_page_limit")
        val DEFAULT_FILTER = stringPreferencesKey("default_scan_filter")
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val HAPTIC = booleanPreferencesKey("haptic_capture")
        val EXPORT_FORMAT = stringPreferencesKey("default_export_format")
        val COMPRESSION = stringPreferencesKey("default_compression")
        val APP_LOCK = booleanPreferencesKey("app_lock_enabled")
        val VAULT_BIOMETRIC = booleanPreferencesKey("vault_biometric")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val CLIPBOARD_CLEAR_SEC = intPreferencesKey("clipboard_clear_sec")
        val OTHER_ID_RATIO = floatPreferencesKey("other_id_aspect_ratio")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themePreference = prefs[Keys.THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.SYSTEM,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: true,
            defaultPageLimit = prefs[Keys.PAGE_LIMIT] ?: 50,
            defaultScanFilter = prefs[Keys.DEFAULT_FILTER]?.let { runCatching { PageFilter.valueOf(it) }.getOrNull() }
                ?: PageFilter.MAGIC_COLOR,
            autoCaptureEnabled = prefs[Keys.AUTO_CAPTURE] ?: true,
            hapticOnCapture = prefs[Keys.HAPTIC] ?: true,
            defaultExportFormat = prefs[Keys.EXPORT_FORMAT]?.let { runCatching { ExportFormat.valueOf(it) }.getOrNull() }
                ?: ExportFormat.PDF,
            defaultCompressionPreset = prefs[Keys.COMPRESSION]?.let { runCatching { CompressionPreset.valueOf(it) }.getOrNull() }
                ?: CompressionPreset.MEDIUM,
            appLockEnabled = prefs[Keys.APP_LOCK] ?: false,
            vaultBiometricEnabled = prefs[Keys.VAULT_BIOMETRIC] ?: true,
            vaultPinConfigured = !prefs[Keys.VAULT_PIN_HASH].isNullOrBlank(),
            clipboardAutoClearSeconds = prefs[Keys.CLIPBOARD_CLEAR_SEC] ?: 30,
            otherIdAspectRatio = prefs[Keys.OTHER_ID_RATIO] ?: 1.586f,
            hasCompletedOnboarding = prefs[Keys.ONBOARDING_DONE] ?: false
        )
    }

    suspend fun setThemePreference(pref: ThemePreference) {
        context.settingsDataStore.edit { it[Keys.THEME] = pref.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDefaultPageLimit(limit: Int) {
        context.settingsDataStore.edit { it[Keys.PAGE_LIMIT] = limit.coerceIn(1, 200) }
    }

    suspend fun setDefaultFilter(filter: PageFilter) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_FILTER] = filter.name }
    }

    suspend fun setAutoCapture(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_CAPTURE] = enabled }
    }

    suspend fun setHapticOnCapture(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAPTIC] = enabled }
    }

    suspend fun setDefaultExportFormat(format: ExportFormat) {
        context.settingsDataStore.edit { it[Keys.EXPORT_FORMAT] = format.name }
    }

    suspend fun setDefaultCompression(preset: CompressionPreset) {
        context.settingsDataStore.edit { it[Keys.COMPRESSION] = preset.name }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.APP_LOCK] = enabled }
    }

    suspend fun setVaultBiometricEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.VAULT_BIOMETRIC] = enabled }
    }

    suspend fun setVaultPin(pin: String) {
        val hashed = cryptoManager.hashPin(pin)
        context.settingsDataStore.edit { it[Keys.VAULT_PIN_HASH] = hashed }
    }

    suspend fun verifyVaultPin(pin: String): Boolean {
        val stored = context.settingsDataStore.data.first()[Keys.VAULT_PIN_HASH] ?: return false
        return stored == cryptoManager.hashPin(pin)
    }

    suspend fun setClipboardAutoClearSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.CLIPBOARD_CLEAR_SEC] = seconds.coerceIn(10, 120) }
    }

    suspend fun setOtherIdAspectRatio(ratio: Float) {
        context.settingsDataStore.edit { it[Keys.OTHER_ID_RATIO] = ratio.coerceIn(1.1f, 2.2f) }
    }

    suspend fun markOnboardingDone() {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun resetAllSettings() {
        context.settingsDataStore.edit { it.clear() }
    }
}
