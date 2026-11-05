package com.noobdev.waffle.core.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import com.noobdev.waffle.data.remote.RouteResult
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
    private var lastArrived = false
    private var lastRoute: RouteResult? = null
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
        // A new trip can replace an active snapshot without an inactive frame. Start its
        // voice state fresh so the first instruction and the eventual arrival are spoken.
        if (snapshot.route !== lastRoute) {
            lastRoute = snapshot.route
            lastInstruction = null
            lastNotice = null
            lastArrived = false
        }
        if (!enabled || !ready) return
        speakSnapshot(snapshot)
    }

    fun reset() {
        lastInstruction = null
        lastNotice = null
        lastArrived = false
        lastRoute = null
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
        // Treat arrival as an event, not as a text match. The final "Arrive" step is selected
        // while approaching and its text does not change when the route actually ends.
        if (snapshot.arrived && !lastArrived) {
            lastArrived = true
            speak("You have arrived at your destination")
        } else if (!snapshot.arrived) {
            lastArrived = false
        }

        val instruction = snapshot.instruction.trim()
        if (instruction.isNotEmpty() && instruction != lastInstruction) {
            lastInstruction = instruction
            if (!snapshot.arrived) {
                speak(
                        if (instruction.contains("arrive", ignoreCase = true))
                                "Continue to your destination"
                        else instruction
                )
            }
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
