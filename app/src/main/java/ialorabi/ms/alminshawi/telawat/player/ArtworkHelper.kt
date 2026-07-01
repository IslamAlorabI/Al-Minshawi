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

    private const val MAX_ARTWORK_BYTES = 150_000

    private data class ArtworkConfig(
        val size: Int,
        val withLogo: Boolean,
        val format: Bitmap.CompressFormat,
        val quality: Int,
    )

    private val FALLBACK_CONFIGS = listOf(
        ArtworkConfig(512, withLogo = true, Bitmap.CompressFormat.PNG, 100),
        ArtworkConfig(512, withLogo = true, Bitmap.CompressFormat.JPEG, 85),
        ArtworkConfig(320, withLogo = true, Bitmap.CompressFormat.JPEG, 80),
        ArtworkConfig(256, withLogo = true, Bitmap.CompressFormat.JPEG, 75),
        ArtworkConfig(128, withLogo = false, Bitmap.CompressFormat.JPEG, 70),
        ArtworkConfig(64, withLogo = false, Bitmap.CompressFormat.PNG, 100),
    )

    fun generate(context: Context): ByteArray? {
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

        for (config in FALLBACK_CONFIGS) {
            try {
                val bytes = renderArtwork(context, config, primaryContainer, primary)
                if (bytes != null && bytes.size <= MAX_ARTWORK_BYTES) return bytes
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun renderArtwork(
        context: Context,
        config: ArtworkConfig,
        backgroundColor: Int,
        logoColor: Int,
    ): ByteArray? {
        val bitmap = createBitmap(config.size, config.size)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(backgroundColor)

            if (config.withLogo) {
                val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.player_logo)
                    ?: return null
                try {
                    val logoSize = (config.size * 0.65f).toInt()
                    val scaled = logoBitmap.scale(logoSize, logoSize)
                    try {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        paint.colorFilter = PorterDuffColorFilter(logoColor, PorterDuff.Mode.SRC_IN)
                        val left = (config.size - logoSize) / 2f
                        val top = (config.size - logoSize) / 2f
                        canvas.drawBitmap(scaled, left, top, paint)
                    } finally {
                        scaled.recycle()
                    }
                } finally {
                    logoBitmap.recycle()
                }
            }

            val stream = ByteArrayOutputStream()
            bitmap.compress(config.format, config.quality, stream)
            return stream.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
