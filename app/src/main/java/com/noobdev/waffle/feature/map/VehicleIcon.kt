package com.noobdev.waffle.feature.map

import androidx.annotation.DrawableRes
import com.noobdev.waffle.R

/** The car-style choices in "Choose vehicle icon". Bike/walk are selected by travel mode. */
enum class VehicleIcon(
        val label: String,
        @DrawableRes val drawable: Int,
        val storageKey: String
) {
    ARROW("Arrow", R.drawable.ic_puck_arrow_3d, "ARROW"),
    SEDAN("Sedan", R.drawable.ic_puck_sedan_3d, "SEDAN"),
    SUV("SUV", R.drawable.ic_puck_suv_3d, "SUV"),
    PICKUP("Pickup", R.drawable.ic_puck_pickup_3d, "PICKUP");

    companion object {
        fun fromKey(key: String?): VehicleIcon =
                entries.firstOrNull { it.storageKey == key } ?: SEDAN
    }
}
