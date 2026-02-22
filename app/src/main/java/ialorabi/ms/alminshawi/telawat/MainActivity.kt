package ialorabi.ms.alminshawi.telawat

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.*
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import ialorabi.ms.alminshawi.telawat.data.Surah
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import ialorabi.ms.alminshawi.telawat.player.PlayerViewModel

class MainActivity : AppCompatActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlMinshawiTheme {
                QuranAppUi(playerViewModel)
            }
        }
        playerViewModel.initializeController(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerViewModel.releaseController()
    }

    override fun onResume() {
        super.onResume()
        playerViewModel.refreshCachedSurahs()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuranAppUi(viewModel: PlayerViewModel) {
    val surahs = SurahRepository.surahs
    val currentSurahId by viewModel.currentPlayingSurahId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val downloadingSurahs by viewModel.downloadingSurahs.collectAsState()
    val cachedSurahIds by viewModel.cachedSurahIds.collectAsState()
    val downloadingProgress by viewModel.downloadingProgress.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.refreshCachedSurahs()
    }
    
    val context = LocalContext.current

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var downloadFilter by remember { mutableStateOf(DownloadFilter.ALL) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val localizedSurahNames = stringArrayResource(R.array.surah_names)

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
        
        matchesSearch && matchesDownload
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
        },
        bottomBar = {
            if (currentSurahId != null && !showBottomSheet) {
                val currentSurah = surahs.find { it.id == currentSurahId }
                currentSurah?.let {
                    BottomPlayerBar(
                        surah = it,
                        localizedName = localizedSurahNames.getOrElse(it.id - 1) { _ -> it.name },
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        downloadProgress = downloadingProgress.values.maxOrNull(),
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onBarClick = { showBottomSheet = true }
                    )
                }
            } else if (currentSurahId == null && downloadingSurahs.isNotEmpty()) {
                val dlSurahId = downloadingSurahs.first()
                val dlSurah = surahs.find { it.id == dlSurahId }
                dlSurah?.let {
                    val dlName = localizedSurahNames.getOrElse(it.id - 1) { _ -> it.name }
                    val dlProgress = downloadingProgress[dlSurahId] ?: 0f
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 8.dp
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { dlProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .align(Alignment.TopCenter),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${stringResource(R.string.surah_prefix)} $dlName",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "${(dlProgress * 100).toInt()}%",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
                onQueryChange = { searchQuery = it },
                isActive = isSearchActive,
                onActiveChange = { isSearchActive = it },
                downloadFilter = downloadFilter,
                onFilterChange = { downloadFilter = it }
            )

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
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
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
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                        isDownloaded = cachedSurahIds.contains(surah.id),
                        onPlayClick = { viewModel.playSurah(surah) },
                        onPauseClick = { viewModel.togglePlayPause() },
                        onDownloadClick = { viewModel.downloadSurah(surah) }
                    )
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
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = stringResource(R.string.help_filter_desc))
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = stringResource(R.string.help_download_desc))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(2.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = stringResource(R.string.help_downloading_desc))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = stringResource(R.string.help_downloaded_desc))
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = stringResource(R.string.help_auto_play_desc))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = stringResource(R.string.help_auto_play_reverse_desc))
                    }
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.help_streaming_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
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
    isActive: Boolean, 
    onActiveChange: (Boolean) -> Unit,
    downloadFilter: DownloadFilter,
    onFilterChange: (DownloadFilter) -> Unit
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

@Composable
fun SurahItem(
    surah: Surah,
    localizedName: String,
    isCurrentSelected: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isCurrentSelected) onPauseClick() else onPlayClick()
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
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

            Text(
                text = "${stringResource(R.string.surah_prefix)} $localizedName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

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

@Composable
fun BottomPlayerBar(
    surah: Surah,
    localizedName: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    downloadProgress: Float?,
    onPlayPauseClick: () -> Unit,
    onBarClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBarClick() },
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 8.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (downloadProgress != null) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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

    val progress = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                            String.format(stringResource(R.string.sleep_timer_active), String.format("%02d:%02d", remainMin, remainSec))
                        } else {
                            stringResource(R.string.sleep_timer_off_label)
                        },
                        color = if (sleepTimerMs > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    viewModel.seekTo((newProgress * duration).toLong())
                },
                onValueChangeFinished = {
                    viewModel.finishSeek()
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(currentPos), color = Color.Gray, fontSize = 12.sp)
                Text(text = formatTime(duration), color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

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
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                            Icon(
                                imageVector = if (isAutoPlayReversed) Icons.AutoMirrored.Rounded.Sort else Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = stringResource(R.string.auto_play_next),
                                tint = if (isAutoPlayNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
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

            Spacer(modifier = Modifier.weight(1f))

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
                    val path = androidx.compose.ui.graphics.Path().apply {
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
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
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
    return String.format("%02d:%02d", minutes, seconds)
}

enum class DownloadFilter {
    ALL,
    DOWNLOADED,
    NOT_DOWNLOADED
}