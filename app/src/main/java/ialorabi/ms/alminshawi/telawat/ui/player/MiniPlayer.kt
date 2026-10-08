package ialorabi.ms.alminshawi.telawat.ui.player

import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ialorabi.ms.alminshawi.telawat.R
import ialorabi.ms.alminshawi.telawat.data.Surah
import ialorabi.ms.alminshawi.telawat.player.PlayerViewModel
import java.util.Locale

// Separate restart scope: position ticks (10x/sec) recompose only the mini player, not the whole screen
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MiniPlayer(
    surah: Surah,
    localizedSurahNames: Array<String>,
    viewModel: PlayerViewModel,
    isPlaying: Boolean,
    isBuffering: Boolean,
    downloadingSurahs: Set<Int>,
    downloadingProgress: Map<Int, Float>,
    cachedSurahIds: Set<Int>,
    onOpenPlayer: () -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val sleepTimerMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val isEndOfSurahTimer by viewModel.sleepTimerEndOfSurah.collectAsState()
    val isTimerOn = sleepTimerMs > 0 || isEndOfSurahTimer
    val isAutoPlayNext by viewModel.autoPlayNext.collectAsState()
    val isAutoPlayReversed by viewModel.autoPlayReversed.collectAsState()
    val isRepeatOn by viewModel.repeatMode.collectAsState()
    val playbackProgress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
    val pendingDownloadId by viewModel.pendingDownloadSurahId.collectAsState()
    val isCurrentDownloading = pendingDownloadId != null || downloadingSurahs.contains(surah.id)
    val currentDlProgress = pendingDownloadId?.let { downloadingProgress[it] }
        ?: downloadingProgress[surah.id]
        ?: 0f
    val localizedName = localizedSurahNames.getOrElse(surah.id - 1) { _ -> surah.name }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size -> onHeightChanged(size.height) },
        contentAlignment = Alignment.BottomCenter
    ) {
        var showSleepTimerPicker by remember { mutableStateOf(false) }
        val selectedTimerMinutes by viewModel.sleepTimerSelectedMinutes.collectAsState()

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clickable { onOpenPlayer() },
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
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (!isTimerOn) MaterialTheme.colorScheme.primary
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
                                    color = if (!isTimerOn) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isEndOfSurahTimer) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        viewModel.setSleepTimerEndOfSurah()
                                        showSleepTimerPicker = false
                                    }
                            ) {
                                Text(
                                    text = stringResource(R.string.sleep_timer_end_of_surah),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isEndOfSurahTimer) MaterialTheme.colorScheme.onPrimary
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
                                        text = stringResource(R.string.sleep_timer_minutes_short, minutes),
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
                                        text = "${surah.id}",
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
                                val revelationText = if (surah.revelationType == ialorabi.ms.alminshawi.telawat.data.RevelationType.MAKKI)
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
                                        text = stringResource(R.string.juz_label, surah.juz),
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
                                            if (showSleepTimerPicker || isTimerOn) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                                        )
                                        .clickable { showSleepTimerPicker = !showSleepTimerPicker }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bedtime,
                                        contentDescription = null,
                                        tint = if (showSleepTimerPicker || isTimerOn) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    if (isEndOfSurahTimer) {
                                        Text(
                                            text = stringResource(R.string.sleep_timer_active_end_of_surah),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else if (sleepTimerMs > 0) {
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
                                    .clickable { viewModel.cycleAutoPlay() }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Icon(
                                            imageVector = if (isAutoPlayReversed) Icons.AutoMirrored.Rounded.Sort else Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = stringResource(autoPlayLabel(isAutoPlayNext, isAutoPlayReversed)),
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
                            val miniCurrentId = surah.id
                            val isMiniFirst = miniCurrentId <= 1
                            val isMiniPrevDownloaded = (miniCurrentId - 1) in cachedSurahIds

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = !isMiniFirst) { viewModel.playPreviousSurah(autoPlay = true) },
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
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = stringResource(R.string.rewind),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = !isMiniLast) { viewModel.playNextSurah(autoPlay = true) },
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
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = stringResource(R.string.forward),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
