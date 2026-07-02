package ialorabi.ms.alminshawi.telawat.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import ialorabi.ms.alminshawi.telawat.R
import java.io.File
import java.io.FileOutputStream

object ArtworkHelper {

    private const val ARTWORK_FILENAME = "media_artwork.png"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    private var cachedUri: Uri? = null

    fun getArtworkUri(context: Context): Uri? {
        cachedUri?.let { return it }

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

        try {
            val size = 512
            val bitmap = createBitmap(size, size)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(primaryContainer)

                val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.player_logo)
                if (logoBitmap != null) {
                    try {
                        val logoSize = (size * 0.65f).toInt()
                        val scaled = logoBitmap.scale(logoSize, logoSize)
                        try {
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            paint.colorFilter = PorterDuffColorFilter(primary, PorterDuff.Mode.SRC_IN)
                            val left = (size - logoSize) / 2f
                            val top = (size - logoSize) / 2f
                            canvas.drawBitmap(scaled, left, top, paint)
                        } finally {
                            scaled.recycle()
                        }
                    } finally {
                        logoBitmap.recycle()
                    }
                }

                val artworkDir = File(context.cacheDir, "artwork")
                artworkDir.mkdirs()
                val file = File(artworkDir, ARTWORK_FILENAME)

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val authority = context.packageName + AUTHORITY_SUFFIX
                val uri = FileProvider.getUriForFile(context, authority, file)
                cachedUri = uri
                return uri
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            return null
        }
    }

    fun invalidate() {
        cachedUri = null
    }
}
