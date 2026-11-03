package com.noobdev.waffle.core.navigation

import java.time.LocalTime

/** Which side the next maneuver indicates; drives the amber blinkers. */
enum class TurnSignal {
    NONE,
    LEFT,
    RIGHT
}

data class VehicleLights(
        val headlights: Boolean = false,
        val braking: Boolean = false,
        val turnSignal: TurnSignal = TurnSignal.NONE,
        val blinkOn: Boolean = true
)

fun turnSignalFor(maneuver: ManeuverType?): TurnSignal =
        when (maneuver) {
            ManeuverType.TURN_LEFT,
            ManeuverType.TURN_SHARP_LEFT,
            ManeuverType.TURN_SLIGHT_LEFT,
            ManeuverType.KEEP_LEFT,
            ManeuverType.RAMP_LEFT,
            ManeuverType.U_TURN_LEFT,
            ManeuverType.ROUNDABOUT_LEFT -> TurnSignal.LEFT
            ManeuverType.TURN_RIGHT,
            ManeuverType.TURN_SHARP_RIGHT,
            ManeuverType.TURN_SLIGHT_RIGHT,
            ManeuverType.KEEP_RIGHT,
            ManeuverType.RAMP_RIGHT,
            ManeuverType.U_TURN_RIGHT,
            ManeuverType.ROUNDABOUT_RIGHT -> TurnSignal.RIGHT
            else -> TurnSignal.NONE
        }

/** Local-time night check; the map theme can be dark during the day, so time is the honest cue. */
fun isNightTime(): Boolean {
    val hour = LocalTime.now().hour
    return hour >= 19 || hour < 6
}
