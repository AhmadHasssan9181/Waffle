package com.noobdev.waffle.core.navigation

/**
 * Structured maneuver type shared by the offline and online routers. The navigation banner uses
 * this instead of guessing from the instruction text, so "Make a U-turn", "Take the roundabout",
 * "Merge" and "Keep left" all get the right icon.
 */
enum class ManeuverType {
    DEPART,
    CONTINUE,
    TURN_LEFT,
    TURN_RIGHT,
    TURN_SHARP_LEFT,
    TURN_SHARP_RIGHT,
    TURN_SLIGHT_LEFT,
    TURN_SLIGHT_RIGHT,
    KEEP_LEFT,
    KEEP_RIGHT,
    U_TURN_LEFT,
    U_TURN_RIGHT,
    ROUNDABOUT_LEFT,
    ROUNDABOUT_RIGHT,
    MERGE,
    RAMP_LEFT,
    RAMP_RIGHT,
    ARRIVE,
    WAYPOINT,
    UNKNOWN
}
