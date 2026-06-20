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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import android.content.Context
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.painterResource
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
                QuranAppUi(playerViewModel, openPlayerRequest)
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
fun QuranAppUi(viewModel: PlayerViewModel, openPlayerRequest: kotlinx.coroutines.flow.MutableStateFlow<Boolean>) {
    val surahs = SurahRepository.surahs
    val currentSurahId by viewModel.currentPlayingSurahId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val downloadingSurahs by viewModel.downloadingSurahs.collectAsState()
    val cachedSurahIds by viewModel.cachedSurahIds.collectAsState()
    val favoriteSurahIds by viewModel.favoriteSurahIds.collectAsState()
    val downloadingProgress by viewModel.downloadingProgress.collectAsState()
    
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
    var showHelpDialog by remember { mutableStateOf(false) }
    val appSettingsPrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var isHelpBannerHidden by remember { mutableStateOf(appSettingsPrefs.getBoolean("hide_help_banner", false)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isHelpBannerHidden = appSettingsPrefs.getBoolean("hide_help_banner", false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                dragHandle = {},
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
    LaunchedEffect(Unit) {
        viewModel.downloadLimitReached.collect {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = downloadLimitMsg,
                duration = SnackbarDuration.Short
            )
        }
    }

    val showFloatingPlayer = currentSurahId != null
    val isPlayerCollapsed by viewModel.isPlayerCollapsed.collectAsState()
    var playerHeightPx by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text(stringResource(R.string.sheikh_name), fontWeight = FontWeight.Bold)
                            Text(
                                text = stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        IconButton(onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                WavyTopDecor()

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

                androidx.compose.animation.AnimatedVisibility(visible = !isHelpBannerHidden) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { showHelpDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.help_title),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    isHelpBannerHidden = true
                                    appSettingsPrefs.edit { putBoolean("hide_help_banner", true) }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.hide_help_banner),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = if (showFloatingPlayer && playerHeightPx > 0)
                                with(LocalDensity.current) { playerHeightPx.toDp() } + 32.dp
                            else 8.dp
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
                            onToggleFavorite = { viewModel.toggleFavorite(surah.id) }
                        )
                    }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp)
                    ) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }
        if (showFloatingPlayer) {
            val currentPosition by viewModel.currentPosition.collectAsState()
            val duration by viewModel.duration.collectAsState()
            val sleepTimerMs by viewModel.sleepTimerRemainingMs.collectAsState()
            val isAutoPlayNext by viewModel.autoPlayNext.collectAsState()
            val isAutoPlayReversed by viewModel.autoPlayReversed.collectAsState()
            val currentSurah = surahs.find { it.id == currentSurahId }
            val playbackProgress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
            currentSurah?.let {
                val localizedName = localizedSurahNames.getOrElse(it.id - 1) { _ -> it.name }
                val haptic = LocalHapticFeedback.current

                val collapseFraction by animateFloatAsState(
                    targetValue = if (isPlayerCollapsed) 1f else 0f,
                    animationSpec = tween(400, easing = FastOutSlowInEasing),
                    label = "collapse"
                )


                val cornerRadius = androidx.compose.ui.unit.lerp(20.dp, 32.dp, collapseFraction)
                val surfaceColor = if (collapseFraction >= 0.99f)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.secondaryContainer

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .onSizeChanged { size ->
                            if (collapseFraction < 0.01f || collapseFraction > 0.99f) {
                                playerHeightPx = size.height
                            }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .then(
                                if (collapseFraction >= 0.99f) Modifier.size(64.dp)
                                else Modifier
                                    .fillMaxWidth(
                                        androidx.compose.ui.util.lerp(1f, 0.18f, collapseFraction)
                                    )
                                    .defaultMinSize(minWidth = 64.dp, minHeight = 64.dp)
                            )
                            .clip(RoundedCornerShape(cornerRadius))
                            .combinedClickable(
                                onClick = {
                                    if (isPlayerCollapsed) viewModel.togglePlayPause()
                                    else showBottomSheet = true
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.togglePlayerCollapsed()
                                }
                            ),
                        shape = RoundedCornerShape(cornerRadius),
                        color = surfaceColor,
                        shadowElevation = if (collapseFraction >= 0.99f) 0.dp else androidx.compose.ui.unit.lerp(8.dp, 6.dp, collapseFraction),
                        tonalElevation = if (collapseFraction >= 0.99f) 0.dp else 4.dp
                    ) {
                        val expandedAlpha = (1f - collapseFraction * 3f).coerceIn(0f, 1f)
                        val collapsedAlpha = ((collapseFraction - 0.5f) * 2f).coerceIn(0f, 1f)
                        var expandedHeightPx by remember { mutableIntStateOf(0) }
                        val density = LocalDensity.current
                        val collapsedHeightPx = with(density) { 64.dp.roundToPx() }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (collapseFraction > 0f) {
                                        val expandedH = if (expandedHeightPx > 0) expandedHeightPx.toFloat() else collapsedHeightPx.toFloat()
                                        val h = androidx.compose.ui.util.lerp(
                                            expandedH,
                                            collapsedHeightPx.toFloat(),
                                            collapseFraction
                                        )
                                        Modifier.height(with(density) { h.toInt().toDp() })
                                    } else Modifier
                                )
                                .clipToBounds(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { size ->
                                        if (collapseFraction < 0.01f && size.height > 0) {
                                            expandedHeightPx = size.height
                                        }
                                    }
                                    .graphicsLayer { alpha = expandedAlpha }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                modifier = Modifier.padding(end = 6.dp)
                                            ) {
                                                Text(
                                                    text = "${it.id}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = "${stringResource(R.string.surah_prefix)} $localizedName",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                maxLines = 1
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            val revelationText = if (it.revelationType == ialorabi.ms.alminshawi.telawat.data.RevelationType.MAKKI)
                                                stringResource(R.string.revelation_makki) else stringResource(R.string.revelation_madani)
                                            Text(
                                                text = "$revelationText · ${stringResource(R.string.juz_label, it.juz)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = " · ",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.Bedtime,
                                                contentDescription = null,
                                                tint = if (sleepTimerMs > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            } else {
                                                Text(
                                                    text = stringResource(R.string.sleep_timer_off_label),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isAutoPlayNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .combinedClickable(
                                                    enabled = collapseFraction < 0.3f,
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
                                                        tint = if (isAutoPlayNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (isBuffering) {
                                            BufferingIndicator(modifier = Modifier.size(50.dp))
                                        } else {
                                            FilledIconButton(
                                                onClick = { viewModel.togglePlayPause() },
                                                modifier = Modifier.size(50.dp),
                                                enabled = collapseFraction < 0.3f
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
                                            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = if (duration > 0) formatTime(currentPosition) else "--:--",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(64.dp)
                                    .graphicsLayer { alpha = collapsedAlpha }
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    CircularProgressIndicator(
                                        progress = { playbackProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier.size(64.dp),
                                        strokeWidth = 4.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                                }
                                Surface(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shadowElevation = if (collapsedAlpha > 0f) 6.dp else 0.dp,
                                    tonalElevation = if (collapsedAlpha > 0f) 4.dp else 0.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        if (isBuffering) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPlayerCollapsed && collapseFraction >= 0.99f,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 70.dp),
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (sleepTimerMs > 0) {
                                val remainMin = (sleepTimerMs / 60000).toInt()
                                val remainSec = ((sleepTimerMs % 60000) / 1000).toInt()
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    tonalElevation = 2.dp
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bedtime,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = String.format(Locale.US, "%02d:%02d", remainMin, remainSec),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                tonalElevation = 2.dp
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = if (duration > 0) "${formatTime(currentPosition)} / ${formatTime(duration)}" else "--:-- / --:--",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.help_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = stringResource(R.string.help_tap_play_desc), style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(R.string.help_long_press_favorite_desc), style = MaterialTheme.typography.bodyMedium)
                    Text(text = stringResource(R.string.help_favorite_wave_desc), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.help_icons_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(R.string.help_download_desc), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(2.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(R.string.help_downloading_desc), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(R.string.help_downloaded_desc), style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                    Text(text = stringResource(R.string.help_filter_desc), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(R.string.help_auto_play_desc), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(R.string.help_auto_play_reverse_desc), style = MaterialTheme.typography.bodyMedium)
                    }

                }
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
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
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    TextField(
        value = query,
        onValueChange = onQueryChange,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(2.dp, borderColor, MaterialTheme.shapes.medium),
        placeholder = { Text(stringResource(R.string.search_surahs)) },
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null)
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
                            tint = if (downloadFilter == DownloadFilter.DOWNLOADED) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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
                            tint = if (downloadFilter == DownloadFilter.NOT_DOWNLOADED) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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
                            tint = if (showFavoritesOnly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
fun WavyTopDecor() {
    val waveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(vertical = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val waveWidth = 40.dp.toPx()
        val path = Path().apply {
            moveTo(0f, height / 2)
            var currentX = 0f
            while (currentX < width) {
                relativeQuadraticTo(waveWidth / 4, -height / 2, waveWidth / 2, 0f)
                relativeQuadraticTo(waveWidth / 4, height / 2, waveWidth / 2, 0f)
                currentX += waveWidth
            }
        }
        drawPath(
            path = path,
            color = waveColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    onToggleFavorite: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val cardShape = MaterialTheme.shapes.medium
    Card(
        shape = cardShape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .combinedClickable(
                onClick = { 
                    if (!isCurrentSelected) {
                        onPlayClick()
                    } else if (!isPlaying) {
                        onPauseClick() // togglePlayPause inside will resume it
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite()
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = surah.id.toString(),
                    color = MaterialTheme.colorScheme.primary,
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
                    color = if (isFavorite) MaterialTheme.colorScheme.primary else Color.Unspecified
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

            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).padding(2.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
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
            Spacer(modifier = Modifier.width(8.dp))

            if (isBuffering) {
                BufferingIndicator(modifier = Modifier.size(40.dp))
            } else {
                IconButton(
                    onClick = { if (isCurrentSelected) onPauseClick() else onPlayClick() }
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
        }
        if (isDownloading) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomPlayerBar(
    localizedName: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    sleepTimerMs: Long,
    onPlayPauseClick: () -> Unit,
    onBarClick: () -> Unit,
    onCollapse: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val playerShape = RoundedCornerShape(20.dp)
    val playbackProgress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(playerShape)
            .combinedClickable(
                onClick = { onBarClick() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCollapse()
                }
            ),
        shape = playerShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.surah_prefix)} $localizedName",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = stringResource(R.string.sheikh_short),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                if (duration > 0 || sleepTimerMs > 0) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            if (duration > 0) {
                                Text(
                                    text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (sleepTimerMs > 0) {
                                val remainMin = (sleepTimerMs / 60000).toInt()
                                val remainSec = ((sleepTimerMs % 60000) / 1000).toInt()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bedtime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format(Locale.US, "%02d:%02d", remainMin, remainSec),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                if (isBuffering) {
                    BufferingIndicator(modifier = Modifier.size(50.dp))
                } else {
                    FilledIconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LinearProgressIndicator(
                    progress = { playbackProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollapsedPlayerFab(
    progress: Float,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    sleepTimerMs: Long,
    onPlayPauseClick: () -> Unit,
    onExpand: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (sleepTimerMs > 0) {
            val remainMin = (sleepTimerMs / 60000).toInt()
            val remainSec = ((sleepTimerMs % 60000) / 1000).toInt()
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = String.format(Locale.US, "%02d:%02d", remainMin, remainSec),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            }
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = onPlayPauseClick,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onExpand()
                        }
                    ),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 6.dp,
                tonalElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        if (duration > 0) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 2.dp
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun FullScreenPlayer(surah: Surah, localizedName: String, localizedSurahNames: Array<String>, viewModel: PlayerViewModel) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val currentPos by viewModel.currentPosition.collectAsState()
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

    val progress = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val density = androidx.compose.ui.platform.LocalDensity.current
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


                Spacer(modifier = Modifier.height(24.dp))

                val discRotation = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(isPlaying) {
                    if (isPlaying) {
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

                val cookieShape = MaterialShapes.Cookie9Sided.toShape()
                Box(
                    modifier = Modifier
                        .then(
                            if (isTablet) Modifier.size(screenHeightDp * 0.30f)
                            else Modifier.fillMaxWidth(0.70f).aspectRatio(1f)
                        )
                        .clip(cookieShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(R.string.surah_prefix)} $localizedName (${surah.id})",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
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
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = null,
                            tint = if (sleepTimerMs > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (sleepTimerMs > 0) {
                                val remainMin = (sleepTimerMs / 60000).toInt()
                                val remainSec = ((sleepTimerMs % 60000) / 1000).toInt()
                                String.format(Locale.US, stringResource(R.string.sleep_timer_active), String.format(Locale.US, "%02d:%02d", remainMin, remainSec))
                            } else {
                                stringResource(R.string.sleep_timer_off_label)
                            },
                            color = if (sleepTimerMs > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Slider(
                value = if (isDownloadingForPlay) dlProgress else progress,
                onValueChange = { newProgress ->
                    if (!isDownloadingForPlay) viewModel.seekTo((newProgress * duration).toLong())
                },
                onValueChangeFinished = {
                    if (!isDownloadingForPlay) viewModel.finishSeek()
                },
                enabled = !isDownloadingForPlay,
                modifier = Modifier.fillMaxWidth(),
                colors = if (isDownloadingForPlay) SliderDefaults.colors(
                    disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                    disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    disabledThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                ) else SliderDefaults.colors()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (duration > 0) formatTime(currentPos) else "--:--", 
                    color = Color.Gray, 
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                if (isDownloadingForPlay && pendingDownloadId != null) {
                    val pendingSurah = SurahRepository.surahs.find { it.id == pendingDownloadId }
                    val pendingSurahName = pendingSurah?.let { s -> localizedSurahNames.getOrElse(s.id - 1) { s.name } } ?: ""
                    val percent = "${(dlProgress * 100).toInt()}%"
                    
                    Text(
                        text = stringResource(R.string.downloading_for_play_progress, pendingSurahName, percent),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                    )
                }

                Text(
                    text = if (duration > 0) formatTime(duration) else "--:--", 
                    color = Color.Gray, 
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.playPreviousSurah() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = "Previous Surah", modifier = Modifier.size(32.dp))
                }

                FilledTonalIconButton(
                    onClick = { viewModel.seekBackward() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay10,
                        contentDescription = stringResource(R.string.rewind),
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (isBuffering) {
                    BufferingIndicator(modifier = Modifier.size(80.dp))
                } else {
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = { viewModel.seekForward() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forward30,
                        contentDescription = stringResource(R.string.forward),
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.playNextSurah() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = "Next Surah", modifier = Modifier.size(32.dp))
                }
            }

            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = { showSleepTimerSheet = true },
                        modifier = Modifier.size(48.dp)
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
                    Surface(
                        shape = CircleShape,
                        color = if (isAutoPlayNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(48.dp)
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
                                    tint = if (isAutoPlayNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = { viewModel.toggleRepeat() },
                        modifier = Modifier.size(48.dp)
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
                        Text(
                            text = prevName ?: "",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).defaultMinSize(minWidth = 40.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
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
                        Text(
                            text = nextName ?: "",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).defaultMinSize(minWidth = 40.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
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
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
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
                        showSleepTimerSheet = false
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
                                    showSleepTimerSheet = false
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