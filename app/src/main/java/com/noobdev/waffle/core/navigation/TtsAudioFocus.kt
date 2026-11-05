package com.noobdev.waffle.core.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Short-lived navigation-guidance audio focus.
 *
 * TTS used to speak over music with no focus request. Ducking only while a prompt is likely to be
 * playing keeps music audible between instructions while preventing the two streams from fighting.
 */
class TtsAudioFocus(context: Context) {

    private val manager =
            context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var held = false
    private var focusRequest: AudioFocusRequest? = null

    private val releaseRunnable = Runnable { abandon() }

    fun begin() {
        handler.removeCallbacks(releaseRunnable)
        if (held) {
            handler.postDelayed(releaseRunnable, HOLD_MS)
            return
        }
        val attributes =
                AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(attributes)
                            .setOnAudioFocusChangeListener { }
                            .build()
            focusRequest = request
            held = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            held =
                    manager.requestAudioFocus(
                            null,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        handler.postDelayed(releaseRunnable, HOLD_MS)
    }

    fun abandon() {
        handler.removeCallbacks(releaseRunnable)
        if (!held) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION") manager.abandonAudioFocus(null)
        }
        focusRequest = null
        held = false
    }

    private companion object {
        const val HOLD_MS = 15_000L
    }
}
