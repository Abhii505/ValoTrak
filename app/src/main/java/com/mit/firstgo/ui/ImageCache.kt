package com.mit.firstgo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// In-memory cache (shared with RemoteImage)
object imageCache {
    private val map = LinkedHashMap<String, Bitmap>(256, 0.75f, true)
    private val MAX = 256
    @Synchronized fun get(url: String): Bitmap? = map[url]
    @Synchronized fun put(url: String, bmp: Bitmap) {
        if (map.size >= MAX) {
            val it = map.entries.iterator()
            if (it.hasNext()) { it.next(); it.remove() }
        }
        map[url] = bmp
    }
}

object AssetPreloader {
    var progress by mutableStateOf(0f)
    var done by mutableStateOf(false)
    var running by mutableStateOf(false)

    suspend fun preload(context: Context) {
        if (running || done) {
            if (!running) done = true
            return
        }
        running = true
        progress = 0f
        try {
            val urls = linkedSetOf<String>()

            // Maps
            fetchText("https://valorant-api.com/v1/maps")?.let { text ->
                val root = JSONObject(text)
                val data = root.optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val o = data.getJSONObject(i)
                    o.optString("splash").takeIf { it.isNotBlank() }?.let { urls.add(it) }
                    o.optString("displayIcon").takeIf { it.isNotBlank() }?.let { urls.add(it) }
                }
            }

            // Agents
            fetchText("https://valorant-api.com/v1/agents?isPlayableCharacter=true")?.let { text ->
                val root = JSONObject(text)
                val data = root.optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val o = data.getJSONObject(i)
                    o.optString("displayIcon").takeIf { it.isNotBlank() }?.let { urls.add(it) }
                    o.optString("displayIconSmall").takeIf { it.isNotBlank() }?.let { urls.add(it) }
                    o.optString("fullPortrait").takeIf { it.isNotBlank() }?.let { urls.add(it) }
                }
            }

            // Competitive tiers (latest season)
            fetchText("https://valorant-api.com/v1/competitivetiers")?.let { text ->
                val root = JSONObject(text)
                val data = root.optJSONArray("data") ?: JSONArray()
                if (data.length() > 0) {
                    val season = data.getJSONObject(data.length() - 1)
                    val tiers = season.optJSONArray("tiers") ?: JSONArray()
                    for (i in 0 until tiers.length()) {
                        val o = tiers.getJSONObject(i)
                        o.optString("largeIcon").takeIf { it.isNotBlank() }?.let { urls.add(it) }
                        o.optString("smallIcon").takeIf { it.isNotBlank() }?.let { urls.add(it) }
                        o.optString("displayIcon").takeIf { it.isNotBlank() }?.let { urls.add(it) }
                    }
                }
            }

            val total = urls.size.coerceAtLeast(1)
            var doneCount = 0
            urls.forEach { url ->
                downloadBitmap(context, url)
                doneCount++
                progress = doneCount.toFloat() / total
            }
            progress = 1f
            done = true
        } catch (_: Exception) {
            done = true
        } finally {
            running = false
        }
    }

    private fun fetchText(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 20000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", "ValoTrak/1.0")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code in 200..299) text else null
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadBitmap(context: Context, url: String): Bitmap? {
        // memory cache
        imageCache.get(url)?.let { return it }
        // disk cache
        getCachedBitmap(context, url)?.let { bmp ->
            imageCache.put(url, bmp)
            return bmp
        }
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
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

fun getCachedBitmap(context: Context, url: String): Bitmap? {
    val f = cacheFile(context, url)
    if (f.exists()) {
        return try { BitmapFactory.decodeFile(f.absolutePath) } catch (_: Exception) { null }
    }
    return null
}

fun putCachedBitmap(context: Context, url: String, bmp: Bitmap) {
    try {
        val f = cacheFile(context, url)
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } catch (_: Exception) { }
}

private fun cacheFile(context: Context, url: String): File {
    val dir = File(context.cacheDir, "remote_images")
    val md5 = MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    return File(dir, "$md5.png")
}