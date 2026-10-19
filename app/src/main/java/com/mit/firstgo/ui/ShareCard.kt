package com.mit.firstgo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a simple, clean "share card" bitmap (name, rank, RR and key stats)
 * that can be exported through the system share sheet.
 */
object ShareCardBuilder {

    suspend fun build(
        context: Context,
        playerName: String,
        playerTag: String,
        tier: String?,
        rr: Int?,
        elo: Int?,
        peak: String?,
        peakSeason: String?,
        wins: Int,
        games: Int,
        winPctStr: String,
        kdStr: String,
        hsPctStr: String,
        acsStr: String,
        rankIconUrl: String?,
        bannerUrl: String?,
    ): File? = withContext(Dispatchers.IO) {
        try {
            val W = 720
            val H = 980
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)

            // --- background ---
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, W * 0.55f, H.toFloat(),
                    intArrayOf(0xFF0F1923.toInt(), 0xFF0A1220.toInt()),
                    null, Shader.TileMode.CLAMP,
                )
            }
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

            // ambient light washes
            fun wash(cx: Float, cy: Float, r: Float, color: Int) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(cx, cy, r, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                }
                c.drawCircle(cx, cy, r, p)
            }
            wash(70f, 80f, 340f, 0x1AFF4655)
            wash(680f, 600f, 420f, 0x1A4CC3FF)
            wash(240f, 940f, 360f, 0x12F6C243)

            // top accent bar
            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, W.toFloat(), 0f,
                    intArrayOf(0xFFFF4655.toInt(), 0x00FF4655), null, Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, W.toFloat(), 10f, accent)

            // --- banner (optional) ---
            val bannerTop = 40f
            val bannerH = 90f
            bannerUrl?.let { url ->
                fetchBitmap(context, url, radius = 0)?.let {
                    val m = Matrix()
                    val s = W.toFloat() / it.width
                    m.postScale(s, s)
                    m.postTranslate(0f, bannerTop)
                    c.drawBitmap(it, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                }
            }

            // --- rank icon ---
            val iconS = 200
            val iconX = (W - iconS) / 2f
            val iconY = bannerTop + bannerH + 40f
            rankIconUrl?.let { url ->
                fetchBitmap(context, url)?.let {
                    // soft glow disc behind the icon
                    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(iconX + iconS / 2f, iconY + iconS / 2f, iconS * 0.75f,
                            0x66F6C243, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    }
                    c.drawCircle(iconX + iconS / 2f, iconY + iconS / 2f, iconS * 0.75f, glow)
                    c.drawBitmap(it, null, RectF(iconX, iconY, iconX + iconS, iconY + iconS),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                }
            }
            val belowIcon = iconY + iconS + 16f

            // --- text helpers ---
            val white = 0xFFF9F9F9.toInt()
            val yellow = 0xFFF6C243.toInt()
            val gray = 0xFF8B9BB4.toInt()
            val red = 0xFFFF4655.toInt()

            fun text(str: String, x: Float, y: Float, sizePx: Float, colorInt: Int, bold: Boolean = true, align: Paint.Align = Paint.Align.CENTER) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    textSize = sizePx
                    color = colorInt
                    textAlign = align
                    setShadowLayer(6f, 0f, 0f, colorInt)
                }
                c.drawText(str, x, y, p)
            }

            // name + rank + rr
            text("$playerName#$playerTag", W / 2f, belowIcon + 44f, 40f, gray, bold = false)
            text(tier ?: "Unranked", W / 2f, belowIcon + 106f, 62f, white)
            if (rr != null) text("$rr RR", W / 2f, belowIcon + 162f, 46f, yellow)
            if (elo != null) text("ELO $elo", W / 2f, belowIcon + 202f, 28f, gray, bold = false)

            // --- stats panel ---
            val panelTop = belowIcon + 236f
            val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xDC152232.toInt()
            }
            val panelRect = RectF(40f, panelTop, (W - 40).toFloat(), panelTop + 330f)
            c.drawRoundRect(panelRect, 26f, 26f, panel)
            // subtle border
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = 0x30FFFFFF
            }
            c.drawRoundRect(panelRect, 26f, 26f, border)

            val rowColor = white
            val labelColor = gray
            val rows = listOf(
                Triple("Win rate", winPctStr, yellow),
                Triple("W / L", "$wins W / ${games - wins} L", white),
                Triple("K/D ratio", kdStr, white),
                Triple("Headshot %", hsPctStr, white),
                Triple("Avg combat score", acsStr, white),
                Triple("Peak rank", peak?.let { if (peakSeason.isNullOrBlank()) it else "$it ($peakSeason)" } ?: "—", red),
            )
            var y = panelTop + 58f
            val xLabel = 76f
            val xValue = W - 76f
            for ((label, value, col) in rows) {
                text(label, xLabel, y, 28f, labelColor, bold = false, align = Paint.Align.LEFT)
                text(value, xValue, y, 30f, col, bold = true, align = Paint.Align.RIGHT)
                y += 48f
            }

            // --- footer ---
            text("VALOTRAK", W / 2f, H - 44f, 26f, red)
            text("Valorant progress tracker", W / 2f, H - 16f, 18f, gray, bold = false)

            // --- save ---
            val dir = File(context.cacheDir, "share")
            dir.mkdirs()
            val f = File(dir, "valotrak_share.png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f
        } catch (_: Exception) {
            null
        }
    }

    fun shareImage(context: Context, file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.mit.firstgo.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share stats"))
        } catch (_: Exception) { }
    }

    private fun fetchBitmap(context: Context, url: String, radius: Int = 0): Bitmap? {
        imageCache.get(url)?.let { return it }
        getCachedBitmap(context, url)?.let { bmp ->
            imageCache.put(url, bmp)
            return bmp
        }
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.doInput = true
            conn.connect()
            val input = conn.inputStream
            val bmp = BitmapFactory.decodeStream(input)
            input.close()
            conn.disconnect()
            if (bmp != null) {
                imageCache.put(url, bmp)
                putCachedBitmap(context, url, bmp)
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }
}
