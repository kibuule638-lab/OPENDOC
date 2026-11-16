package com.folioscan.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.folioscan.core.model.CaptureMode
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.QualityHintType
import com.folioscan.core.model.ScanDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocCard(
    document: ScanDocument,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(document.updatedAt))
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(FolioSpacing.cardCorner)
            )
            .clip(RoundedCornerShape(FolioSpacing.cardCorner))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("doc_card_${document.id}"),
        shape = RoundedCornerShape(FolioSpacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = FolioSpacing.cardElevation)
    ) {
        Column(modifier = Modifier.padding(FolioSpacing.xs)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val thumbFile = document.coverThumbPath?.let { File(it) }
                if (thumbFile != null && thumbFile.exists()) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = "Preview of ${document.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = document.type.toIcon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Type badge top-left
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = document.type.shortBadge(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Vault / Favorite / Selection badges top-right
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (document.isVault) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Vault protected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(14.dp)
                            )
                        }
                    }
                    if (document.isFavorite) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorite",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(14.dp)
                            )
                        }
                    }
                    if (selectionMode) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FolioSpacing.xs))

            Text(
                text = document.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${document.pageCount} pg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ModeChip(
    mode: CaptureMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = mode.displayLabel,
                style = MaterialTheme.typography.labelLarge
            )
        },
        leadingIcon = {
            Icon(
                imageVector = mode.toIcon(),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(FolioSpacing.chipCorner),
        modifier = modifier
            .heightIn(min = FolioSpacing.minTouchTarget)
            .testTag("mode_chip_${mode.name.lowercase()}")
    )
}

@Composable
fun ToolbarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTint = tint ?: if (selected) {
        MaterialTheme.colorScheme.primary
    } else if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent,
        modifier = modifier
            .widthIn(min = 56.dp)
            .heightIn(min = FolioSpacing.minTouchTarget)
            .testTag("toolbar_item_${label.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = activeTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = activeTint,
                maxLines = 1
            )
        }
    }
}

@Composable
fun QualityHint(
    hintType: QualityHintType,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFolioSemanticColors.current
    val bgColor = when (hintType) {
        QualityHintType.LOCKED, QualityHintType.GOOD -> semantic.scanLockGreen.copy(alpha = 0.9f)
        QualityHintType.TOO_MUCH_GLARE, QualityHintType.LOW_LIGHT_TORCH -> semantic.qualityWarningAmber.copy(alpha = 0.92f)
        QualityHintType.HOLD_STEADY, QualityHintType.MOVE_CLOSER -> MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    }
    val textColor = when (hintType) {
        QualityHintType.LOCKED, QualityHintType.GOOD -> Color.White
        QualityHintType.TOO_MUCH_GLARE, QualityHintType.LOW_LIGHT_TORCH -> SlateInk
        QualityHintType.HOLD_STEADY, QualityHintType.MOVE_CLOSER -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        shadowElevation = 4.dp,
        modifier = modifier
            .semantics { contentDescription = hintType.message }
            .testTag("quality_hint_banner")
    ) {
        Text(
            text = hintType.message,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun FieldRow(
    label: String,
    value: String,
    isValid: Boolean,
    validationNote: String? = null,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFolioSemanticColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                Icon(
                    imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = if (isValid) "$label valid" else (validationNote ?: "$label needs review"),
                    tint = if (isValid) semantic.scanLockGreen else semantic.qualityWarningAmber
                )
            },
            supportingText = if (!isValid && !validationNote.isNullOrBlank()) {
                { Text(text = validationNote, color = semantic.qualityWarningAmber) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("field_row_${label.lowercase().replace(" ", "_")}")
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    @DrawableRes illustrationRes: Int? = null,
    icon: ImageVector = Icons.Default.Description,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(FolioSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (illustrationRes != null) {
            Image(
                painter = painterResource(id = illustrationRes),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(FolioSpacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(FolioSpacing.xs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 340.dp)
        )
        if (ctaLabel != null && onCtaClick != null) {
            Spacer(modifier = Modifier.height(FolioSpacing.md))
            Button(
                onClick = onCtaClick,
                modifier = Modifier
                    .heightIn(min = FolioSpacing.minTouchTarget)
                    .testTag("empty_state_cta")
            ) {
                Text(ctaLabel)
            }
        }
    }
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalReduceMotion.current
    val alpha = if (reduceMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val anim by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 280),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeleton_alpha"
        )
        anim
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(FolioSpacing.cardCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onMove: (() -> Unit)? = null,
    onTag: (() -> Unit)? = null,
    onMerge: (() -> Unit)? = null,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier.testTag("selection_close_btn")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
        },
        actions = {
            if (onMove != null) {
                IconButton(onClick = onMove, modifier = Modifier.testTag("selection_move_btn")) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move selected")
                }
            }
            if (onTag != null) {
                IconButton(onClick = onTag, modifier = Modifier.testTag("selection_tag_btn")) {
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Tag selected")
                }
            }
            if (onMerge != null && selectedCount >= 2) {
                IconButton(onClick = onMerge, modifier = Modifier.testTag("selection_merge_btn")) {
                    Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = "Merge selected")
                }
            }
            IconButton(onClick = onExport, modifier = Modifier.testTag("selection_export_btn")) {
                Icon(Icons.Default.IosShare, contentDescription = "Export selected")
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("selection_delete_btn")) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete selected",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

fun DocumentType.toIcon(): ImageVector = when (this) {
    DocumentType.DOCUMENT -> Icons.Default.Description
    DocumentType.ID_CARD -> Icons.Default.Badge
    DocumentType.PASSPORT -> Icons.Default.TravelExplore
    DocumentType.BUSINESS_CARD -> Icons.Default.ContactMail
}

fun DocumentType.shortBadge(): String = when (this) {
    DocumentType.DOCUMENT -> "DOC"
    DocumentType.ID_CARD -> "ID"
    DocumentType.PASSPORT -> "PASSPORT"
    DocumentType.BUSINESS_CARD -> "CARD"
}

fun CaptureMode.toIcon(): ImageVector = when (this) {
    CaptureMode.DOCUMENT -> Icons.Default.Description
    CaptureMode.ID_CARD -> Icons.Default.Badge
    CaptureMode.PASSPORT -> Icons.Default.TravelExplore
    CaptureMode.BUSINESS_CARD -> Icons.Default.ContactMail
    CaptureMode.OTHER_ID -> Icons.Default.Badge
}
