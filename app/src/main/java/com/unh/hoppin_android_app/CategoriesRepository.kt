package com.unh.hoppin_android_app

import com.google.android.libraries.places.api.model.Place

/**
 * Represents a single subcategory under a main category.
 *
 * Example: Under the "Explore" category, you might have subcategories
 * like "Museums" or "Parks".
 *
 * @param id Unique string identifier for the subcategory.
 * @param title Display name for the subcategory.
 * @param includedTypes List of Google Place API type strings associated with this subcategory.
 * @param image Drawable resource representing this subcategory visually.
 */
data class SubCategory(
    val id: String,
    val title: String,
    val includedTypes: List<String>,
    val image: Int
)

/**
 * Repository that holds all category and subcategory data
 * used throughout the app for exploring places.
 *
 * Acts as a centralized data provider — similar to a local database —
 * that maps each category and subcategory to its corresponding
 * Google Places API type(s).
 *
 * The data can be used to:
 *  - Display categories/subcategories in the UI.
 *  - Filter Google Places results based on type.
 *  - Match a given Place object to its logical category.
 */
object CategoriesRepository {

    /**
     * The list of main (top-level) categories shown in the UI.
     */
    val MAIN: List<Category> = listOf(
        Category(1, "Explore",   R.drawable.binoculars),
        Category(2, "Refresh",   R.drawable.hamburger_soda),
        Category(3, "Entertain", R.drawable.theater_masks),
        Category(4, "ShopStop",  R.drawable.shop),
        Category(5, "Relax",     R.drawable.dorm_room),
        Category(6, "Wellbeing", R.drawable.hands_brain),
        Category(7, "Emergency", R.drawable.light_emergency_on),
        Category(8, "Services",  R.drawable.holding_hand_delivery)
    )

    /**
     * Maps each main category to a list of related Google Places types.
     */
    private val CATEGORY_TYPES: Map<Int, List<String>> = mapOf(
        1 to listOf("tourist_attraction", "museum", "art_gallery", "park"),
        2 to listOf("restaurant", "bar", "cafe", "bakery"),
        3 to listOf("movie_theater", "night_club", "bowling_alley", "casino"),
        4 to listOf("shopping_mall", "clothing_store", "department_store", "store", "supermarket"),
        5 to listOf("spa", "lodging", "campground"),
        6 to listOf("gym", "pharmacy", "doctor", "beauty_salon"),
        7 to listOf("hospital", "police", "fire_station"),
        8 to listOf("post_office", "bank", "atm", "gas_station", "car_repair")
    )

    /**
     * Nested map of subcategories for each main category.
     * Each subcategory contains more specific types of places.
     */
    private val SUBS: Map<Int, List<SubCategory>> = mapOf(
        1 to listOf(
            SubCategory("museums", "Museums", listOf("museum"), R.drawable.museum),
            SubCategory("gallery", "Art Gallery", listOf("art_gallery"), R.drawable.art_gallery),
            SubCategory("parks", "Parks", listOf("park"), R.drawable.park),
            SubCategory("attract", "Attractions", listOf("tourist_attraction"), R.drawable.tour_attr)
        ),
        2 to listOf(
            SubCategory("restaurants", "Restaurants", listOf("restaurant"), R.drawable.restaurants),
            SubCategory("cafes", "Cafes", listOf("cafe", "bakery"), R.drawable.cafe),
            SubCategory("bars", "Bars", listOf("bar"), R.drawable.bar)
        ),
        3 to listOf(
            SubCategory("movies", "Movie Theater", listOf("movie_theater"), R.drawable.theater),
            SubCategory("night", "Nightlife", listOf("night_club"), R.drawable.nightlife),
            SubCategory("casino", "Casino", listOf("casino"), R.drawable.casino)
        ),
        4 to listOf(
            SubCategory("malls", "Malls", listOf("shopping_mall"), R.drawable.shopping),
            SubCategory("clothes", "Clothing", listOf("clothing_store"), R.drawable.closet),
            SubCategory("groceries", "Groceries", listOf("supermarket", "store","department_store"), R.drawable.groceries)
        ),
        5 to listOf(
            SubCategory("spa", "Spa", listOf("spa"), R.drawable.spa),
            SubCategory("stays", "Lodging", listOf("lodging"), R.drawable.lodging),
            SubCategory("camp", "Campground", listOf("campground"), R.drawable.salon),
        ),
        6 to listOf(
            SubCategory("gym", "Gym", listOf("gym"), R.drawable.gym),
            SubCategory("pharmacy", "Pharmacy", listOf("pharmacy"), R.drawable.pharmacy),
            SubCategory("salon", "Beauty Salon", listOf("beauty_salon"), R.drawable.salon)
        ),
        7 to listOf(
            SubCategory("hospital", "Hospitals", listOf("hospital"), R.drawable.hospital),
            SubCategory("police", "Police", listOf("police"), R.drawable.police),
            SubCategory("fire", "Fire Stations", listOf("fire_station"), R.drawable.fire)
        ),
        8 to listOf(
            SubCategory("post", "Post Office", listOf("post_office"), R.drawable.post),
            SubCategory("bank", "Bank & ATM", listOf("bank", "atm"), R.drawable.bank),
            SubCategory("gas", "Gas Station", listOf("gas_station"), R.drawable.gas),
            SubCategory("auto", "Auto Repair", listOf("car_repair"), R.drawable.services)
        )
    )

    /** Returns a list of all main categories. */
    fun allCategories(): List<Category> = MAIN

    /** Returns all subcategories belonging to a specific main category. */
    fun subCategoriesOf(categoryId: Int): List<SubCategory> =
        SUBS[categoryId].orEmpty()

    /** Returns all included Google Place types for a given main category. */
    fun includedTypesForCategory(categoryId: Int): List<String> =
        CATEGORY_TYPES[categoryId].orEmpty()

    /** Returns included Google Place types for a specific subcategory. */
    fun includedTypesForSub(categoryId: Int, subId: String): List<String> =
        SUBS[categoryId]?.firstOrNull { it.id == subId }?.includedTypes.orEmpty()

    /**
     * Combines all place types across multiple categories into a single distinct list.
     * Useful for aggregated searches (e.g., show everything under “Food & Fun”).
     */
    fun unionTypesFor(categories: List<Category>): List<String> =
        categories.flatMap { includedTypesForCategory(it.id) }.distinct()

    /**
     * Checks if a given [Place] matches a main category
     * based on its list of types.
     *
     * @return true if at least one of the place's types matches the category.
     */
    fun placeMatchesCategory(categoryId: Int, placeTypes: List<Place.Type>?): Boolean {
        val needed = includedTypesForCategory(categoryId)
        val types = placeTypes ?: return false
        return types.any { it.name.lowercase() in needed }
    }

    /**
     * Checks if a given [Place] matches a specific subcategory.
     *
     * @return true if the place’s types include any of the subcategory’s types.
     */
    fun placeMatchesSub(categoryId: Int, subId: String, placeTypes: List<Place.Type>?): Boolean {
        val needed = includedTypesForSub(categoryId, subId)
        val types = placeTypes ?: return false
        return types.any { it.name.lowercase() in needed }
    }

    /** Fetches a main category object by its ID, if it exists. */
    fun getCategoryById(id: Int): Category? =
        MAIN.firstOrNull { it.id == id }
}
