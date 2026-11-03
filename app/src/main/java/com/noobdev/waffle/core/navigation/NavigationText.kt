package com.noobdev.waffle.core.navigation

import java.lang.Character

/**
 * The offline graph stores OSM `name` values in the local script, so a route in Rawalpindi can mix
 * "Turn left onto Aziz Bhatti Shaheed Road" with "Keep right onto مردی روڈ". The app UI and the
 * English TTS voice should stay consistent, so a non-Latin road name is omitted and the maneuver
 * action is spoken/shown on its own. The map still renders the local label.
 */
fun String.hasNonLatinLetters(): Boolean =
        any { character ->
            character.isLetter() &&
                    Character.UnicodeScript.of(character.code) != Character.UnicodeScript.LATIN
        }

/** A short English action for a maneuver, used when the router supplied a non-Latin instruction. */
fun ManeuverType.plainAction(): String =
        when (this) {
            ManeuverType.DEPART -> "Start"
            ManeuverType.CONTINUE -> "Continue"
            ManeuverType.TURN_LEFT -> "Turn left"
            ManeuverType.TURN_RIGHT -> "Turn right"
            ManeuverType.TURN_SHARP_LEFT -> "Turn sharp left"
            ManeuverType.TURN_SHARP_RIGHT -> "Turn sharp right"
            ManeuverType.TURN_SLIGHT_LEFT -> "Bear left"
            ManeuverType.TURN_SLIGHT_RIGHT -> "Bear right"
            ManeuverType.KEEP_LEFT -> "Keep left"
            ManeuverType.KEEP_RIGHT -> "Keep right"
            ManeuverType.U_TURN_LEFT, ManeuverType.U_TURN_RIGHT -> "Make a U-turn"
            ManeuverType.ROUNDABOUT_LEFT, ManeuverType.ROUNDABOUT_RIGHT -> "Take the roundabout"
            ManeuverType.MERGE -> "Merge"
            ManeuverType.RAMP_LEFT -> "Take the left ramp"
            ManeuverType.RAMP_RIGHT -> "Take the right ramp"
            ManeuverType.ARRIVE -> "Arrive at destination"
            ManeuverType.WAYPOINT -> "Reach the via point"
            ManeuverType.UNKNOWN -> "Continue"
        }

/** Keeps a router instruction only when it is usable in the English UI. */
fun cleanInstruction(instruction: String, maneuver: ManeuverType): String =
        if (instruction.hasNonLatinLetters()) maneuver.plainAction() else instruction
