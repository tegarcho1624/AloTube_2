package com.alotube.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alotube.app.data.model.VideoItem
import com.alotube.app.data.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

enum class SearchError {
    NETWORK, BLOCKED, EXTRACTION, UNKNOWN;

    companion object {
        fun from(t: Throwable): SearchError = when (t) {
            is ReCaptchaException -> BLOCKED
            is IOException -> NETWORK
            is ExtractionException -> EXTRACTION
            else -> UNKNOWN
        }
    }
}

data class SearchUiState(
    val query: String = "",
    val lastQuery: String = "",
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<VideoItem> = emptyList(),
    val error: SearchError? = null
)

class SearchViewModel(
    private val repository: VideoRepository = VideoRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun clearQuery() {
        _state.update { it.copy(query = "") }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return

        searchJob?.cancel()
        _state.update {
            it.copy(
                lastQuery = query,
                isLoading = true,
                hasSearched = true,
                results = emptyList(),
                error = null
            )
        }
        searchJob = viewModelScope.launch {
            repository.search(query).fold(
                onSuccess = { videos ->
                    _state.update { it.copy(isLoading = false, results = videos) }
                },
                onFailure = { t ->
                    _state.update { it.copy(isLoading = false, error = SearchError.from(t)) }
                }
            )
        }
    }
}
