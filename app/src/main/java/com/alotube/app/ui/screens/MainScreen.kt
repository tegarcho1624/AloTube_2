package com.alotube.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alotube.app.R
import com.alotube.app.data.model.VideoItem
import com.alotube.app.ui.components.SearchTopBar
import com.alotube.app.ui.components.StatusMessage
import com.alotube.app.ui.components.VideoListItem
import com.alotube.app.viewmodel.SearchError
import com.alotube.app.viewmodel.SearchViewModel

@StringRes
private fun SearchError.messageRes(): Int = when (this) {
    SearchError.NETWORK -> R.string.error_network
    SearchError.BLOCKED -> R.string.error_blocked
    SearchError.EXTRACTION -> R.string.error_extraction
    SearchError.UNKNOWN -> R.string.error_unknown
}

@Composable
fun MainScreen(
    onVideoClick: (VideoItem) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Copias locales para poder hacer smart-cast
    val error = state.error
    val results = state.results

    Scaffold(
        topBar = {
            SearchTopBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {
                    focusManager.clearFocus()
                    viewModel.search()
                },
                onClear = viewModel::clearQuery
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                error != null -> StatusMessage(
                    text = stringResource(error.messageRes()),
                    actionLabel = stringResource(R.string.retry),
                    onAction = viewModel::search,
                    modifier = Modifier.align(Alignment.Center)
                )

                !state.hasSearched -> StatusMessage(
                    text = stringResource(R.string.hint_start),
                    modifier = Modifier.align(Alignment.Center)
                )

                results.isEmpty() -> StatusMessage(
                    text = stringResource(R.string.no_results, state.lastQuery),
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(results) { video ->
                        VideoListItem(video = video, onClick = { onVideoClick(video) })
                    }
                }
            }
        }
    }
}
