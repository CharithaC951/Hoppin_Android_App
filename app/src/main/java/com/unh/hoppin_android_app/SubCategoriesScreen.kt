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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.maps.model.LatLng
import com.unh.hoppin_android_app.viewmodels.RecommendationViewModel
import kotlinx.coroutines.launch
import kotlin.collections.getOrNull

/**
 * SubCategoriesScreen
 *
 * Displays subcategories for a selected top-level category.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SubCategoriesScreen(
    navController: NavController,
    catId: Int,
    vm: RecommendationViewModel = viewModel(),
    center: LatLng = LatLng(41.31, -72.93),
    perCategory: Int = 20
) {
    val context = LocalContext.current
    val ui = vm.ui.collectAsState().value

    val category: Category? = remember(catId) { CategoriesRepository.getCategoryById(catId) }
    val subs: List<SubCategory> = remember(catId) { CategoriesRepository.subCategoriesOf(catId) }

    LaunchedEffect(catId) {
        val oneCat = category?.let { listOf(it) } ?: emptyList()
        if (ui.sections.isEmpty()) {
            vm.load(
                context = context,
                center = center,
                categories = oneCat,
                perCategory = perCategory,
                fetchThumbnails = true
            )
        } else {
            vm.deriveLocally(center = center, categories = oneCat, perCategory = perCategory)
        }
    }

    val pagerState = rememberPagerState(pageCount = { subs.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.title ?: "Category", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            ui.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            ui.error != null -> Text(
                text = ui.error ?: "Something went wrong",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
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

                    HorizontalPager(
                        state = pagerState,
                        pageSpacing = 16.dp,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val sub = subs.getOrNull(page)
                        val resolvedType = resolveTypeFromTitle(sub?.title.orEmpty())

                        // ✅ Tap the pane to open the list for that sub-type
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(enabled = resolvedType.isNotEmpty()) {
                                    navController.navigate("discover?type=$resolvedType&categoryId=")
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
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
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
 */
private fun resolveTypeFromTitle(title: String): String {
    val map = mapOf(
        // Category 1
        "Tourist Attraction" to "tourist_attraction",
        "Museum" to "museum",
        "Art Gallery" to "art_gallery",
        "Park" to "park",

        // Category 2
        "Restaurant" to "restaurant",
        "Restaurants" to "restaurant",
        "Bar" to "bar",
        "Bars" to "bar",
        "Cafe" to "cafe",
        "Cafes" to "cafe",
        "Bakery" to "bakery",
        "Bakeries" to "bakery",

        // Category 3
        "Movie Theater" to "movie_theater",
        "Night Club" to "night_club",
        "Bowling Alley" to "bowling_alley",
        "Casino" to "casino",

        // Category 4
        "Shopping Mall" to "shopping_mall",
        "Clothing Store" to "clothing_store",
        "Department Store" to "department_store",
        "Store" to "store",
        "Supermarket" to "supermarket",

        // Category 5
        "Spa" to "spa",
        "Lodging" to "lodging",
        "Campground" to "campground",

        // Category 6
        "Gym" to "gym",
        "Pharmacy" to "pharmacy",
        "Doctor" to "doctor",
        "Beauty Salon" to "beauty_salon",

        // Category 7
        "Hospital" to "hospital",
        "Police" to "police",
        "Fire Station" to "fire_station",

        // Category 8
        "Post Office" to "post_office",
        "Bank" to "bank",
        "ATM" to "atm",
        "Gas Station" to "gas_station",
        "Car Repair" to "car_repair"
    )

    return map[title] ?: title
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
}
