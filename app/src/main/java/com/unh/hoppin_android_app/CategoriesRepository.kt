package com.unh.hoppin_android_app

import com.google.android.libraries.places.api.model.Place
import kotlin.collections.orEmpty

object CategoriesRepository {

    // ---------------- Main categories (moved out of Home) ----------------
    val MAIN_CATEGORIES: List<Category> = listOf(
        Category(1, "Explore",    R.drawable.binoculars),
        Category(2, "Refresh",    R.drawable.hamburger_soda),
        Category(3, "Entertain",  R.drawable.theater_masks),
        Category(4, "ShopStop",   R.drawable.shop),
        Category(5, "Relax",      R.drawable.dorm_room),
        Category(6, "Wellbeing",  R.drawable.hands_brain),
        Category(7, "Emergency",  R.drawable.light_emergency_on),
        Category(8, "Services",   R.drawable.holding_hand_delivery)
    )

    // ---------------- Valid includedTypes per MAIN category (IDs) --------
    // Only API-supported types; all lowercase and safe for SearchNearby.setIncludedTypes()
    private val CATEGORY_TYPES: Map<Int, List<String>> = mapOf(
        // 1) Explore
        1 to listOf("tourist_attraction", "museum", "art_gallery", "park"),

        // 2) Refresh
        2 to listOf("restaurant", "bar", "cafe", "bakery"),

        // 3) Entertain
        3 to listOf("movie_theater", "night_club", "bowling_alley", "casino"),

        // 4) ShopStop
        4 to listOf("shopping_mall", "clothing_store", "department_store", "store", "supermarket"),

        // 5) Relax
        5 to listOf("spa", "lodging", "campground"),

        // 6) Wellbeing
        6 to listOf("gym", "pharmacy", "doctor", "beauty_salon"),

        // 7) Emergency
        7 to listOf("hospital", "police", "fire_station"),

        // 8) Services
        8 to listOf("post_office", "bank", "atm", "gas_station", "car_repair")
    )

    // ---------------- Sub-categories (per MAIN category) -----------------
    // Each sub-category has its own includedTypes (subset of the parent)
    private val SUBCATEGORIES: Map<Int, List<SubCategory>> = mapOf(
        1 to listOf( // Explore
            SubCategory("museums",   "Museums",   listOf("museum")),
            SubCategory("gallery",   "Art Gallery", listOf("art_gallery")),
            SubCategory("parks",     "Parks",     listOf("park")),
            SubCategory("attract",   "Attractions", listOf("tourist_attraction"))
        ),
        2 to listOf( // Refresh
            SubCategory("restaurants", "Restaurants", listOf("restaurant")),
            SubCategory("cafes",       "Cafes",       listOf("cafe", "bakery")),
            SubCategory("bars",        "Bars",        listOf("bar"))
        ),
        3 to listOf( // Entertain
            SubCategory("movies", "Movie Theater", listOf("movie_theater")),
            SubCategory("night",  "Nightlife",      listOf("night_club")),
            SubCategory("bowling","Bowling",        listOf("bowling_alley")),
            SubCategory("casino", "Casino",         listOf("casino"))
        ),
        4 to listOf( // ShopStop
            SubCategory("malls",   "Malls",    listOf("shopping_mall")),
            SubCategory("clothes", "Clothing", listOf("clothing_store")),
            SubCategory("dept",    "Dept. Stores", listOf("department_store")),
            SubCategory("groceries","Groceries", listOf("supermarket", "store"))
        ),
        5 to listOf( // Relax
            SubCategory("spa",      "Spa",       listOf("spa")),
            SubCategory("stays",    "Stay / Lodging", listOf("lodging")),
            SubCategory("camp",     "Campground", listOf("campground"))
        ),
        6 to listOf( // Wellbeing
            SubCategory("gym",      "Gym",       listOf("gym")),
            SubCategory("pharmacy", "Pharmacy",  listOf("pharmacy")),
            SubCategory("doctor",   "Doctors",   listOf("doctor")),
            SubCategory("salon",    "Beauty Salon", listOf("beauty_salon"))
        ),
        7 to listOf( // Emergency
            SubCategory("hospital", "Hospitals", listOf("hospital")),
            SubCategory("police",   "Police",    listOf("police")),
            SubCategory("fire",     "Fire Stations", listOf("fire_station"))
        ),
        8 to listOf( // Services
            SubCategory("post",  "Post Office", listOf("post_office")),
            SubCategory("bank",  "Bank & ATM",  listOf("bank", "atm")),
            SubCategory("gas",   "Gas Station", listOf("gas_station")),
            SubCategory("auto",  "Auto Repair", listOf("car_repair"))
        )
    )

    // ---------------- Public API ----------------------------------------
    fun allCategories(): List<Category> = MAIN_CATEGORIES

    fun subCategoriesOf(categoryId: Int): List<SubCategory> =
        SUBCATEGORIES[categoryId].orEmpty()

    fun includedTypesForCategory(categoryId: Int): List<String> =
        CATEGORY_TYPES[categoryId].orEmpty()

    fun includedTypesForSub(categoryId: Int, subId: String): List<String> =
        SUBCATEGORIES[categoryId]
            ?.firstOrNull { it.id == subId }
            ?.includedTypes
            .orEmpty()

    /** Union of includedTypes for a set of categories (for one-call Nearby). */
    fun unionTypesFor(categories: List<Category>): List<String> =
        categories
            .flatMap { CATEGORY_TYPES[it.id].orEmpty() }
            .distinct()

    /** Local filter helper: compare Place.Type enums to our string map (lowercase). */
    fun placeMatchesCategory(categoryId: Int, placeTypes: List<Place.Type>?): Boolean {
        val needed = CATEGORY_TYPES[categoryId].orEmpty()
        val types = placeTypes ?: return false
        return types.any { it.name.lowercase() in needed }
    }

    fun placeMatchesSub(categoryId: Int, subId: String, placeTypes: List<Place.Type>?): Boolean {
        val needed = includedTypesForSub(categoryId, subId)
        val types = placeTypes ?: return false
        return types.any { it.name.lowercase() in needed }
    }

    /** Resolve Category by id or title if needed */
    fun getCategoryById(id: Int): Category? = MAIN_CATEGORIES.firstOrNull { it.id == id }
    fun getCategoryByTitle(title: String): Category? = MAIN_CATEGORIES.firstOrNull { it.title == title }
}
