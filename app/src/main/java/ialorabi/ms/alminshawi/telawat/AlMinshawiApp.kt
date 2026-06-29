package ialorabi.ms.alminshawi.telawat

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class AlMinshawiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val sharedPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val themeMode = sharedPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }
}
