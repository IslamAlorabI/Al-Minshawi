package ialorabi.ms.alminshawi.telawat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import ialorabi.ms.alminshawi.telawat.ui.theme.AlMinshawiTheme
import ialorabi.ms.alminshawi.telawat.player.PlaybackService
import android.content.Context
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlMinshawiTheme {
                val sharedPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

                var currentLang by remember {
                    mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
                }
                var currentTheme by remember {
                    mutableStateOf(sharedPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
                }
                var cacheSizeBytes by remember {
                    mutableStateOf(0L)
                }
                var showClearAllDialog by remember { mutableStateOf(false) }
                var showManageCacheSheet by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    cacheSizeBytes = PlaybackService.getCacheSize(this@SettingsActivity)
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.settings)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.close))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_language),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        SettingOption(
                            label = stringResource(R.string.system_default),
                            icon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            isSelected = currentLang.isEmpty(),
                            onClick = {
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                                currentLang = ""
                            }
                        )

                        SettingOption(
                            label = stringResource(R.string.arabic),
                            isSelected = currentLang.startsWith("ar"),
                            onClick = {
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
                                currentLang = "ar"
                            }
                        )

                        SettingOption(
                            label = stringResource(R.string.english),
                            isSelected = currentLang.startsWith("en"),
                            onClick = {
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                                currentLang = "en"
                            }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = stringResource(R.string.app_theme),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        SettingOption(
                            label = stringResource(R.string.system_default_mode),
                            icon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            isSelected = currentTheme == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                            onClick = {
                                sharedPrefs.edit().putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM).apply()
                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                                currentTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                            }
                        )

                        SettingOption(
                            label = stringResource(R.string.theme_dark),
                            icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            isSelected = currentTheme == AppCompatDelegate.MODE_NIGHT_YES,
                            onClick = {
                                sharedPrefs.edit().putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES).apply()
                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                                currentTheme = AppCompatDelegate.MODE_NIGHT_YES
                            }
                        )

                        SettingOption(
                            label = stringResource(R.string.theme_light),
                            icon = { Icon(Icons.Rounded.LightMode, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            isSelected = currentTheme == AppCompatDelegate.MODE_NIGHT_NO,
                            onClick = {
                                sharedPrefs.edit().putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO).apply()
                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                currentTheme = AppCompatDelegate.MODE_NIGHT_NO
                            }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = stringResource(R.string.storage),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        val cacheText = String.format(Locale.US, "%.1f MB", cacheSizeBytes / (1024f * 1024f))

                        SettingOption(
                            label = stringResource(R.string.manage_cache),
                            icon = { Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            isSelected = false,
                            onClick = { showManageCacheSheet = true }
                        )

                        SettingOption(
                            label = "${stringResource(R.string.clear_cache)} ($cacheText)",
                            icon = { Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            isSelected = false,
                            onClick = { showClearAllDialog = true }
                        )
                    }

                    if (showClearAllDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearAllDialog = false },
                            title = { Text(stringResource(R.string.clear_cache_confirm_title)) },
                            text = { Text(stringResource(R.string.clear_cache_confirm_message)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        PlaybackService.clearCache(this@SettingsActivity)
                                        cacheSizeBytes = PlaybackService.getCacheSize(this@SettingsActivity)
                                        showClearAllDialog = false
                                    }
                                ) {
                                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearAllDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }

                    if (showManageCacheSheet) {
                        ModalBottomSheet(onDismissRequest = { showManageCacheSheet = false }) {
                            var cachedSurahs by remember { mutableStateOf(PlaybackService.getCachedSurahs()) }
                            val localizedNames = stringArrayResource(R.array.surah_names)
                            val snackbarHostState = remember { SnackbarHostState() }
                            val scope = rememberCoroutineScope()
                            val undoText = stringResource(R.string.undo)
                            val deletedText = stringResource(R.string.surah_deleted)

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.manage_cache),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    if (cachedSurahs.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.no_cached_surahs),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(vertical = 32.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        LazyColumn {
                                            items(
                                                items = cachedSurahs,
                                                key = { it.id }
                                            ) { surah ->
                                                val dismissState = rememberSwipeToDismissBoxState(
                                                    confirmValueChange = { value ->
                                                        if (value != SwipeToDismissBoxValue.Settled) {
                                                            val removedSurah = surah
                                                            PlaybackService.removeSurahCache(removedSurah)
                                                            cachedSurahs = PlaybackService.getCachedSurahs()
                                                            cacheSizeBytes = PlaybackService.getCacheSize(this@SettingsActivity)
                                                            scope.launch {
                                                                val result = snackbarHostState.showSnackbar(
                                                                    message = deletedText,
                                                                    actionLabel = undoText,
                                                                    duration = SnackbarDuration.Short
                                                                )
                                                                if (result == SnackbarResult.ActionPerformed) {
                                                                    // Undo is not possible after cache removal,
                                                                    // the file will be re-cached on next play
                                                                }
                                                            }
                                                            true
                                                        } else false
                                                    }
                                                )
                                                SwipeToDismissBox(
                                                    state = dismissState,
                                                    backgroundContent = {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(MaterialTheme.colorScheme.errorContainer)
                                                                .padding(horizontal = 20.dp),
                                                            contentAlignment = Alignment.CenterEnd
                                                        ) {
                                                            Icon(
                                                                Icons.Rounded.Delete,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                                            )
                                                        }
                                                    },
                                                    enableDismissFromStartToEnd = false
                                                ) {
                                                    val localizedName = localizedNames.getOrElse(surah.id - 1) { surah.name }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(MaterialTheme.colorScheme.surface)
                                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${surah.id}. $localizedName",
                                                            modifier = Modifier.weight(1f),
                                                            style = MaterialTheme.typography.bodyLarge
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Rounded.Delete,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                                HorizontalDivider()
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                                SnackbarHost(
                                    hostState = snackbarHostState,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
