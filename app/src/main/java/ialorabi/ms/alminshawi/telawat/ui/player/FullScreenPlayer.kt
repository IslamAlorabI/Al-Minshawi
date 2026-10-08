package ialorabi.ms.alminshawi.telawat.ui.player

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import ialorabi.ms.alminshawi.telawat.R
import ialorabi.ms.alminshawi.telawat.data.Surah
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import ialorabi.ms.alminshawi.telawat.player.PlayerViewModel
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    val isEndOfSurahTimer by viewModel.sleepTimerEndOfSurah.collectAsState()

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
                val timerActive = sleepTimerMs > 0 || isEndOfSurahTimer
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
                            text = if (isEndOfSurahTimer) {
                                stringResource(R.string.sleep_timer_active_end_of_surah)
                            } else if (timerActive) {
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
                        text = if (duration > 0) {
                            val displayMs = sliderPosition?.let { (it * duration).toLong() } ?: currentPos
                            formatTime(displayMs)
                        } else "--:--",
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
                        if (isFirstSurah) {
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
                            viewModel.playPreviousSurah(autoPlay = true)
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
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.rewind),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                        if (isLastSurah) {
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
                            viewModel.playNextSurah(autoPlay = true)
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
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.forward),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                            tint = if (sleepTimerMs > 0 || isEndOfSurahTimer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                            .clickable {
                                scope.launch {
                                    autoPlayScale.animateTo(0.8f, animationSpec = tween(60))
                                    autoPlayScale.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                                }
                                viewModel.cycleAutoPlay()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Icon(
                                    imageVector = if (isAutoPlayReversed) Icons.AutoMirrored.Rounded.Sort else Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = stringResource(autoPlayLabel(isAutoPlayNext, isAutoPlayReversed)),
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

                val isOff = sleepTimerMs == 0L && !isEndOfSurahTimer
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

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    onClick = {
                        viewModel.setSleepTimerEndOfSurah()
                        sleepSheetScope.launch {
                            sleepTimerSheetState.hide()
                            showSleepTimerSheet = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isEndOfSurahTimer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_end_of_surah),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isEndOfSurahTimer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        fontWeight = if (isEndOfSurahTimer) FontWeight.Bold else FontWeight.Normal,
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
