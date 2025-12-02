package com.unh.hoppin_android_app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import kotlin.collections.getOrNull

/**
 * SubCategoriesScreen
 *
 * Displays subcategories for a selected top-level category.
 * NOTE: This version does NOT hit the network – it only shows static images + titles.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SubCategoriesScreen(
    navController: NavController,
    catId: Int,
) {

    val category: Category? = remember(catId) { CategoriesRepository.getCategoryById(catId) }
    val subs: List<SubCategory> = remember(catId) { CategoriesRepository.subCategoriesOf(catId) }

    val pagerState = rememberPagerState(pageCount = { subs.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        category?.title ?: "Category",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Subcategory chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                itemsIndexed(subs) { index, sub ->
                    FilterChip(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        label = { Text(sub.title) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Fullscreen pager with big images
            HorizontalPager(
                state = pagerState,
                pageSpacing = 16.dp,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val sub = subs.getOrNull(page)

                // 1) Try to resolve a type from the title ("Restaurants" -> "restaurant", etc.)
                val rawType = resolveTypeFromTitle(sub?.title.orEmpty())

                // 2) Only trust that type if it is actually valid for this category
                val validTypesForCat = CategoryToTypes[catId].orEmpty()
                val resolvedType = if (rawType in validTypesForCat) rawType else ""

                // Tap pane -> go to Discover; if resolvedType is empty,
                // Discover will rely on categoryId and show all types for that category.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            val encodedType = resolvedType // if you ever need Uri.encode, do it here
                            navController.navigate(
                                "discover?type=$encodedType&categoryId=$catId"
                            )
                        }
                ) {
                    SubPaneCard(
                        title = sub?.title ?: "",
                        image = sub?.image ?: R.drawable.museum,
                        height = 720.dp
                    )
                }
            }
        }
    }
}

/**
 * SubPaneCard
 *
 * Large visual card used for each pager page.
 */
@Composable
private fun SubPaneCard(
    title: String,
    image: Int,
    height: Dp = 420.dp
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
        ) {
            Image(
                painter = painterResource(id = image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(5) { Text("•", color = Color.White.copy(alpha = 0.9f)) }
                    }
                }
            }
        }
    }
}

/**
 * Preview for the SubCategoriesScreen.
 */
@Preview(showBackground = true)
@Composable
fun SubCategoriesPreview() {
    val navController = rememberNavController()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        SubCategoriesScreen(
            navController = navController,
            catId = 1,
        )
    }
}

/* ----------------------------- Helpers ----------------------------- */

/**
 * Maps pane titles to Places API / type strings.
 * If the title isn't in the known map, we fall back to snake_case.
 *
 * IMPORTANT: The actual type strings here should match the ones in CategoryToTypes.
 */
private fun resolveTypeFromTitle(title: String): String {
    val map = mapOf(
        // Category 1 – Attractions
        "Tourist Attraction" to "tourist_attraction",
        "Tourist Attractions" to "tourist_attraction",
        "Museum" to "museum",
        "Museums" to "museum",
        "Art Gallery" to "art_gallery",
        "Art Galleries" to "art_gallery",
        "Park" to "park",
        "Parks" to "park",

        // Category 2 – Food & Drink
        "Restaurant" to "restaurant",
        "Restaurants" to "restaurant",
        "Bar" to "bar",
        "Bars" to "bar",
        "Cafe" to "cafe",
        "Cafes" to "cafe",
        "Coffee Shop" to "cafe",
        "Coffee Shops" to "cafe",
        "Bakery" to "bakery",
        "Bakeries" to "bakery",

        // Category 3 – Entertainment
        "Movie Theater" to "movie_theater",
        "Movie Theaters" to "movie_theater",
        "Cinema" to "movie_theater",
        "Cinemas" to "movie_theater",
        "Night Club" to "night_club",
        "Night Clubs" to "night_club",
        "Bowling Alley" to "bowling_alley",
        "Bowling Alleys" to "bowling_alley",
        "Casino" to "casino",
        "Casinos" to "casino",

        // Category 4 – Shopping
        "Shopping Mall" to "shopping_mall",
        "Shopping Malls" to "shopping_mall",
        "Clothing Store" to "clothing_store",
        "Clothing Stores" to "clothing_store",
        "Department Store" to "department_store",
        "Department Stores" to "department_store",
        "Store" to "store",
        "Stores" to "store",
        "Supermarket" to "supermarket",
        "Supermarkets" to "supermarket",

        // Category 5 – Stay & Relax
        "Spa" to "spa",
        "Spas" to "spa",
        "Lodging" to "lodging",
        "Hotel" to "lodging",
        "Hotels" to "lodging",
        "Campground" to "campground",
        "Campgrounds" to "campground",

        // Category 6 – Wellness & Services
        "Gym" to "gym",
        "Gyms" to "gym",
        "Pharmacy" to "pharmacy",
        "Pharmacies" to "pharmacy",
        "Doctor" to "doctor",
        "Doctors" to "doctor",
        "Clinic" to "doctor",
        "Clinics" to "doctor",
        "Beauty Salon" to "beauty_salon",
        "Beauty Salons" to "beauty_salon",

        // Category 7 – Emergency
        "Hospital" to "hospital",
        "Hospitals" to "hospital",
        "Police" to "police",
        "Police Station" to "police",
        "Police Stations" to "police",
        "Fire Station" to "fire_station",
        "Fire Stations" to "fire_station",

        // Category 8 – Essentials
        "Post Office" to "post_office",
        "Post Offices" to "post_office",
        "Bank" to "bank",
        "Banks" to "bank",
        "ATM" to "atm",
        "ATMs" to "atm",
        "Gas Station" to "gas_station",
        "Gas Stations" to "gas_station",
        "Car Repair" to "car_repair",
        "Car Repairs" to "car_repair",
        "Mechanic" to "car_repair",
        "Mechanics" to "car_repair"
    )

    return map[title] ?: title
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
}
