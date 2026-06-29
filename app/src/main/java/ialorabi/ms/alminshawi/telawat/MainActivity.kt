@file:SuppressLint("UnsafeOptInUsageError")

package ialorabi.ms.alminshawi.telawat

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import ialorabi.ms.alminshawi.telawat.ui.theme.AlMinshawiTheme

import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import android.content.Context
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayDisabled
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown

import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

import ialorabi.ms.alminshawi.telawat.data.Surah
import androidx.core.content.edit
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import java.util.Locale
import ialorabi.ms.alminshawi.telawat.player.PlayerViewModel

class MainActivity : AppCompatActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()
    val openPlayerRequest = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra("OPEN_PLAYER", false) == true) {
            openPlayerRequest.value = true
        }
        setContent {
            AlMinshawiTheme {
                AlMinshawiAppUi(playerViewModel, openPlayerRequest)
            }
        }
        playerViewModel.initializeController(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("OPEN_PLAYER", false)) {
            openPlayerRequest.value = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerViewModel.releaseController()
    }

    override fun onResume() {
        super.onResume()
        playerViewModel.refreshCachedSurahs()
        playerViewModel.syncPlayerState()
        ialorabi.ms.alminshawi.telawat.player.PlaybackService.instance?.refreshLanguage()
    }
}

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
    
    LaunchedEffect(Unit) {
        viewModel.refreshCachedSurahs()
    }
    
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
        val matchesSearch = if (searchQuery.isEmpty()) {
            true
        } else {
            val locName = localizedSurahNames.getOrElse(surah.id - 1) { _ -> surah.name }
            locName.contains(searchQuery, ignoreCase = true) || surah.name.contains(searchQuery, ignoreCase = true) || surah.id.toString().contains(searchQuery)
        }
        
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
                            onPlayClick = { viewModel.playSurah(surah) },
                            onPauseClick = { viewModel.togglePlayPause() },
                            onDownloadClick = { viewModel.downloadSurah(surah) },
                            onCancelDownload = { viewModel.cancelDownload(surah.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(surah.id) }
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
            val currentPosition by viewModel.currentPosition.collectAsState()
            val duration by viewModel.duration.collectAsState()
            val sleepTimerMs by viewModel.sleepTimerRemainingMs.collectAsState()
            val isAutoPlayNext by viewModel.autoPlayNext.collectAsState()
            val isAutoPlayReversed by viewModel.autoPlayReversed.collectAsState()
            val isRepeatOn by viewModel.repeatMode.collectAsState()
            val currentSurah = surahs.find { it.id == currentSurahId }
            val playbackProgress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
            val pendingDownloadId by viewModel.pendingDownloadSurahId.collectAsState()
            val isCurrentDownloading = pendingDownloadId != null || downloadingSurahs.contains(currentSurahId)
            val currentDlProgress = pendingDownloadId?.let { downloadingProgress[it] }
                ?: currentSurahId?.let { downloadingProgress[it] }
                ?: 0f
            currentSurah?.let {
                val localizedName = localizedSurahNames.getOrElse(it.id - 1) { _ -> it.name }
                val haptic = LocalHapticFeedback.current

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            playerHeightPx = size.height
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    var showSleepTimerPicker by remember { mutableStateOf(false) }
                    val selectedTimerMinutes by viewModel.sleepTimerSelectedMinutes.collectAsState()

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .clickable { showBottomSheet = true },
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 4.dp
                    ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showSleepTimerPicker,
                                    enter = expandVertically(tween(250), expandFrom = Alignment.Bottom) + fadeIn(tween(200)),
                                    exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Bottom) + fadeOut(tween(150))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = if (sleepTimerMs == 0L) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .clickable {
                                                    viewModel.setSleepTimer(null)
                                                    showSleepTimerPicker = false
                                                }
                                        ) {
                                            Text(
                                                text = stringResource(R.string.sleep_timer_off),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (sleepTimerMs == 0L) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                        listOf(10, 15, 20, 30, 45, 60).forEach { minutes ->
                                            val isActive = selectedTimerMinutes == minutes && sleepTimerMs > 0
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = if (isActive) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .clickable {
                                                        viewModel.setSleepTimer(minutes)
                                                        showSleepTimerPicker = false
                                                    }
                                            ) {
                                                Text(
                                                    text = "$minutes",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "${it.id}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                Text(
                                                    text = "${stringResource(R.string.surah_prefix)} $localizedName",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    maxLines = 1,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier
                                                        .padding(horizontal = 10.dp, vertical = 2.dp)
                                                        .basicMarquee(
                                                            iterations = Int.MAX_VALUE,
                                                            velocity = 30.dp
                                                        )
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = if (isRepeatOn) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .clickable { viewModel.toggleRepeat() }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Repeat,
                                                        contentDescription = stringResource(R.string.repeat_surah),
                                                        tint = if (isRepeatOn) MaterialTheme.colorScheme.onPrimary
                                                            else MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = stringResource(R.string.repeat_label),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isRepeatOn) MaterialTheme.colorScheme.onPrimary
                                                            else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            val revelationText = if (it.revelationType == ialorabi.ms.alminshawi.telawat.data.RevelationType.MAKKI)
                                                stringResource(R.string.revelation_makki) else stringResource(R.string.revelation_madani)
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                                            ) {
                                                Text(
                                                    text = revelationText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.juz_label, it.juz),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(
                                                        if (showSleepTimerPicker || sleepTimerMs > 0) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                                                    )
                                                    .clickable { showSleepTimerPicker = !showSleepTimerPicker }
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Bedtime,
                                                    contentDescription = null,
                                                    tint = if (showSleepTimerPicker || sleepTimerMs > 0) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                if (sleepTimerMs > 0) {
                                                    val remainMin = (sleepTimerMs / 60000).toInt()
                                                    val remainSec = ((sleepTimerMs % 60000) / 1000).toInt()
                                                    val timeStr = String.format(Locale.US, "%02d:%02d", remainMin, remainSec)
                                                    Text(
                                                        text = stringResource(R.string.sleep_timer_active, timeStr),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                } else {
                                                    Text(
                                                        text = stringResource(R.string.sleep_timer_off_label),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (showSleepTimerPicker) MaterialTheme.colorScheme.onPrimary
                                                            else MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isAutoPlayNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .combinedClickable(

                                                    onClick = { viewModel.toggleAutoPlayNext() },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.toggleAutoPlayReversed()
                                                    }
                                                )
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                    Icon(
                                                        imageVector = if (isAutoPlayReversed) Icons.AutoMirrored.Rounded.Sort else Icons.AutoMirrored.Rounded.QueueMusic,
                                                        contentDescription = stringResource(R.string.auto_play_next),
                                                        tint = if (isAutoPlayNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (isCurrentDownloading) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(50.dp)) {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                    CircularProgressIndicator(
                                                        progress = { currentDlProgress.coerceIn(0f, 1f) },
                                                        modifier = Modifier.size(50.dp),
                                                        strokeWidth = 3.dp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Rounded.Download,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        } else if (isBuffering) {
                                            BufferingIndicator(modifier = Modifier.size(50.dp))
                                        } else {
                                            FilledIconButton(
                                                onClick = { viewModel.togglePlayPause() },
                                                modifier = Modifier.size(50.dp),

                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                    contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp)
                                    ) {
                                        val miniCurrentId = currentSurahId ?: -1
                                        val isMiniFirst = miniCurrentId <= 1
                                        val isMiniPrevDownloaded = (miniCurrentId - 1) in cachedSurahIds
                                        val canMiniSkipPrev = !isMiniFirst && isMiniPrevDownloaded

                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .clickable(enabled = canMiniSkipPrev) { viewModel.playPreviousSurah() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isMiniFirst) {
                                                Icon(
                                                    imageVector = Icons.Rounded.SkipPrevious,
                                                    contentDescription = stringResource(R.string.rewind),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            } else if (!isMiniPrevDownloaded) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Download,
                                                    contentDescription = stringResource(R.string.rewind),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.SkipPrevious,
                                                    contentDescription = stringResource(R.string.rewind),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (duration > 0) formatTime(currentPosition) else "--:--",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        LinearProgressIndicator(
                                            progress = { playbackProgress.coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(50)),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                        )
                                        Text(
                                            text = if (duration > 0) formatTime(duration) else "--:--",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val isMiniLast = miniCurrentId >= 114 || miniCurrentId == -1
                                        val isMiniNextDownloaded = (miniCurrentId + 1) in cachedSurahIds
                                        val canMiniSkipNext = !isMiniLast && isMiniNextDownloaded

                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .clickable(enabled = canMiniSkipNext) { viewModel.playNextSurah() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isMiniLast) {
                                                Icon(
                                                    imageVector = Icons.Rounded.SkipNext,
                                                    contentDescription = stringResource(R.string.forward),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            } else if (!isMiniNextDownloaded) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Download,
                                                    contentDescription = stringResource(R.string.forward),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.SkipNext,
                                                    contentDescription = stringResource(R.string.forward),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(24.dp)
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



@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SurahItem(
    surah: Surah,
    localizedName: String,
    isCurrentSelected: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    isDownloaded: Boolean,
    isFavorite: Boolean,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val cardShape = RoundedCornerShape(20.dp)
    Card(
        shape = cardShape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .combinedClickable(
                onClick = { 
                    if (isDownloaded) {
                        if (!isCurrentSelected) {
                            onPlayClick()
                        } else if (!isPlaying) {
                            onPauseClick() // togglePlayPause inside will resume it
                        }
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite()
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val numberShape = MaterialShapes.Cookie9Sided.toShape()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(numberShape)
                    .background(
                        if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = surah.id.toString(),
                    color = if (isFavorite) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${stringResource(R.string.surah_prefix)} $localizedName",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = Color.Unspecified
                )
                val revelationText = if (surah.revelationType == ialorabi.ms.alminshawi.telawat.data.RevelationType.MAKKI)
                    stringResource(R.string.revelation_makki) else stringResource(R.string.revelation_madani)
                val juzText = stringResource(R.string.juz_label, surah.juz)
                Text(
                    text = "$revelationText · $juzText",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isDownloading) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onCancelDownload() }
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cancel_download),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isDownloaded) {
                if (isBuffering) {
                    BufferingIndicator(modifier = Modifier.size(40.dp))
                } else {
                    IconButton(
                        onClick = { if (isCurrentSelected) onPauseClick() else onPlayClick() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        val icon = if (isCurrentSelected && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
                        val desc = if (isCurrentSelected && isPlaying) stringResource(R.string.pause) else stringResource(R.string.play)
                        Icon(
                            imageVector = icon,
                            contentDescription = desc,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayDisabled,
                        contentDescription = "Not Downloaded",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = isDownloading,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
fun BufferingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "buffering")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "rotation"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Rounded.Sync,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun FullScreenPlayer(surah: Surah, localizedName: String, localizedSurahNames: Array<String>, viewModel: PlayerViewModel) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val currentPos by viewModel.currentPosition.collectAsState()
    val cachedSurahIds by viewModel.cachedSurahIds.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isRepeatOn by viewModel.repeatMode.collectAsState()
    val isAutoPlayNext by viewModel.autoPlayNext.collectAsState()
    val isAutoPlayReversed by viewModel.autoPlayReversed.collectAsState()
    val sleepTimerMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val selectedTimerMinutes by viewModel.sleepTimerSelectedMinutes.collectAsState()

    val pendingDownloadId by viewModel.pendingDownloadSurahId.collectAsState()
    val downloadingProgressMap by viewModel.downloadingProgress.collectAsState()
    val isDownloadingForPlay = pendingDownloadId != null
    val dlProgress = pendingDownloadId?.let { downloadingProgressMap[it] } ?: 0f

    val playbackProgress = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    val sliderValue = sliderPosition ?: playbackProgress
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val screenHeightDp = with(density) { windowInfo.containerSize.height.toDp() }
    val isTablet = screenWidthDp > 600.dp

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .then(
                    if (isTablet) Modifier.systemBarsPadding()
                    else Modifier.statusBarsPadding()
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                val discRotation = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    snapshotFlow { isPlaying }
                        .distinctUntilChanged()
                        .collectLatest { playing ->
                            if (playing) {
                                delay(150.milliseconds)
                                discRotation.animateTo(
                                    targetValue = discRotation.value + 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(8000, easing = LinearEasing)
                                    )
                                )
                            } else {
                                val current = discRotation.value % 360f
                                discRotation.snapTo(current)
                                discRotation.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(600, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                }

                val cookieShape = MaterialShapes.Cookie9Sided.toShape()
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE) }
                var showSheikhPhoto by remember { mutableStateOf(prefs.getBoolean("show_sheikh_photo", false)) }
                var showArtworkHint by remember { mutableStateOf(!prefs.getBoolean("artwork_hint_dismissed", false)) }
                Box(contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .then(
                                if (isTablet) Modifier.size(screenHeightDp * 0.30f)
                                else Modifier.fillMaxWidth(0.70f).aspectRatio(1f)
                            )
                            .clip(cookieShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                showSheikhPhoto = !showSheikhPhoto
                                prefs.edit { putBoolean("show_sheikh_photo", showSheikhPhoto) }
                                if (showArtworkHint) {
                                    showArtworkHint = false
                                    prefs.edit { putBoolean("artwork_hint_dismissed", true) }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(
                            targetState = showSheikhPhoto,
                            animationSpec = tween(500),
                            label = "artworkSwitch"
                        ) { showPhoto ->
                            if (showPhoto) {
                                Image(
                                    painter = painterResource(id = R.drawable.sheikh_photo),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.player_logo),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize(0.55f)
                                            .rotate(discRotation.value),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showArtworkHint,
                        enter = fadeIn(tween(400)),
                        exit = fadeOut(tween(300)),
                        modifier = Modifier.offset(y = 20.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.artwork_tap_hint),
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = {
                                        showArtworkHint = false
                                        prefs.edit { putBoolean("artwork_hint_dismissed", true) }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    var surahFontSize by remember { mutableStateOf(24.sp) }
                    Text(
                        text = "${stringResource(R.string.surah_prefix)} $localizedName (${surah.id})",
                        fontSize = surahFontSize,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        onTextLayout = { result ->
                            if (result.hasVisualOverflow && surahFontSize > 16.sp) {
                                surahFontSize = (surahFontSize.value - 1f).sp
                            }
                        }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.sheikh_name),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                val timerActive = sleepTimerMs > 0
                val chipTint by animateColorAsState(
                    targetValue = if (timerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                    animationSpec = tween(300),
                    label = "chipTint"
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = null,
                            tint = chipTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (timerActive) {
                                val remainMin = (sleepTimerMs / 60000).toInt()
                                val remainSec = ((sleepTimerMs % 60000) / 1000).toInt()
                                String.format(Locale.US, stringResource(R.string.sleep_timer_active), String.format(Locale.US, "%02d:%02d", remainMin, remainSec))
                            } else {
                                stringResource(R.string.sleep_timer_off_label)
                            },
                            color = chipTint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Slider(
                value = if (isDownloadingForPlay) dlProgress else sliderValue,
                onValueChange = { newProgress ->
                    if (!isDownloadingForPlay) {
                        if (sliderPosition == null) viewModel.beginSeek()
                        sliderPosition = newProgress
                    }
                },
                onValueChangeFinished = {
                    if (!isDownloadingForPlay) {
                        sliderPosition?.let { pos ->
                            viewModel.seekTo((pos * duration).toLong())
                            viewModel.finishSeek()
                        }
                        sliderPosition = null
                    }
                },
                enabled = !isDownloadingForPlay,
                modifier = Modifier.fillMaxWidth(),
                colors = if (isDownloadingForPlay) SliderDefaults.colors(
                    disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                    disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    disabledThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                ) else SliderDefaults.colors()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = if (duration > 0) formatTime(currentPos) else "--:--",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                if (isDownloadingForPlay && pendingDownloadId != null) {
                    val pendingSurah = SurahRepository.surahs.find { it.id == pendingDownloadId }
                    val pendingSurahName = pendingSurah?.let { s -> localizedSurahNames.getOrElse(s.id - 1) { s.name } } ?: ""
                    val percent = "${(dlProgress * 100).toInt()}%"

                    Text(
                        text = stringResource(R.string.downloading_for_play_progress, pendingSurahName, percent),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = if (duration > 0) formatTime(duration) else "--:--",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val haptic = LocalHapticFeedback.current
                val surahs = SurahRepository.surahs
                val isFirstSurah = surah.id <= 1
                val isLastSurah = surah.id >= surahs.size
                val isPrevDownloaded = (surah.id - 1) in cachedSurahIds
                val isNextDownloaded = (surah.id + 1) in cachedSurahIds

                val prevScale = remember { Animatable(1f) }
                val prevOffsetX = remember { Animatable(0f) }
                val scope = rememberCoroutineScope()

                IconButton(
                    onClick = {
                        if (isFirstSurah || !isPrevDownloaded) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                prevOffsetX.animateTo(
                                    -8f,
                                    animationSpec = tween(50)
                                )
                                prevOffsetX.animateTo(
                                    8f,
                                    animationSpec = tween(50)
                                )
                                prevOffsetX.animateTo(
                                    -4f,
                                    animationSpec = tween(50)
                                )
                                prevOffsetX.animateTo(
                                    0f,
                                    animationSpec = tween(50)
                                )
                            }
                        } else {
                            scope.launch {
                                prevScale.animateTo(0.75f, animationSpec = tween(80))
                                prevScale.animateTo(1f, animationSpec = tween(150, easing = FastOutSlowInEasing))
                            }
                            viewModel.playPreviousSurah()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = prevScale.value
                            scaleY = prevScale.value
                            translationX = prevOffsetX.value.dp.toPx()
                        }
                ) {
                    if (isFirstSurah) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.rewind),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    } else if (!isPrevDownloaded) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.rewind),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.rewind),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val rewindRotation = remember { Animatable(0f) }
                FilledTonalIconButton(
                    onClick = {
                        scope.launch {
                            rewindRotation.animateTo(-30f, animationSpec = tween(100))
                            rewindRotation.animateTo(0f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                        }
                        viewModel.seekBackward()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { rotationZ = rewindRotation.value }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay10,
                        contentDescription = stringResource(R.string.rewind),
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (isDownloadingForPlay) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            CircularProgressIndicator(
                                progress = { dlProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.size(68.dp),
                                strokeWidth = 4.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else if (isBuffering) {
                    BufferingIndicator(modifier = Modifier.size(68.dp))
                } else {
                    val playScale = remember { Animatable(1f) }
                    FilledIconButton(
                        onClick = {
                            scope.launch {
                                playScale.animateTo(0.85f, animationSpec = tween(60))
                                playScale.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                            }
                            viewModel.togglePlayPause()
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .graphicsLayer {
                                scaleX = playScale.value
                                scaleY = playScale.value
                            }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                val forwardRotation = remember { Animatable(0f) }
                FilledTonalIconButton(
                    onClick = {
                        scope.launch {
                            forwardRotation.animateTo(30f, animationSpec = tween(100))
                            forwardRotation.animateTo(0f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                        }
                        viewModel.seekForward()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { rotationZ = forwardRotation.value }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forward30,
                        contentDescription = stringResource(R.string.forward),
                        modifier = Modifier.size(24.dp)
                    )
                }

                val nextScale = remember { Animatable(1f) }
                val nextOffsetX = remember { Animatable(0f) }

                IconButton(
                    onClick = {
                        if (isLastSurah || !isNextDownloaded) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                nextOffsetX.animateTo(
                                    8f,
                                    animationSpec = tween(50)
                                )
                                nextOffsetX.animateTo(
                                    -8f,
                                    animationSpec = tween(50)
                                )
                                nextOffsetX.animateTo(
                                    4f,
                                    animationSpec = tween(50)
                                )
                                nextOffsetX.animateTo(
                                    0f,
                                    animationSpec = tween(50)
                                )
                            }
                        } else {
                            scope.launch {
                                nextScale.animateTo(0.75f, animationSpec = tween(80))
                                nextScale.animateTo(1f, animationSpec = tween(150, easing = FastOutSlowInEasing))
                            }
                            viewModel.playNextSurah()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = nextScale.value
                            scaleY = nextScale.value
                            translationX = nextOffsetX.value.dp.toPx()
                        }
                ) {
                    if (isLastSurah) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.forward),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    } else if (!isNextDownloaded) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.forward),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.forward),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val scope = rememberCoroutineScope()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val sleepScale = remember { Animatable(1f) }
                    FilledTonalIconButton(
                        onClick = {
                            scope.launch {
                                sleepScale.animateTo(0.8f, animationSpec = tween(60))
                                sleepScale.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                            }
                            showSleepTimerSheet = true
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = sleepScale.value
                                scaleY = sleepScale.value
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = stringResource(R.string.sleep_timer),
                            tint = if (sleepTimerMs > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val haptic = LocalHapticFeedback.current
                    val autoPlayScale = remember { Animatable(1f) }
                    Surface(
                        shape = CircleShape,
                        color = if (isAutoPlayNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = autoPlayScale.value
                                scaleY = autoPlayScale.value
                            }
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = {
                                    scope.launch {
                                        autoPlayScale.animateTo(0.8f, animationSpec = tween(60))
                                        autoPlayScale.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                                    }
                                    viewModel.toggleAutoPlayNext()
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleAutoPlayReversed()
                                }
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Icon(
                                    imageVector = if (isAutoPlayReversed) Icons.AutoMirrored.Rounded.Sort else Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = stringResource(R.string.auto_play_next),
                                    tint = if (isAutoPlayNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val repeatScale = remember { Animatable(1f) }
                    FilledTonalIconButton(
                        onClick = {
                            scope.launch {
                                repeatScale.animateTo(0.8f, animationSpec = tween(60))
                                repeatScale.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                            }
                            viewModel.toggleRepeat()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = repeatScale.value
                                scaleY = repeatScale.value
                            }
                    ) {
                        Icon(
                            imageVector = if (isRepeatOn) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = stringResource(R.string.repeat_surah),
                            tint = if (isRepeatOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }


            val surahs = SurahRepository.surahs
            val prevSurah = if (surah.id > 1) surahs.getOrNull(surah.id - 2) else null
            val nextSurah = if (surah.id < surahs.size) surahs.getOrNull(surah.id) else null
            val prevName = prevSurah?.let { localizedSurahNames.getOrElse(it.id - 1) { _ -> it.name } }
            val nextName = nextSurah?.let { localizedSurahNames.getOrElse(it.id - 1) { _ -> it.name } }
            val waveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (prevSurah != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp).defaultMinSize(minWidth = 40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = prevName ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "•",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).defaultMinSize(minWidth = 40.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val midY = h / 2
                    val amplitude = h * 0.35f
                    val path = Path().apply {
                        moveTo(0f, midY)
                        val waves = 4
                        val segW = w / (waves * 2)
                        for (i in 0 until waves * 2) {
                            val cpX = segW * i + segW / 2
                            val cpY = if (i % 2 == 0) midY - amplitude else midY + amplitude
                            val endX = segW * (i + 1)
                            quadraticTo(cpX, cpY, endX, midY)
                        }
                    }
                    drawPath(
                        path = path,
                        color = waveColor,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                if (nextSurah != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).defaultMinSize(minWidth = 40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = nextName ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "•",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).defaultMinSize(minWidth = 40.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showSleepTimerSheet) {
        @Suppress("DEPRECATION")
        val sleepTimerSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerSheet = false },
            sheetState = sleepTimerSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val sleepSheetScope = rememberCoroutineScope()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.sleep_timer),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, bottom = 16.dp)
                )

                val isOff = sleepTimerMs == 0L
                Surface(
                    onClick = {
                        viewModel.setSleepTimer(null)
                        sleepSheetScope.launch {
                            sleepTimerSheetState.hide()
                            showSleepTimerSheet = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isOff) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_off),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isOff) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        fontWeight = if (isOff) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val timeOptions = listOf(10, 15, 20, 30, 45, 60)
                val rows = timeOptions.chunked(2)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { minutes ->
                            val label = String.format(stringResource(R.string.sleep_timer_minutes), minutes)
                            val isActive = selectedTimerMinutes == minutes && sleepTimerMs > 0
                            Surface(
                                onClick = {
                                    viewModel.setSleepTimer(minutes)
                                    sleepSheetScope.launch {
                                        sleepTimerSheetState.hide()
                                        showSleepTimerSheet = false
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(vertical = 14.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

enum class DownloadFilter {
    ALL, DOWNLOADED, NOT_DOWNLOADED
}

@Composable
fun ActiveDownloadIndicator(
    downloadingSurahIds: Set<Int>,
    downloadingProgress: Map<Int, Float>,
    pendingDownloadSurahId: Int?,
    localizedSurahNames: Array<String>,
    onCancelDownload: (Int) -> Unit,
    onCancelAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActiveDownloads = downloadingSurahIds.isNotEmpty()
    var showPopup by remember { mutableStateOf(false) }

    LaunchedEffect(hasActiveDownloads) {
        if (!hasActiveDownloads) showPopup = false
    }

    val combinedProgress = if (downloadingSurahIds.isNotEmpty()) {
        downloadingSurahIds.map { downloadingProgress[it] ?: 0f }.average().toFloat()
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = combinedProgress,
        animationSpec = tween(300),
        label = "dl_progress"
    )

    androidx.compose.animation.AnimatedVisibility(
        visible = hasActiveDownloads,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(300)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showPopup,
                enter = expandVertically(tween(250), expandFrom = Alignment.Bottom) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Bottom) + fadeOut(tween(150))
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .widthIn(max = 320.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.active_downloads),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onCancelAll() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.cancel_all_downloads),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        val scrollState = rememberScrollState()
                        val showBottomFade by remember {
                            derivedStateOf {
                                scrollState.value < scrollState.maxValue && scrollState.maxValue > 0
                            }
                        }

                        Box {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(scrollState)
                            ) {
                                val surahs = SurahRepository.surahs
                                downloadingSurahIds.forEachIndexed { index, surahId ->
                                    val surah = surahs.find { it.id == surahId }
                                    val name = surah?.let { s ->
                                        val locName = localizedSurahNames.getOrElse(s.id - 1) { s.name }
                                        "${stringResource(R.string.surah_prefix)} $locName"
                                    } ?: "#$surahId"
                                    val progress = downloadingProgress[surahId] ?: 0f
                                    val percent = (progress * 100).toInt()
                                    val isForPlay = surahId == pendingDownloadSurahId

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isForPlay) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.PlayArrow,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            val isQueued = progress == 0f
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = if (isQueued) MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = if (isQueued) stringResource(R.string.download_queued) else "$percent%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isQueued) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .clickable { onCancelDownload(surahId) }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = stringResource(R.string.cancel_download),
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            LinearProgressIndicator(
                                                progress = { progress.coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(50)),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            )
                                        }
                                    }
                                    if (index < downloadingSurahIds.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 20.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                            if (showBottomFade) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(20.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
                                                )
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Box(contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    CircularProgressIndicator(
                        progress = { animatedProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(52.dp),
                        strokeWidth = 3.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable { showPopup = !showPopup },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.active_downloads),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}