package ialorabi.ms.alminshawi.telawat.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import ialorabi.ms.alminshawi.telawat.R
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
        val primary = if (isDark) {
            context.getColor(android.R.color.system_accent1_200)
        } else {
            context.getColor(android.R.color.system_accent1_600)
        }
        val size = 512
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(primaryContainer)
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.player_logo)
        val logoSize = (size * 0.65f).toInt()
        val scaled = logoBitmap.scale(logoSize, logoSize)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = PorterDuffColorFilter(primary, PorterDuff.Mode.SRC_IN)
        val left = (size - logoSize) / 2f
        val top = (size - logoSize) / 2f
        canvas.drawBitmap(scaled, left, top, paint)
        logoBitmap.recycle()
        scaled.recycle()
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }
}
