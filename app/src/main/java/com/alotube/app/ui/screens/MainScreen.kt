package com.alotube.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.alotube.app.ui.components.AloTubeHeader
import com.alotube.app.ui.components.SearchTopBar
import com.alotube.app.ui.components.StatusMessage
import com.alotube.app.ui.components.VideoListItem
import com.alotube.app.ui.theme.accents
import com.alotube.app.viewmodel.SearchError
import com.alotube.app.viewmodel.SearchViewModel

@StringRes
private fun SearchError.messageRes(): Int = when (this) {
    SearchError.NETWORK -> R.string.error_network
    SearchError.BLOCKED -> R.string.error_blocked
    SearchError.EXTRACTION -> R.string.error_extraction
    SearchError.UNKNOWN -> R.string.error_unknown
}

/** Pestaña "Inicio": logo, buscador y resultados. El padding de barras lo pone AloTubeRoot. */
@Composable
fun MainScreen(
    onVideoClick: (List<VideoItem>, Int) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Copias locales para poder hacer smart-cast
    val error = state.error
    val results = state.results

    Column(modifier = Modifier.fillMaxSize()) {
        AloTubeHeader()
        SearchTopBar(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = {
                focusManager.clearFocus()
                viewModel.search()
            },
            onClear = viewModel::clearQuery
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.accents.accent
                )

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
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(results) { index, video ->
                        VideoListItem(video = video, onClick = { onVideoClick(results, index) })
                    }
                }
            }
        }
    }
}
