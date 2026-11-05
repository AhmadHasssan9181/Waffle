package com.noobdev.waffle.core.network

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/**
 * MapLibre normally lets the native renderer fire a large burst of tile, glyph and sprite
 * requests. That is ideal for online panning, but MapTiler's free tier rejects offline packs with
 * HTTP 429 when a city-sized pack starts at full speed.
 *
 * Offline downloads call [throttleForOfflineDownload] so requests are spaced just under the
 * provider's per-minute limit. Normal map use leaves the gap at zero and is not delayed.
 */
object MapTileHttp {

    private val slotLock = Any()
    private var nextSlotAt = 0L

    @Volatile private var requestGapMs = 0L
    private var systemProxy: Proxy? = null

    /**
     * MapLibre's native HTTP bridge does not reliably honour Android's global proxy setting.
     * Read it once at process start so a system proxy (or a QA proxy via adb reverse) also carries
     * map traffic. When no proxy is configured this is a no-op.
     */
    fun configure(context: Context) {
        val value =
                runCatching {
                            Settings.Global.getString(
                                    context.contentResolver,
                                    Settings.Global.HTTP_PROXY
                            )
                        }
                        .getOrNull()
                        .orEmpty()
        if (value.isBlank() || value == ":0") {
            systemProxy = null
            return
        }
        val host = value.substringBeforeLast(":", value)
        val port = value.substringAfterLast(":", "").toIntOrNull() ?: 0
        systemProxy =
                if (host.isNotBlank() && port in 1..65535) {
                    Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
                } else null
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
                .dispatcher(
                        Dispatcher().apply {
                            maxRequests = 6
                            maxRequestsPerHost = 6
                        }
                )
                .apply { systemProxy?.let { proxy(it) } }
                .addInterceptor { chain ->
                    val gap = requestGapMs
                    if (gap > 0L) {
                        val waitMs =
                                synchronized(slotLock) {
                                    val now = SystemClock.elapsedRealtime()
                                    val slot = maxOf(now, nextSlotAt)
                                    nextSlotAt = slot + gap
                                    slot - now
                                }
                        if (waitMs > 0L) Thread.sleep(waitMs)
                    }
                    chain.proceed(chain.request())
                }
                .build()
    }

    fun throttleForOfflineDownload() {
        requestGapMs = 350L
    }

    fun allowNormalMapTraffic() {
        requestGapMs = 0L
    }
}
