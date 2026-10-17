package com.mit.firstgo.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val imageCache = object {
    private val map = LinkedHashMap<String, Bitmap>(64, 0.75f, true)
    private val MAX = 80
    @Synchronized fun get(url: String): Bitmap? = map[url]
    @Synchronized fun put(url: String, bmp: Bitmap) {
        if (map.size >= MAX) {
            val it = map.entries.iterator()
            if (it.hasNext()) { it.next(); it.remove() }
        }
        map[url] = bmp
    }
}

@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(imageCache.get(url ?: "")) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) { failed = true; return@LaunchedEffect }
        imageCache.get(url)?.let { bitmap = it; return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            try {
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
                    bitmap = bmp
                } else {
                    failed = true
                }
            } catch (_: Exception) {
                failed = true
            }
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        androidx.compose.foundation.layout.Box(modifier = modifier)
    }
}