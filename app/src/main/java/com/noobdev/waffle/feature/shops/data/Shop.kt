package com.noobdev.waffle.feature.shops.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.design.theme.Honey
import com.noobdev.waffle.design.theme.HoneyDeep
import com.noobdev.waffle.design.theme.SafeGreen
import com.noobdev.waffle.design.theme.Sky
import com.noobdev.waffle.design.theme.StormColor

/** Clean fuel-can glyph; the Material gas pump reads like an "F with dots" at chip size. */
private val FuelCanIcon: ImageVector by lazy {
    ImageVector.Builder(
                    name = "FuelCan",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f
            )
            .apply {
                path(fill = SolidColor(Color.Black)) {
                    // Can body
                    moveTo(5f, 7f)
                    lineTo(14f, 7f)
                    lineTo(14f, 21f)
                    lineTo(5f, 21f)
                    close()
                    // Handle
                    moveTo(7f, 3f)
                    lineTo(13f, 3f)
                    lineTo(13f, 7f)
                    lineTo(7f, 7f)
                    close()
                    // Spout
                    moveTo(14f, 8f)
                    lineTo(19f, 5.5f)
                    lineTo(19f, 10.5f)
                    lineTo(14f, 13f)
                    close()
                }
            }
            .build()
}

/**
 * A point of interest around the user, sourced from OpenStreetMap through Overpass.
 *
 * Shops are not alerts: they are the "where can I buy water / medicine / fuel right now" layer,
 * which matters most in the first hour after a disaster.
 */
data class Shop(
        val id: String,
        val name: String,
        val category: ShopCategory,
        val lat: Double,
        val lon: Double,
        val address: String? = null,
        val openingHours: String? = null,
        val phone: String? = null,
        val website: String? = null,
        val brand: String? = null,
        val cuisine: String? = null,
        val distanceMeters: Int = 0,
        val photoUrl: String? = null,
        val photoCredit: String? = null
) {
    val distanceLabel: String
        get() =
                when {
                    distanceMeters < 1000 -> "$distanceMeters m"
                    else -> String.format("%.1f km", distanceMeters / 1000f)
                }

    /** OSM's opening_hours is a full grammar; we only special-case the one unambiguous value. */
    val isOpen24h: Boolean
        get() = openingHours?.trim() == "24/7"
}

enum class ShopCategory(
        val label: String,
        val icon: ImageVector,
        val tint: Color,
        /** Tighter label for chips. */
        val shortLabel: String = label
) {
    FOOD("Food & drink", Icons.Rounded.Restaurant, Honey, "Food"),
    GROCERY("Groceries", Icons.Rounded.LocalGroceryStore, SafeGreen, "Grocery"),
    HEALTH("Health", Icons.Rounded.LocalPharmacy, Sky, "Health"),
    FUEL("Fuel & auto", FuelCanIcon, HoneyDeep, "Fuel"),
    MONEY("Banking", Icons.Rounded.AccountBalance, StormColor, "Money"),
    SHOPPING("Shopping", Icons.Rounded.ShoppingBag, Honey, "Shops"),
    SERVICES("Services", Icons.Rounded.ContentCut, SafeGreen, "Services"),
    STAY("Rest & stay", Icons.Rounded.Hotel, Sky, "Rest"),
    OTHER("Other", Icons.Rounded.Storefront, StormColor, "Other");

    companion object {
        /** Maps OSM `shop` / `amenity` / `tourism` tags onto the app's nine categories. */
        fun fromTags(tags: Map<String, String>): ShopCategory {
            val shop = tags["shop"]?.lowercase()
            val amenity = tags["amenity"]?.lowercase()
            val tourism = tags["tourism"]?.lowercase()

            return when {
                amenity in
                        setOf(
                                "restaurant", "cafe", "fast_food", "food_court", "ice_cream",
                                "bar", "pub", "bakery"
                        ) ||
                        shop in setOf("bakery", "confectionery", "butcher", "greengrocer", "deli") &&
                                amenity == null -> FOOD

                amenity in setOf("pharmacy", "hospital", "clinic", "doctors", "dentist", "veterinary") ||
                        shop in setOf("chemist", "medical_supply", "optician") -> HEALTH

                amenity in setOf("fuel", "charging_station", "car_repair", "car_wash", "car_rental") ||
                        shop in setOf("car", "car_repair", "car_parts", "motorcycle", "tyres") ->
                        FUEL

                amenity in setOf("bank", "atm", "bureau_de_change", "money_transfer") ||
                        shop == "money_lender" -> MONEY

                tourism in setOf("hotel", "guest_house", "hostel", "motel", "apartment") -> STAY

                amenity in setOf("marketplace", "post_office", "library", "police", "fire_station") ->
                        SHOPPING

                amenity in setOf("hairdresser", "beauty", "laundry", "dry_cleaning", "tailor") ||
                        shop in setOf("hairdresser", "beauty", "laundry", "dry_cleaning", "tailor") ->
                        SERVICES

                shop != null -> SHOPPING
                else -> OTHER
            }
        }
    }
}
