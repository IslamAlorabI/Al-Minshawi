package ialorabi.ms.alminshawi.telawat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import android.widget.Toast

import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import ialorabi.ms.alminshawi.telawat.ui.theme.AlMinshawiTheme
import ialorabi.ms.alminshawi.telawat.player.PlaybackService
import android.content.Context
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi

class SettingsActivity : AppCompatActivity() {
    private val cacheSizeBytes = mutableLongStateOf(0L)

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onResume() {
        super.onResume()
        cacheSizeBytes.longValue = PlaybackService.getCacheSize(this)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
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
                    mutableIntStateOf(sharedPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
                }
                var cacheSizeBytes by cacheSizeBytes
                var showClearAllDialog by remember { mutableStateOf(false) }
                var showManageCacheSheet by remember { mutableStateOf(false) }

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
                            .verticalScroll(rememberScrollState())
                    ) {
                      Column(modifier = Modifier.padding(16.dp)) {
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
                                sharedPrefs.edit { putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                                currentTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                            }
                        )

                        SettingOption(
                            label = stringResource(R.string.theme_dark),
                            icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            isSelected = currentTheme == AppCompatDelegate.MODE_NIGHT_YES,
                            onClick = {
                                sharedPrefs.edit { putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES) }
                                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                                currentTheme = AppCompatDelegate.MODE_NIGHT_YES
                            }
                        )

                        SettingOption(
                            label = stringResource(R.string.theme_light),
                            icon = { Icon(Icons.Rounded.LightMode, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            isSelected = currentTheme == AppCompatDelegate.MODE_NIGHT_NO,
                            onClick = {
                                sharedPrefs.edit { putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO) }
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

                        Spacer(modifier = Modifier.height(8.dp))

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

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(R.drawable.player_logo),
                                contentDescription = null,
                                modifier = Modifier.size(100.dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(R.string.copyright),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = stringResource(R.string.rights_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.made_with_love),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "v$versionName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = " · ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                val privacyShape = RoundedCornerShape(8.dp)
                                Surface(
                                    shape = privacyShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .clip(privacyShape)
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, "https://islamalorabi.github.io/al-minshawi-privacy-policy.html".toUri())
                                            try {
                                                startActivity(intent)
                                            } catch (_: android.content.ActivityNotFoundException) {
                                                Toast.makeText(this@SettingsActivity, getString(R.string.no_browser_found), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PrivacyTip,
                                            contentDescription = stringResource(R.string.privacy_policy),
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.privacy_policy),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
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
                                                var showInlineConfirm by remember { mutableStateOf(false) }

                                                Column(modifier = Modifier.animateItem()) {
                                                    val localizedName = localizedNames.getOrElse(surah.id - 1) { surah.name }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        AnimatedContent(
                                                            targetState = showInlineConfirm,
                                                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                                                            modifier = Modifier.weight(1f),
                                                            label = "confirmDelete"
                                                        ) { isConfirming ->
                                                            if (isConfirming) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                                ) {
                                                                    Text(
                                                                        text = stringResource(R.string.confirm_delete_surah),
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        color = MaterialTheme.colorScheme.error,
                                                                        modifier = Modifier.padding(start = 8.dp)
                                                                    )
                                                                    Row {
                                                                        TextButton(onClick = { showInlineConfirm = false }) {
                                                                            Text(stringResource(R.string.cancel))
                                                                        }
                                                                        TextButton(
                                                                            onClick = {
                                                                                showInlineConfirm = false
                                                                                PlaybackService.removeSurahCache(surah)
                                                                                cachedSurahs = PlaybackService.getCachedSurahs()
                                                                                cacheSizeBytes = PlaybackService.getCacheSize(this@SettingsActivity)
                                                                            }
                                                                        ) {
                                                                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                                                        }
                                                                    }
                                                                }
                                                            } else {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = "${surah.id}. $localizedName",
                                                                        modifier = Modifier.weight(1f),
                                                                        style = MaterialTheme.typography.bodyLarge
                                                                    )
                                                                        IconButton(
                                                                            onClick = {
                                                                                val fileName = "${surah.id}_${localizedName}_Minshawi.mp3"
                                                                                val saved = PlaybackService.saveSurahToDownloads(this@SettingsActivity, surah, fileName)
                                                                                val message = if (saved) R.string.saved_to_downloads else R.string.save_failed
                                                                                Toast.makeText(this@SettingsActivity, getString(message), Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        ) {
                                                                        Icon(
                                                                            imageVector = Icons.Rounded.SaveAlt,
                                                                            contentDescription = "Save to device",
                                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                                        )
                                                                    }
                                                                    IconButton(
                                                                        onClick = { showInlineConfirm = true }
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Rounded.Delete,
                                                                            contentDescription = null,
                                                                            tint = MaterialTheme.colorScheme.error
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    HorizontalDivider()
                                                }
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
    val optionShape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(optionShape)
            .clickable { onClick() },
        shape = optionShape,
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
