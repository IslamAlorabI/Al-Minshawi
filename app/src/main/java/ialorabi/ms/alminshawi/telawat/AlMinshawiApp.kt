package ialorabi.ms.alminshawi.telawat

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.media3.common.util.UnstableApi
import ialorabi.ms.alminshawi.telawat.player.AudioCache

class AlMinshawiApp : Application() {
    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        AudioCache.init(this)
        val sharedPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val themeMode = sharedPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }
}
