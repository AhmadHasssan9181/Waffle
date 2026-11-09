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
                    else ->
                            String.format(
                                    java.util.Locale.US,
                                    "%.1f km",
                                    distanceMeters / 1000f
                            )
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
        private val ATM_TOKEN = Regex("""(?i)\batms?\b""")
        private val LAB_TOKEN = Regex("""(?i)\b(labs?|laboratory)\b""")
        private val REAL_STAY_NAME =
                Regex(
                        """(?i)\b(hotel|guest\s*house|inn|motel|resort|hostel|lodge|suites|rooms|continental|serena|marriott|ramada|avari|shelton|residency)\b"""
                )
        private val FOOD_NAME =
                Regex(
                        """(?i)\b(foods?|restaurant|cafe|coffee|tea|chai|dhaba|tikka|karahi|biryani|pulao|nihari|samosa|sweets?|bakers?|bakery|pizza|pizzeria|burger|shawarma|broast|fried\s*chicken|ice\s*cream|kulfi|juice|lassi|chaat|tandoor|naan|haleem|kebab|kabab|grill|bbq|barbecue|refreshment|kitchen|canteen)\b"""
                )
        private val GROCERY_NAME =
                Regex(
                        """(?i)\b(grocery|supermarket|cash\s*&\s*carry|cash\s+and\s+carry|bachat\s*mart|save\s*mart|general\s*store|kiryana|atta\s*chakki|flour\s*mill|dry\s*fruit|mineral\s*water|dairy)\b"""
                )

        /** Maps the bundled places DB's `cat|bcat` type and place name onto the app's categories. */
        fun fromOfflineType(type: String, name: String = ""): ShopCategory {
            val value = type.lowercase()
            val n = name.lowercase()
            val combined = "$value $n"

            // In Pakistan, roadside restaurants are often called "Lahori Foods Hotel" or "Quetta Tea Hotel"
            if (FOOD_NAME.containsMatchIn(n) &&
                            !n.contains("guest house") &&
                            !n.contains("resort") &&
                            !n.contains("suites")
            ) {
                return if (GROCERY_NAME.containsMatchIn(n) &&
                                !n.contains("sweets") &&
                                !n.contains("bakers")
                ) {
                    GROCERY
                } else {
                    FOOD
                }
            }

            return when {
                GROCERY_NAME.containsMatchIn(n) &&
                        !n.contains("sweets") &&
                        !n.contains("bakers") &&
                        !value.contains("bakery") -> GROCERY

                value.contains("grocery") ||
                        value.contains("supermarket") ||
                        value.contains("convenience") ||
                        value.contains("food_and_beverage_store") ||
                        value.contains("produce_store") ||
                        value.contains("specialty_food_store") ||
                        value.contains("butcher") ||
                        value.contains("fishmonger") ||
                        value.contains("greengrocer") ||
                        value.contains("dairy") ||
                        value.contains("fruit_and_vegetable") -> {
                    if (n.contains("sweets") || n.contains("bakers") || value.contains("bakery")) {
                        FOOD
                    } else {
                        GROCERY
                    }
                }

                value.contains("restaurant") ||
                        value.contains("cafe") ||
                        value.contains("eatery") ||
                        value.contains("food_court") ||
                        value.contains("bakery") ||
                        value.contains("coffee") ||
                        value.contains("fast_food") ||
                        value.contains("pizza") ||
                        value.contains("burger") ||
                        value.contains("ice_cream") ||
                        value.contains("dessert") ||
                        value.contains("smoothie") ||
                        value.contains("juice") ||
                        value.contains("tea_room") ||
                        value.contains("food_stand") ||
                        value.contains("food_truck") ||
                        value.contains("food_delivery") ||
                        value.contains("food_service") ||
                        value.contains("food_and_drink") ||
                        value == "food" -> FOOD

                value.contains("hospital") ||
                        value.contains("clinic") ||
                        value.contains("pharmacy") ||
                        value.contains("chemist") ||
                        value.contains("drug_store") ||
                        value.contains("health") ||
                        value.contains("dental") ||
                        value.contains("dentist") ||
                        value.contains("doctor") ||
                        value.contains("diagnostic") ||
                        value.contains("laboratory") ||
                        value.contains("surgery") ||
                        value.contains("optometrist") ||
                        value.contains("physiotherap") ||
                        value.contains("physical_therapy") ||
                        value.contains("rehabilitation") ||
                        value.contains("nursing_home") ||
                        value.contains("veterinar") ||
                        value.contains("medical") ||
                        n.contains("hospital") ||
                        n.contains("clinic") ||
                        n.contains("pharmacy") ||
                        n.contains("medical store") ||
                        n.contains("chemist") ||
                        n.contains("dental") ||
                        LAB_TOKEN.containsMatchIn(n) -> HEALTH

                value.contains("rental") || n.contains("rent a car") || n.contains("car rental") ->
                        SERVICES

                value.contains("gas_station") ||
                        value.contains("fuel") ||
                        value.contains("petrol") ||
                        value.contains("automotive") ||
                        value.contains("auto_") ||
                        value.contains("vehicle_service") ||
                        value.contains("vehicle_repair") ||
                        value.contains("vehicle_parts") ||
                        value.contains("vehicle_dealer") ||
                        value.contains("car_repair") ||
                        value.contains("car_wash") ||
                        value.contains("car_parts") ||
                        value.contains("car_dealer") ||
                        value.contains("motorcycle_repair") ||
                        value.contains("motorcycle_dealer") ||
                        value.contains("tire") ||
                        value.contains("tyre") ||
                        n.contains("petrol") ||
                        n.contains("filling station") ||
                        n.contains("cng") ||
                        n.contains("auto workshop") ||
                        n.contains("autos") -> FUEL

                value.contains("bank") ||
                        value.contains("credit_union") ||
                        value.contains("financial") ||
                        value.contains("money_transfer") ||
                        value.contains("currency_exchange") ||
                        value.contains("bureau_de_change") ||
                        value.contains("insurance_agency") ||
                        ATM_TOKEN.containsMatchIn(combined) ||
                        n.contains("bank") ||
                        n.contains("moneygram") ||
                        n.contains("western union") -> MONEY

                (value.contains("hotel") ||
                        value.contains("lodging") ||
                        value.contains("hostel") ||
                        value.contains("guest_house") ||
                        value.contains("resort") ||
                        value.contains("motel")) &&
                        (REAL_STAY_NAME.containsMatchIn(n) || !value.contains("lodging")) -> STAY

                (value.contains("store") ||
                        value.contains("shopping") ||
                        value.contains("mall") ||
                        value.contains("marketplace") ||
                        value.contains("fashion") ||
                        value.contains("apparel") ||
                        value.contains("clothing") ||
                        value.contains("jewelry") ||
                        value.contains("electronics") ||
                        value.contains("electronic_equipment") ||
                        value.contains("hardware") ||
                        value.contains("furniture") ||
                        value.contains("mobile_phone") ||
                        value.contains("shoe") ||
                        value.contains("book") ||
                        value.contains("cosmetics") ||
                        value == "shop") &&
                        !value.contains("telemarketing") &&
                        !value.contains("marketing") -> SHOPPING

                value.contains("beauty") ||
                        value.contains("salon") ||
                        value.contains("barber") ||
                        value.contains("hairdresser") ||
                        value.contains("spa") ||
                        value.contains("laundry") ||
                        value.contains("dry_clean") ||
                        value.contains("tailor") ||
                        value.contains("repair") ||
                        value.contains("travel") ||
                        value.contains("tour_operator") ||
                        value.contains("post_office") ||
                        value.contains("courier") ||
                        value.contains("shipping") ||
                        value.contains("gym") ||
                        value.contains("fitness") ||
                        value.contains("yoga") ||
                        value.contains("printing") ||
                        value.contains("photography") ||
                        value.contains("legal") ||
                        value.contains("internet_service") ||
                        value.contains("service") -> SERVICES

                else -> OTHER
            }
        }

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
