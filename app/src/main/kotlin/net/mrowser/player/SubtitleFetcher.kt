package net.mrowser.player

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a side-loaded subtitle file's text, carrying the playback headers (UA/Referer/Cookie). */
object SubtitleFetcher {
    private const val TAG = "SubtitleFetcher"
    private const val TIMEOUT_MS = 15_000

    /** Returns the body text, or null on failure (non-fatal — that track is dropped). */
    fun fetch(url: String, headers: Map<String, String>): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        // Subtitle URLs can include signed tokens; keep them out of Android logs.
        Log.w(TAG, "fetch failed")
        null
    }
}
