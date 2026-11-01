package com.askrueger.radartren.data.remote

import com.askrueger.radartren.domain.Feed
import com.askrueger.radartren.domain.FeedHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class FeedResult(val feed: Feed, val message: FeedMessage?, val health: FeedHealth)

/**
 * Downloads the official feeds as Protocol Buffers: the server does not compress, and `.pb` weighs
 * about a quarter of the `.json` twin. Conditional requests make an unchanged feed cost one header.
 */
class RenfeClient(private val http: OkHttpClient) {
    private data class Cached(val etag: String?, val lastModified: Long?, val message: FeedMessage)

    private val cache = ConcurrentHashMap<Feed, Cached>()

    suspend fun fetch(feed: Feed): FeedResult = withContext(Dispatchers.IO) {
        val cached = cache[feed]
        val request = Request.Builder()
            .url("$BASE_URL/${feed.path}")
            .apply { cached?.etag?.let { header("If-None-Match", it) } }
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val serverNow = response.headers.getDate("Date")?.time?.div(1000)
                    ?: (System.currentTimeMillis() / 1000)
                when {
                    response.code == 304 && cached != null ->
                        FeedResult(feed, cached.message, FeedHealth.evaluate(feed, cached.lastModified, serverNow))

                    response.isSuccessful -> {
                        val lastModified = response.headers.getDate("Last-Modified")?.time?.div(1000)
                        val message = FeedMessage.decode(response.body.bytes())
                        cache[feed] = Cached(response.header("ETag"), lastModified, message)
                        FeedResult(feed, message, FeedHealth.evaluate(feed, lastModified, serverNow))
                    }

                    else -> unreachable(feed, cached)
                }
            }
        } catch (_: IOException) {
            unreachable(feed, cached)
        } catch (_: IllegalArgumentException) {
            // Raised by the decoder on a truncated or malformed payload.
            unreachable(feed, cached)
        }
    }

    private fun unreachable(feed: Feed, cached: Cached?) =
        FeedResult(feed, null, FeedHealth.unreachable(feed, cached?.lastModified))

    companion object {
        private const val BASE_URL = "https://gtfsrt.renfe.com"
    }
}
