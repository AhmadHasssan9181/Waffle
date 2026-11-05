package com.noobdev.waffle.core.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Turn-by-turn speech owned by [NavigationService], not by the map screen.
 *
 * The UI used to own TextToSpeech, which meant the phone went silent as soon as the screen turned
 * off or the user left the map tab. This class runs in the foreground service and keeps speaking
 * from the same [NavigationSnapshot] that drives the notification.
 */
class NavigationVoice(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var enabled = true
    private var lastInstruction: String? = null
    private var lastNotice: String? = null
    private var pending: NavigationSnapshot? = null

    init {
        tts =
                TextToSpeech(context.applicationContext) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    if (!ready) return@TextToSpeech
                    val result = tts?.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                                    result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                    pending?.let { speakSnapshot(it) }
                }
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) tts?.stop()
    }

    fun onSnapshot(snapshot: NavigationSnapshot) {
        pending = snapshot
        if (!snapshot.active) {
            reset()
            return
        }
        if (!enabled || !ready) return
        speakSnapshot(snapshot)
    }

    fun reset() {
        lastInstruction = null
        lastNotice = null
        tts?.stop()
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
        pending = null
    }

    private fun speakSnapshot(snapshot: NavigationSnapshot) {
        val instruction = snapshot.instruction.trim()
        if (instruction.isNotEmpty() && instruction != lastInstruction) {
            lastInstruction = instruction
            val phrase =
                    when {
                        !instruction.contains("arrive", ignoreCase = true) -> instruction
                        // The final step is also selected while approaching the destination.
                        // Never say "arrived" until the route is actually finished.
                        snapshot.arrived -> "You have arrived at your destination"
                        else -> "Continue to your destination"
                    }
            speak(phrase)
        }

        val notice = snapshot.notice?.trim().orEmpty()
        if (notice.isNotEmpty() && notice != lastNotice) {
            lastNotice = notice
            val phrase =
                    when {
                        notice.startsWith("Wrong way", ignoreCase = true) ->
                                "Wrong way. Please turn around."
                        notice.startsWith("Off route", ignoreCase = true) ->
                                "Off route. Finding a new way."
                        notice.contains("rerout", ignoreCase = true) -> "Rerouting."
                        else -> null
                    }
            if (phrase != null) speak(phrase)
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "waffle-${System.nanoTime()}")
    }
}
