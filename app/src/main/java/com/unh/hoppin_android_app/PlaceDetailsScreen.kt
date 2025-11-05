@file:OptIn(ExperimentalMaterial3Api::class)

package com.unh.hoppin_android_app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import androidx.core.content.ContextCompat.startActivity
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.google.android.libraries.places.api.model.PhotoMetadata
import kotlin.collections.isNotEmpty
import kotlin.collections.take

private fun extractEditorialSummary(place: Place): String? {
    val s = place.editorialSummary ?: return null
    runCatching { s.javaClass.getMethod("getOverview").invoke(s) as? String }
        .getOrNull()
        ?.let { return it }
    runCatching { s.javaClass.getMethod("getText").invoke(s) as? String }
        .getOrNull()
        ?.let { return it }
    return null
}

private fun priceLevelToText(priceLevel: Int?): String? = when (priceLevel) {
    0 -> "$"
    1 -> "$"
    2 -> "$$"
    3 -> "$$$"
    4 -> "$$$$"
    else -> null
}

private fun buildFallbackDescription(
    name: String?,
    address: String?,
    types: List<Place.Type>?,
    rating: Double?,
    ratingsTotal: Int?,
    openingNow: Boolean?,
    priceLevel: Int?
): String {
    val city = address?.substringAfterLast(",")?.trim() // crude city-ish pull
    val typeText = types?.firstOrNull()?.name
        ?.lowercase()
        ?.replace('_', ' ')
        ?.replaceFirstChar { it.uppercase() }

    val priceText = priceLevelToText(priceLevel)
    val parts = mutableListOf<String>()

    if (typeText != null && city != null) parts += "$typeText in $city"
    else if (typeText != null) parts += typeText
    else if (city != null) parts += "Located in $city"

    rating?.let { r ->
        val base = "Rated ${"%.1f".format(r)}"
        parts += if ((ratingsTotal ?: 0) > 0) "$base (${ratingsTotal} reviews)" else base
    }

    openingNow?.let { parts += if (it) "Open now" else "Closed now" }
    priceText?.let { parts += it }

    val lead = name ?: "This place"
    return listOfNotNull(lead, parts.takeIf { it.isNotEmpty() }?.joinToString(" · "))
        .joinToString(" — ")
}

@Composable
fun PlaceDetailsScreen(
    placeId: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var name by remember { mutableStateOf<String?>(null) }
    var rating by remember { mutableStateOf<Double?>(null) }
    var ratingsTotal by remember { mutableStateOf<Int?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf<String?>(null) }
    var website by remember { mutableStateOf<Uri?>(null) }
    var latLng by remember { mutableStateOf<com.google.android.gms.maps.model.LatLng?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var openingNow by remember { mutableStateOf<Boolean?>(null) }
    var photos by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var editorial by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(placeId) {
        loading = true
        error = null
        val client = Places.createClient(ctx)
        runCatching {
            val fields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.RATING,
                Place.Field.USER_RATINGS_TOTAL,
                Place.Field.ADDRESS,
                Place.Field.PHONE_NUMBER,
                Place.Field.WEBSITE_URI,
                Place.Field.OPENING_HOURS,
                Place.Field.PHOTO_METADATAS,
                Place.Field.LAT_LNG,
                Place.Field.BUSINESS_STATUS,
                Place.Field.EDITORIAL_SUMMARY,
                Place.Field.TYPES,
                Place.Field.PRICE_LEVEL
            )
            val fetch = FetchPlaceRequest.builder(placeId, fields).build()
            val fetched = withContext(Dispatchers.IO) { Tasks.await(client.fetchPlace(fetch)) }
            val p = fetched.place
            editorial = extractEditorialSummary(p)?.trim()
                ?: buildFallbackDescription(
                    name = p.name,
                    address = p.address,
                    types = p.types,
                    rating = p.rating,
                    ratingsTotal = p.userRatingsTotal,
                    openingNow = openingNow,           // set a few lines later; we’ll recompute below
                    priceLevel = p.priceLevel
                )

            name = p.name
            rating = p.rating
            ratingsTotal = p.userRatingsTotal
            address = p.address
            phone = p.phoneNumber
            website = p.websiteUri
            latLng = p.latLng

            // ✅ Robust open/closed detection
            openingNow = try {
                val openHours = p.openingHours
                val method = openHours?.javaClass?.methods?.find { it.name == "isOpenNow" }
                method?.invoke(openHours) as? Boolean
            } catch (_: Exception) {
                p.businessStatus?.name?.contains("OPEN", ignoreCase = true)
            }

            if (extractEditorialSummary(p).isNullOrBlank()) {
                editorial = buildFallbackDescription(
                    name = p.name,
                    address = p.address,
                    types = p.types,
                    rating = p.rating,
                    ratingsTotal = p.userRatingsTotal,
                    openingNow = openingNow,
                    priceLevel = p.priceLevel
                )
            }

            val metas: List<PhotoMetadata> = p.photoMetadatas ?: emptyList()
            if (metas.isNotEmpty()) {
                val take = metas.take(7)
                val bitmaps = mutableListOf<Bitmap>()
                for (m in take) {
                    runCatching {
                        val photoReq = FetchPhotoRequest.builder(m)
                            .setMaxWidth(1600)
                            .setMaxHeight(1000)
                            .build()
                        val bmp = withContext(Dispatchers.IO) { Tasks.await(client.fetchPhoto(photoReq)).bitmap }
                        bitmaps += bmp
                    }
                }
                photos = bitmaps
                photo = bitmaps.firstOrNull()
            }
        }.onFailure {
            error = it.message ?: "Failed to load place"
        }
        loading = false
    }



    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name ?: "Place details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        when {
            loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) { Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error) }

            else -> {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(inner)
                ) {
                    // --- Carousel ---
                    if (photos.isNotEmpty()) {
                        val pagerState = rememberPagerState(initialPage = 0, pageCount = { photos.size })
                        Column {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                            ) { page ->
                                Image(
                                    bitmap = photos[page].asImageBitmap(),
                                    contentDescription = name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            // Dots
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                repeat(photos.size) { index ->
                                    val active = pagerState.currentPage == index
                                    Box(
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .size(if (active) 8.dp else 6.dp)
                                            .background(
                                                if (active) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(50)
                                            )
                                    )
                                }
                            }
                        }
                    } else {
                        // Fallback if no photos
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Title + rating
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(name.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            rating?.let { r ->
                                Text("⭐ ${"%.1f".format(r)}", style = MaterialTheme.typography.bodyMedium)
                                ratingsTotal?.let { t -> Text("  ·  $t reviews", style = MaterialTheme.typography.bodyMedium) }
                            } ?: Text("No rating", style = MaterialTheme.typography.bodyMedium)
                        }
                        address?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = editorial ?: "No description available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }



                    // Quick actions
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val shape = RoundedCornerShape(16.dp)

                        // Call
                        OutlinedButton(
                            onClick = {
                                phone?.let { tel ->
                                    val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$tel") }
                                    startActivity(ctx, intent, null)
                                }
                            },
                            shape = shape
                        ) {
                            Icon(Icons.Filled.Phone, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Call")
                        }

                        // Website
                        OutlinedButton(
                            onClick = {
                                website?.let { uri ->
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    startActivity(ctx, intent, null)
                                }
                            },
                            shape = shape
                        ) {
                            Icon(Icons.Filled.Language, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Website")
                        }

                        // Directions
                        OutlinedButton(
                            onClick = {
                                latLng?.let { ll ->
                                    val gmm = Uri.parse("geo:${ll.latitude},${ll.longitude}?q=${Uri.encode(name ?: "")}")
                                    val intent = Intent(Intent.ACTION_VIEW, gmm)
                                    startActivity(ctx, intent, null)
                                }
                            },
                            shape = shape
                        ) {
                            Icon(Icons.Filled.Directions, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Directions")
                        }
                    }

                    // Open now
                    openingNow?.let { open ->
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 1.dp
                        ) {
                            val text = if (open) "Open now" else "Closed now"
                            val color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            Text(
                                text = text,
                                color = color,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
