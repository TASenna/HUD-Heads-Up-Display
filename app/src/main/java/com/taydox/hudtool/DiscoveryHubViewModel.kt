package com.taydox.hudtool

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiscoveryHubUiState(
    val searchQuery: String = "",
    val selectedSource: SearchSource = SearchSource.ALL,
    val recentSearches: List<String> = emptyList(),
    val bookmarks: List<String> = listOf("https://za.pinterest.com", "https://www.artstation.com"),
    val history: List<String> = listOf("Anatomy studies", "Color palettes", "Concept art"),
    val currentTab: HubTab = HubTab.SEARCH
)

enum class HubTab { SEARCH, BOOKMARKS, HISTORY, SETTINGS }

enum class SearchSource(val displayName: String, val baseUrl: String, val searchUrl: String) {
    ALL("All Sources", "https://www.google.com", "https://www.google.com/search?q="),
    GOOGLE_IMAGES("Google Images", "https://www.google.com/imghp", "https://www.google.com/search?tbm=isch&q="),
    PINTEREST("Pinterest", "https://za.pinterest.com", "https://za.pinterest.com/search/pins/?q="),
    X_TWITTER("X (Twitter)", "https://x.com", "https://x.com/search?q="),
    MORE("More Sources", "https://www.google.com", "https://www.google.com/search?q=")
}

class DiscoveryHubViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoveryHubUiState())
    val uiState: StateFlow<DiscoveryHubUiState> = _uiState.asStateFlow()

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
    }

    fun onSourceSelected(source: SearchSource) {
        _uiState.update { it.copy(selectedSource = source) }
    }

    fun onTabSelected(tab: HubTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun onBookmarkDeleted(bookmark: String) {
        _uiState.update { state ->
            state.copy(bookmarks = state.bookmarks - bookmark)
        }
    }

    fun onSearchPerformed(query: String) {
        if (query.isNotBlank()) {
            _uiState.update { state ->
                val newHistory = (listOf(query) + state.history).distinct().take(10)
                state.copy(history = newHistory)
            }
        }
    }

    fun getDestinationUrl(): String {
        val state = _uiState.value
        return if (state.searchQuery.isBlank()) {
            state.selectedSource.baseUrl
        } else {
            "${state.selectedSource.searchUrl}${state.searchQuery}"
        }
    }
}
