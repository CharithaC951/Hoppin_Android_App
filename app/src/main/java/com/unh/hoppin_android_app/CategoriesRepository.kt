package com.unh.hoppin_android_app

import com.google.android.libraries.places.api.model.Place

data class SubCategory(
    val id: String,
    val title: String,
    val includedTypes: List<String>,
    val image: Int
)

object CategoriesRepository {

    val MAIN: List<Category> = listOf(
        Category(1,"Explore",   R.drawable.binoculars),
        Category(2,"Refresh",   R.drawable.hamburger_soda),
        Category(3,"Entertain", R.drawable.theater_masks),
        Category(4,"ShopStop",  R.drawable.shop),
        Category(5,"Relax",     R.drawable.dorm_room),
        Category(6,"Wellbeing", R.drawable.hands_brain),
        Category(7,"Emergency", R.drawable.light_emergency_on),
        Category(8,"Services",  R.drawable.holding_hand_delivery)
    )

    private val CATEGORY_TYPES: Map<Int, List<String>> = mapOf(
        1 to listOf("tourist_attraction","museum","art_gallery","park"),
        2 to listOf("restaurant","bar","cafe","bakery"),
        3 to listOf("movie_theater","night_club","bowling_alley","casino"),
        4 to listOf("shopping_mall","clothing_store","department_store","store","supermarket"),
        5 to listOf("spa","lodging","campground"),
        6 to listOf("gym","pharmacy","doctor","beauty_salon"),
        7 to listOf("hospital","police","fire_station"),
        8 to listOf("post_office","bank","atm","gas_station","car_repair")
    )

    private val SUBS: Map<Int, List<SubCategory>> = mapOf(
        1 to listOf(
            SubCategory("museums","Museums", listOf("museum"),R.drawable.museum),
            SubCategory("gallery","Art Gallery", listOf("art_gallery"),R.drawable.museum),
            SubCategory("parks","Parks", listOf("park"),R.drawable.museum),
            SubCategory("attract","Attractions", listOf("tourist_attraction"),R.drawable.museum)
        ),
        2 to listOf(
            SubCategory("restaurants","Restaurants", listOf("restaurant"),R.drawable.museum),
            SubCategory("cafes","Cafes", listOf("cafe","bakery"),R.drawable.museum),
            SubCategory("bars","Bars", listOf("bar"),R.drawable.museum)
        ),
        3 to listOf(
            SubCategory("movies","Movie Theater", listOf("movie_theater"),R.drawable.museum),
            SubCategory("night","Nightlife", listOf("night_club"),R.drawable.museum),
            SubCategory("bowling","Bowling", listOf("bowling_alley"),R.drawable.museum),
            SubCategory("casino","Casino", listOf("casino"),R.drawable.museum)
        ),
        4 to listOf(
            SubCategory("malls","Malls", listOf("shopping_mall"),R.drawable.museum),
            SubCategory("clothes","Clothing", listOf("clothing_store"),R.drawable.museum),
            SubCategory("dept","Dept. Stores", listOf("department_store"),R.drawable.museum),
            SubCategory("groceries","Groceries", listOf("supermarket","store"),R.drawable.museum)
        ),
        5 to listOf(
            SubCategory("spa","Spa", listOf("spa"),R.drawable.museum),
            SubCategory("stays","Stay / Lodging", listOf("lodging"),R.drawable.museum),
            SubCategory("camp","Campground", listOf("campground"),R.drawable.museum),
        ),
        6 to listOf(
            SubCategory("gym","Gym", listOf("gym"),R.drawable.museum),
            SubCategory("pharmacy","Pharmacy", listOf("pharmacy"),R.drawable.museum),
            SubCategory("doctor","Doctors", listOf("doctor"),R.drawable.museum),
            SubCategory("salon","Beauty Salon", listOf("beauty_salon"),R.drawable.museum)
        ),
        7 to listOf(
            SubCategory("hospital","Hospitals", listOf("hospital"),R.drawable.museum),
            SubCategory("police","Police", listOf("police"),R.drawable.museum),
            SubCategory("fire","Fire Stations", listOf("fire_station"),R.drawable.museum)
        ),
        8 to listOf(
            SubCategory("post","Post Office", listOf("post_office"),R.drawable.museum),
            SubCategory("bank","Bank & ATM", listOf("bank","atm"),R.drawable.museum),
            SubCategory("gas","Gas Station", listOf("gas_station"),R.drawable.museum),
            SubCategory("auto","Auto Repair", listOf("car_repair"),R.drawable.museum)
        )
    )

    fun allCategories(): List<Category> = MAIN
    fun subCategoriesOf(categoryId: Int) = SUBS[categoryId].orEmpty()
    fun includedTypesForCategory(categoryId: Int) = CATEGORY_TYPES[categoryId].orEmpty()
    fun includedTypesForSub(categoryId: Int, subId: String) =
        SUBS[categoryId]?.firstOrNull { it.id == subId }?.includedTypes.orEmpty()

    fun unionTypesFor(categories: List<Category>): List<String> =
        categories.flatMap { includedTypesForCategory(it.id) }.distinct()

    fun placeMatchesCategory(categoryId: Int, placeTypes: List<Place.Type>?): Boolean {
        val needed = includedTypesForCategory(categoryId)
        val types = placeTypes ?: return false
        return types.any { it.name.lowercase() in needed }
    }

    fun placeMatchesSub(categoryId: Int, subId: String, placeTypes: List<Place.Type>?): Boolean {
        val needed = includedTypesForSub(categoryId, subId)
        val types = placeTypes ?: return false
        return types.any { it.name.lowercase() in needed }
    }

    fun getCategoryById(id: Int) = MAIN.firstOrNull { it.id == id }
}
