package ialorabi.ms.alminshawi.telawat.ui.home

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ialorabi.ms.alminshawi.telawat.R
import ialorabi.ms.alminshawi.telawat.data.SurahRepository
import ialorabi.ms.alminshawi.telawat.player.PlayerViewModel

// Keeps room on the device for everything else
private const val FREE_SPACE_MARGIN_BYTES = 100L * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuzDownloadSheet(
    viewModel: PlayerViewModel,
    cachedSurahIds: Set<Int>,
    downloadingSurahs: Set<Int>,
    onDismiss: () -> Unit
) {
    var confirmJuz by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.download_juz_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            LazyColumn {
                items((1..30).toList(), key = { it }) { juz ->
                    val surahIds = SurahRepository.surahs
                        .filter { juz in SurahRepository.juzRange(it) }
                        .map { it.id }
                    val downloadedCount = surahIds.count { it in cachedSurahIds }
                    val isComplete = downloadedCount == surahIds.size
                    val isDownloading = surahIds.any { it in downloadingSurahs }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.juz_label, juz),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.juz_downloaded_count, downloadedCount, surahIds.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        when {
                            isComplete -> Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = stringResource(R.string.surah_downloaded),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.padding(12.dp).size(24.dp)
                            )
                            isDownloading -> CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(12.dp).size(24.dp)
                            )
                            else -> IconButton(onClick = { confirmJuz = juz }) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = stringResource(R.string.download_juz_confirm_title, juz),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    confirmJuz?.let { juz ->
        JuzDownloadConfirmDialog(
            juz = juz,
            viewModel = viewModel,
            onConfirm = {
                viewModel.downloadJuz(juz)
                confirmJuz = null
            },
            onDismiss = { confirmJuz = null }
        )
    }
}

private sealed interface SizeState {
    data object Loading : SizeState
    data class Known(val bytes: Long) : SizeState
    data object Unknown : SizeState
}

@Composable
private fun JuzDownloadConfirmDialog(
    juz: Int,
    viewModel: PlayerViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val surahs = remember(juz) { viewModel.surahsMissingInJuz(juz) }
    val freeBytes = remember(juz) { viewModel.freeSpaceBytes() }
    var sizeState by remember(juz) { mutableStateOf<SizeState>(SizeState.Loading) }

    LaunchedEffect(juz) {
        sizeState = viewModel.downloadSizeBytes(surahs)?.let { SizeState.Known(it) } ?: SizeState.Unknown
    }

    val freeText = Formatter.formatShortFileSize(context, freeBytes)
    val hasRoom = when (val state = sizeState) {
        is SizeState.Known -> state.bytes + FREE_SPACE_MARGIN_BYTES <= freeBytes
        else -> freeBytes > FREE_SPACE_MARGIN_BYTES
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_juz_confirm_title, juz)) },
        text = {
            Column {
                when (val state = sizeState) {
                    SizeState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.padding(start = 12.dp))
                        Text(stringResource(R.string.download_juz_calculating))
                    }
                    is SizeState.Known -> Text(
                        stringResource(
                            R.string.download_juz_size,
                            surahs.size,
                            Formatter.formatShortFileSize(context, state.bytes),
                            freeText
                        )
                    )
                    SizeState.Unknown -> Text(stringResource(R.string.download_juz_size_unknown, surahs.size, freeText))
                }
                if (sizeState != SizeState.Loading && !hasRoom) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.download_juz_not_enough_space),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = sizeState != SizeState.Loading && hasRoom && surahs.isNotEmpty()
            ) {
                Text(stringResource(R.string.download_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
