package com.noobdev.waffle.feature.shops.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Shops live in their own database file (`waffle_shops.db`) rather than in the alerts database.
 * That keeps the alert schema untouched and lets the shop cache be cleared independently.
 */
@Entity(
        tableName = "shops",
        indices = [Index(value = ["lat", "lon"]), Index(value = ["fetchedAt"])]
)
data class ShopEntity(
        @PrimaryKey val id: String,
        val name: String,
        val category: String,
        val lat: Double,
        val lon: Double,
        val address: String? = null,
        val openingHours: String? = null,
        val phone: String? = null,
        val website: String? = null,
        val brand: String? = null,
        val cuisine: String? = null,
        val photoUrl: String? = null,
        val photoCredit: String? = null,
        val fetchedAt: Long = System.currentTimeMillis()
) {
    fun toModel(distanceMeters: Int = 0): Shop =
            Shop(
                    id = id,
                    name = name,
                    category =
                            runCatching { ShopCategory.valueOf(category) }
                                    .getOrDefault(ShopCategory.OTHER),
                    lat = lat,
                    lon = lon,
                    address = address,
                    openingHours = openingHours,
                    phone = phone,
                    website = website,
                    brand = brand,
                    cuisine = cuisine,
                    distanceMeters = distanceMeters,
                    photoUrl = photoUrl,
                    photoCredit = photoCredit
            )
}
