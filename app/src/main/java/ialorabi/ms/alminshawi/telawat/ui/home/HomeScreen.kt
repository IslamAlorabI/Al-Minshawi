package ialorabi.ms.alminshawi.telawat.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import ialorabi.ms.alminshawi.telawat.GuideActivity
import ialorabi.ms.alminshawi.telawat.R
import ialorabi.ms.alminshawi.telawat.SettingsActivity
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import ialorabi.ms.alminshawi.telawat.data.SurahSearch
import ialorabi.ms.alminshawi.telawat.player.PlayerViewModel
import ialorabi.ms.alminshawi.telawat.ui.player.FullScreenPlayer
import ialorabi.ms.alminshawi.telawat.ui.player.MiniPlayer
import ialorabi.ms.alminshawi.telawat.ui.player.formatTime
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlMinshawiAppUi(viewModel: PlayerViewModel, openPlayerRequest: kotlinx.coroutines.flow.MutableStateFlow<Boolean>) {
    val surahs = SurahRepository.surahs
    val currentSurahId by viewModel.currentPlayingSurahId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val downloadingSurahs by viewModel.downloadingSurahs.collectAsState()
    val cachedSurahIds by viewModel.cachedSurahIds.collectAsState()
    val favoriteSurahIds by viewModel.favoriteSurahIds.collectAsState()
    val downloadingProgress by viewModel.downloadingProgress.collectAsState()
    val pendingDownloadSurahId by viewModel.pendingDownloadSurahId.collectAsState()
    
    val context = LocalContext.current

    val sharedPref = remember { context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE) }
    val initialScrollIndex = remember { sharedPref.getInt("scroll_index", 0) }
    val initialScrollOffset = remember { sharedPref.getInt("scroll_offset", 0) }
    val listState = rememberLazyListState(initialScrollIndex, initialScrollOffset)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                delay(500.milliseconds)
                sharedPref.edit {
                    putInt("scroll_index", index)
                    putInt("scroll_offset", offset)
                }
            }
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    var showJuzSheet by remember { mutableStateOf(false) }
    var showFavoriteHint by remember { mutableStateOf(!sharedPref.getBoolean("favorite_hint_dismissed", false)) }
    val dismissFavoriteHint = {
        showFavoriteHint = false
        sharedPref.edit { putBoolean("favorite_hint_dismissed", true) }
    }
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val shouldOpenPlayer by openPlayerRequest.collectAsState()
    LaunchedEffect(shouldOpenPlayer, currentSurahId) {
        if (shouldOpenPlayer && currentSurahId != null) {
            showBottomSheet = true
            openPlayerRequest.value = false
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var downloadFilter by remember { mutableStateOf(DownloadFilter.ALL) }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    val localizedSurahNames = stringArrayResource(R.array.surah_names)

    val savedScrollIndex = remember { mutableIntStateOf(-1) }
    val savedScrollOffset = remember { mutableIntStateOf(0) }
    val isFilterActive = searchQuery.isNotEmpty() || downloadFilter != DownloadFilter.ALL || showFavoritesOnly

    val filteredSurahs = surahs.filter { surah ->
        val matchesSearch = SurahSearch.matches(
            surah,
            localizedSurahNames.getOrElse(surah.id - 1) { _ -> surah.name },
            searchQuery
        )
        
        val matchesDownload = when (downloadFilter) {
            DownloadFilter.ALL -> true
            DownloadFilter.DOWNLOADED -> cachedSurahIds.contains(surah.id)
            DownloadFilter.NOT_DOWNLOADED -> !cachedSurahIds.contains(surah.id)
        }

        val matchesFavorite = if (showFavoritesOnly) favoriteSurahIds.contains(surah.id) else true
        
        matchesSearch && matchesDownload && matchesFavorite
    }

    LaunchedEffect(isFilterActive) {
        if (!isFilterActive && savedScrollIndex.intValue >= 0) {
            listState.scrollToItem(savedScrollIndex.intValue, savedScrollOffset.intValue)
            savedScrollIndex.intValue = -1
        }
    }

    if (showBottomSheet && currentSurahId != null) {
        val currentSurah = surahs.find { it.id == currentSurahId }
        currentSurah?.let { surah ->
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = null,
                shape = androidx.compose.ui.graphics.RectangleShape,
                modifier = Modifier.fillMaxSize()
            ) {
                FullScreenPlayer(
                    surah = surah,
                    localizedName = localizedSurahNames.getOrElse(surah.id - 1) { _ -> surah.name },
                    localizedSurahNames = localizedSurahNames,
                    viewModel = viewModel
                )
            }
        }
    }

    if (showJuzSheet) {
        JuzDownloadSheet(
            viewModel = viewModel,
            cachedSurahIds = cachedSurahIds,
            downloadingSurahs = downloadingSurahs,
            onDismiss = { showJuzSheet = false }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val downloadLimitMsg = stringResource(R.string.download_limit_reached)
    val downloadQueueLimitMsg = stringResource(R.string.download_limit_queued_reached)
    val downloadFailedMsg = stringResource(R.string.download_failed)
    val playbackErrorMsg = stringResource(R.string.playback_error)
    LaunchedEffect(Unit) {
        viewModel.downloadLimitReached.collect {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = downloadLimitMsg,
                duration = SnackbarDuration.Short
            )
        }
    }
    LaunchedEffect(Unit) {
        viewModel.downloadQueueLimitReached.collect {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = downloadQueueLimitMsg,
                duration = SnackbarDuration.Short
            )
        }
    }
    LaunchedEffect(Unit) {
        viewModel.downloadFailed.collect {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = downloadFailedMsg,
                duration = SnackbarDuration.Short
            )
        }
    }
    val resumedFromMsg = stringResource(R.string.resumed_from)
    val startOverLabel = stringResource(R.string.start_over)
    LaunchedEffect(Unit) {
        viewModel.resumedAt.collect { positionMs ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = String.format(resumedFromMsg, formatTime(positionMs)),
                actionLabel = startOverLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restartCurrentSurah()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.playbackError.collect {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = playbackErrorMsg,
                duration = SnackbarDuration.Short
            )
        }
    }

    val showFloatingPlayer = currentSurahId != null
    var playerHeightPx by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                IconButton(onClick = {
                                    context.startActivity(Intent(context, GuideActivity::class.java))
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = stringResource(R.string.guide_title),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.sheikh_name),
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = ialorabi.ms.alminshawi.telawat.ui.theme.FustatFontFamily,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.app_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = ialorabi.ms.alminshawi.telawat.ui.theme.FustatFontFamily,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                IconButton(onClick = {
                                    context.startActivity(Intent(context, SettingsActivity::class.java))
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = stringResource(R.string.settings),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        SearchBarSection(
                            query = searchQuery,
                            onQueryChange = {
                                if (!isFilterActive) {
                                    savedScrollIndex.intValue = listState.firstVisibleItemIndex
                                    savedScrollOffset.intValue = listState.firstVisibleItemScrollOffset
                                }
                                searchQuery = it
                            },
                            downloadFilter = downloadFilter,
                            onFilterChange = {
                                if (!isFilterActive) {
                                    savedScrollIndex.intValue = listState.firstVisibleItemIndex
                                    savedScrollOffset.intValue = listState.firstVisibleItemScrollOffset
                                }
                                downloadFilter = it
                            },
                            showFavoritesOnly = showFavoritesOnly,
                            onFavoritesToggle = {
                                if (!isFilterActive) {
                                    savedScrollIndex.intValue = listState.firstVisibleItemIndex
                                    savedScrollOffset.intValue = listState.firstVisibleItemScrollOffset
                                }
                                showFavoritesOnly = it
                            }
                        )

                }

                HorizontalDivider(
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = if (showFloatingPlayer && playerHeightPx > 0) {
                                val navBarPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
                                with(LocalDensity.current) { (playerHeightPx - navBarPx).coerceAtLeast(0).toDp() } + 8.dp
                            } else 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    if (!isFilterActive) {
                        item(key = "list_header") {
                            HomeListHeader(
                                showFavoriteHint = showFavoriteHint,
                                onDismissFavoriteHint = dismissFavoriteHint,
                                onOpenJuzDownloads = { showJuzSheet = true }
                            )
                        }
                    }
                    items(filteredSurahs, key = { it.id }) { surah ->
                        SurahItem(
                            surah = surah,
                            localizedName = localizedSurahNames.getOrElse(surah.id - 1) { _ -> surah.name },
                            isCurrentSelected = currentSurahId == surah.id,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering && currentSurahId == surah.id,
                            isDownloading = downloadingSurahs.contains(surah.id),
                            downloadProgress = downloadingProgress[surah.id] ?: 0f,
                            isDownloaded = cachedSurahIds.contains(surah.id),
                            isFavorite = favoriteSurahIds.contains(surah.id),
                            onPlayClick = { viewModel.playSurah(surah, autoPlay = true) },
                            onPauseClick = { viewModel.togglePlayPause() },
                            onDownloadClick = { viewModel.downloadSurah(surah) },
                            onCancelDownload = { viewModel.cancelDownload(surah.id) },
                            onToggleFavorite = {
                                viewModel.toggleFavorite(surah.id)
                                if (showFavoriteHint) dismissFavoriteHint()
                            }
                        )
                    }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp)
                    ) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    ActiveDownloadIndicator(
                        downloadingSurahIds = downloadingSurahs,
                        downloadingProgress = downloadingProgress,
                        pendingDownloadSurahId = pendingDownloadSurahId,
                        localizedSurahNames = localizedSurahNames,
                        onCancelDownload = { viewModel.cancelDownload(it) },
                        onCancelAll = { viewModel.cancelAllDownloads() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = if (showFloatingPlayer && playerHeightPx > 0) {
                                    val navBarPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
                                    with(LocalDensity.current) { (playerHeightPx - navBarPx).coerceAtLeast(0).toDp() } + 16.dp
                                } else 16.dp
                            )
                    )
                }
            }
        }
        if (showFloatingPlayer) {
            surahs.find { it.id == currentSurahId }?.let { currentSurah ->
                MiniPlayer(
                    surah = currentSurah,
                    localizedSurahNames = localizedSurahNames,
                    viewModel = viewModel,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    downloadingSurahs = downloadingSurahs,
                    downloadingProgress = downloadingProgress,
                    cachedSurahIds = cachedSurahIds,
                    onOpenPlayer = { showBottomSheet = true },
                    onHeightChanged = { playerHeightPx = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

}

@Composable
fun SearchBarSection(
    query: String, 
    onQueryChange: (String) -> Unit, 
    downloadFilter: DownloadFilter,
    onFilterChange: (DownloadFilter) -> Unit,
    showFavoritesOnly: Boolean,
    onFavoritesToggle: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "searchBorderColor"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(2.dp, borderColor, RoundedCornerShape(50))
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_surahs)) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = if (downloadFilter == DownloadFilter.DOWNLOADED) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onFilterChange(if (downloadFilter == DownloadFilter.DOWNLOADED) DownloadFilter.ALL else DownloadFilter.DOWNLOADED) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = stringResource(R.string.filter_downloaded),
                                tint = if (downloadFilter == DownloadFilter.DOWNLOADED) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Surface(
                        shape = CircleShape,
                        color = if (downloadFilter == DownloadFilter.NOT_DOWNLOADED) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onFilterChange(if (downloadFilter == DownloadFilter.NOT_DOWNLOADED) DownloadFilter.ALL else DownloadFilter.NOT_DOWNLOADED) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = stringResource(R.string.filter_not_downloaded),
                                tint = if (downloadFilter == DownloadFilter.NOT_DOWNLOADED) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (showFavoritesOnly) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onFavoritesToggle(!showFavoritesOnly) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (showFavoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = stringResource(R.string.filter_favorites),
                                tint = if (showFavoritesOnly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                cursorColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}

enum class DownloadFilter {
    ALL, DOWNLOADED, NOT_DOWNLOADED
}
