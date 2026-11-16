package com.folioscan.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folioscan.core.database.DocumentRepository
import com.folioscan.core.model.DocumentType
import com.folioscan.core.model.Folder
import com.folioscan.core.model.SearchFilter
import com.folioscan.core.model.SearchResultItem
import com.folioscan.core.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter(),
    val isSearching: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val tags: List<Tag> = emptyList()
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: DocumentRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SearchFilter())

    private val debouncedResults = combine(
        query.debounce(300L),
        filter
    ) { q, f -> q to f }
        .flatMapLatest { (q, f) ->
            flow {
                if (q.isBlank()) {
                    emit(false to emptyList())
                } else {
                    emit(true to emptyList())
                    val hits = repository.search(q, f)
                    emit(false to hits)
                }
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        filter,
        debouncedResults,
        repository.observeRecentSearches(),
        combine(repository.observeAllFolders(), repository.observeAllTags()) { f, t -> f to t }
    ) { q, f, searchingAndHits, recent, foldersAndTags ->
        SearchUiState(
            query = q,
            filter = f,
            isSearching = searchingAndHits.first,
            results = searchingAndHits.second,
            recentSearches = recent,
            folders = foldersAndTags.first,
            tags = foldersAndTags.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun toggleTypeFilter(type: DocumentType?) {
        val current = filter.value.type
        filter.value = filter.value.copy(type = if (current == type) null else type)
    }

    fun setFolderFilter(folderId: String?) {
        filter.value = filter.value.copy(folderId = if (filter.value.folderId == folderId) null else folderId)
    }

    fun setTagFilter(tagId: String?) {
        filter.value = filter.value.copy(tagId = if (filter.value.tagId == tagId) null else tagId)
    }

    fun toggleLast30DaysFilter() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
        filter.value = filter.value.copy(
            fromTimestamp = if (filter.value.fromTimestamp == null) thirtyDaysAgo else null
        )
    }

    fun clearRecentSearches() {
        viewModelScope.launch { repository.clearRecentSearches() }
    }
}
