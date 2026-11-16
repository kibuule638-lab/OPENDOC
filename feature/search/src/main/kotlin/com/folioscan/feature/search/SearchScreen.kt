package com.folioscan.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.folioscan.core.designsystem.EmptyState
import com.folioscan.core.designsystem.FolioSpacing
import com.folioscan.core.designsystem.SkeletonBox
import com.folioscan.core.model.DocumentType
import java.io.File

@Composable
fun SearchScreen(
    onOpenDocument: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = FolioSpacing.xs)
        ) {
            // Auto-focused search field pinned at top
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search OCR text, titles, tags…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FolioSpacing.md)
                    .focusRequester(focusRequester)
                    .testTag("search_input")
            )

            // Filter chips: type, folder, tag, date
            LazyRow(
                contentPadding = PaddingValues(horizontal = FolioSpacing.md, vertical = FolioSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(FolioSpacing.xs)
            ) {
                items(DocumentType.entries) { type ->
                    FilterChip(
                        selected = state.filter.type == type,
                        onClick = { viewModel.toggleTypeFilter(type) },
                        label = { Text(type.name.replace("_", " ")) }
                    )
                }
                item {
                    FilterChip(
                        selected = state.filter.fromTimestamp != null,
                        onClick = viewModel::toggleLast30DaysFilter,
                        label = { Text("Last 30 days") }
                    )
                }
                items(state.folders, key = { "f_${it.id}" }) { folder ->
                    FilterChip(
                        selected = state.filter.folderId == folder.id,
                        onClick = { viewModel.setFolderFilter(folder.id) },
                        label = { Text("📁 ${folder.name}") }
                    )
                }
                items(state.tags, key = { "t_${it.id}" }) { tag ->
                    FilterChip(
                        selected = state.filter.tagId == tag.id,
                        onClick = { viewModel.setTagFilter(tag.id) },
                        label = { Text("#${tag.name}") }
                    )
                }
            }

            when {
                state.query.isBlank() -> {
                    if (state.recentSearches.isEmpty()) {
                        EmptyState(
                            title = "Search offline scans",
                            subtitle = "Matches full-text OCR, document titles, and tags. Private Vault items remain hidden.",
                            icon = Icons.Default.Search,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = FolioSpacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent searches", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = viewModel::clearRecentSearches) { Text("Clear") }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = FolioSpacing.md)
                        ) {
                            items(state.recentSearches) { recent ->
                                ListItem(
                                    leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                                    headlineContent = { Text(recent) },
                                    modifier = Modifier.clickable { viewModel.onQueryChange(recent) }
                                )
                            }
                        }
                    }
                }

                state.isSearching -> {
                    Column(
                        modifier = Modifier.padding(FolioSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(FolioSpacing.xs)
                    ) {
                        repeat(4) {
                            SkeletonBox(modifier = Modifier.fillMaxWidth().height(72.dp))
                        }
                    }
                }

                state.results.isEmpty() -> {
                    EmptyState(
                        title = "No matching documents",
                        subtitle = "Try another keyword or clear active filters.",
                        icon = Icons.Default.Search,
                        modifier = Modifier.weight(1f)
                    )
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(FolioSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(FolioSpacing.xs),
                        modifier = Modifier.testTag("search_results_list")
                    ) {
                        items(state.results, key = { it.document.id }) { item ->
                            val highlightBg = MaterialTheme.colorScheme.primaryContainer
                            val snippetAnnotated = buildAnnotatedString {
                                val text = item.snippet
                                val q = state.query.trim()
                                val idx = if (q.isNotEmpty()) text.indexOf(q, ignoreCase = true) else -1
                                if (idx >= 0) {
                                    append(text.substring(0, idx))
                                    withStyle(SpanStyle(background = highlightBg, fontWeight = FontWeight.Bold)) {
                                        append(text.substring(idx, idx + q.length))
                                    }
                                    append(text.substring(idx + q.length))
                                } else {
                                    append(text)
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenDocument(item.document.id) }
                                    .testTag("search_result_${item.document.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(FolioSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val thumb = item.document.coverThumbPath?.let { File(it) }
                                    if (thumb != null && thumb.exists()) {
                                        AsyncImage(
                                            model = thumb,
                                            contentDescription = item.document.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(Modifier.width(FolioSpacing.sm))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.document.title,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        if (item.snippet.isNotBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = snippetAnnotated,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
