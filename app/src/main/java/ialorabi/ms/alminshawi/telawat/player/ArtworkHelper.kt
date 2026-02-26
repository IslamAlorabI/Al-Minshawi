package ialorabi.ms.alminshawi.telawat.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream

object ArtworkHelper {
    fun generate(context: Context): ByteArray {
        val isDark = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val primaryContainer = if (isDark) {
            context.getColor(android.R.color.system_accent1_700)
        } else {
            context.getColor(android.R.color.system_accent1_100)
        }
        
        val size = 512
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(primaryContainer)
        
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }
}
