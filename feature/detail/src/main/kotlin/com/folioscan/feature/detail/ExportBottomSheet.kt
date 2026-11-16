package com.folioscan.feature.detail

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.folioscan.core.designsystem.FolioSpacing
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.ExportFormat
import com.folioscan.core.model.IdCardLayoutMode
import com.folioscan.core.model.WatermarkPosition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    state: ExportSheetState,
    documentType: DocumentType,
    onDismiss: () -> Unit,
    onFormatChange: (ExportFormat) -> Unit,
    onQualityChange: (Int) -> Unit,
    onPasswordChange: (String?) -> Unit,
    onWatermarkChange: (Boolean, String, Float, WatermarkPosition) -> Unit,
    onRedactionChange: (Boolean) -> Unit,
    onIdCardLayoutChange: (IdCardLayoutMode) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit
) {
    val context = LocalContext.current
    var passwordEnabled by remember { mutableStateOf(!state.config.password.isNullOrBlank()) }
    var passwordText by remember { mutableStateOf(state.config.password.orEmpty()) }
    var wmEnabled by remember { mutableStateOf(state.config.watermark.enabled) }
    var wmText by remember { mutableStateOf(state.config.watermark.text) }
    var wmOpacity by remember { mutableFloatStateOf(state.config.watermark.opacity) }
    var wmPosition by remember { mutableStateOf(state.config.watermark.position) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FolioSpacing.md, vertical = FolioSpacing.xs)
        ) {
            Text(
                text = "Export Document",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(FolioSpacing.sm))

            // Format toggle (PDF / JPG)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ExportFormat.entries.forEachIndexed { idx, format ->
                    SegmentedButton(
                        selected = state.config.format == format,
                        onClick = { onFormatChange(format) },
                        shape = SegmentedButtonDefaults.itemShape(idx, ExportFormat.entries.size),
                        modifier = Modifier.testTag("export_format_${format.name.lowercase()}")
                    ) {
                        Text(format.name)
                    }
                }
            }

            Spacer(Modifier.height(FolioSpacing.md))

            // Quality slider with estimated file size
            val estSize = Formatter.formatShortFileSize(context, state.estimatedBytes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Quality (${state.config.compressionPreset.label})",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "~$estSize",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = state.config.jpegQuality.toFloat(),
                onValueChange = { onQualityChange(it.toInt()) },
                valueRange = 45f..98f,
                modifier = Modifier.testTag("export_quality_slider")
            )

            // ID Card layout toggle (Single A4 to-scale vs 2 pages)
            if (documentType == DocumentType.ID_CARD && state.config.format == ExportFormat.PDF) {
                Text("ID Card PDF layout", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(FolioSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)) {
                    IdCardLayoutMode.entries.forEach { layoutMode ->
                        FilterChip(
                            selected = state.config.idCardLayout == layoutMode,
                            onClick = { onIdCardLayoutChange(layoutMode) },
                            label = { Text(layoutMode.label) }
                        )
                    }
                }
                Spacer(Modifier.height(FolioSpacing.sm))
            }

            // Password protection switch (AES-256 PDF)
            if (state.config.format == ExportFormat.PDF) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Password protect (AES-256)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Encrypts PDF via PdfBox AES-256",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = passwordEnabled,
                        onCheckedChange = { checked ->
                            passwordEnabled = checked
                            if (!checked) {
                                passwordText = ""
                                onPasswordChange(null)
                            }
                        },
                        modifier = Modifier.testTag("export_password_switch")
                    )
                }
                if (passwordEnabled) {
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = {
                            passwordText = it
                            onPasswordChange(it)
                        },
                        label = { Text("PDF password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FolioSpacing.xs)
                            .testTag("export_password_input")
                    )
                }
            }

            // Watermark switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Watermark", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = wmEnabled,
                    onCheckedChange = {
                        wmEnabled = it
                        onWatermarkChange(it, wmText, wmOpacity, wmPosition)
                    },
                    modifier = Modifier.testTag("export_watermark_switch")
                )
            }
            if (wmEnabled) {
                OutlinedTextField(
                    value = wmText,
                    onValueChange = {
                        wmText = it
                        onWatermarkChange(wmEnabled, it, wmOpacity, wmPosition)
                    },
                    label = { Text("Watermark text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)) {
                    WatermarkPosition.entries.forEach { pos ->
                        FilterChip(
                            selected = wmPosition == pos,
                            onClick = {
                                wmPosition = pos
                                onWatermarkChange(wmEnabled, wmText, wmOpacity, pos)
                            },
                            label = { Text(pos.label) }
                        )
                    }
                }
                Text("Opacity (${(wmOpacity * 100).toInt()}%)", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = wmOpacity,
                    onValueChange = {
                        wmOpacity = it
                        onWatermarkChange(wmEnabled, wmText, it, wmPosition)
                    },
                    valueRange = 0.08f..0.75f
                )
            }

            // Redaction switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Apply redaction black-out boxes", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.config.applyRedactions,
                    onCheckedChange = onRedactionChange,
                    modifier = Modifier.testTag("export_redaction_switch")
                )
            }

            Spacer(Modifier.height(FolioSpacing.md))

            state.errorMessage?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(FolioSpacing.xs))
            }

            if (state.isExporting) {
                state.progress?.let { prog ->
                    Text(prog.stageLabel, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(FolioSpacing.xs))
                    LinearProgressIndicator(
                        progress = {
                            if (prog.totalPages > 0) prog.completedPages.toFloat() / prog.totalPages else 0f
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(FolioSpacing.sm))
                OutlinedButton(
                    onClick = onCancelExport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel Export")
                }
            } else if (state.result != null) {
                val res = state.result
                Text(
                    text = "Saved ${res.displayName} (${Formatter.formatShortFileSize(context, res.sizeBytes)})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(FolioSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FolioSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = res.mimeType
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(res.shareUriString))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share export"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_share_btn")
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                    Button(
                        onClick = {
                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(res.shareUriString), res.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(openIntent) }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_open_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open")
                    }
                }
            } else {
                Button(
                    onClick = onStartExport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_primary_btn")
                ) {
                    Text("Export ${state.config.format.name}")
                }
            }

            Spacer(Modifier.height(FolioSpacing.lg))
        }
    }
}
