@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION")

package com.unh.hoppin_android_app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.ktx.auth
import com.unh.hoppin_android_app.ui.theme.cardColor
import com.unh.hoppin_android_app.viewmodels.TripItinerariesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------ Editorial helpers ------------------------ */

private fun extractEditorialSummary(place: Place): String? {
    val s = place.editorialSummary ?: return null
    runCatching { s.javaClass.getMethod("getOverview").invoke(s) as? String }
        .getOrNull()?.let { return it }
    runCatching { s.javaClass.getMethod("getText").invoke(s) as? String }
        .getOrNull()?.let { return it }
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
    val city = address?.substringAfterLast(",")?.trim()
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

/* ------------------------ Firestore: Review model ------------------------ */

data class Review(
    val id: String = "",
    val author: String = "Anonymous",
    val rating: Int = 0,             // 1..5
    val text: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

@Composable
fun PlaceDetailsScreen(
    placeId: String,
    onBack: () -> Unit,
    onOpenTripCard: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var name by remember { mutableStateOf<String?>(null) }
    var rating by remember { mutableStateOf<Double?>(null) }
    var ratingsTotal by remember { mutableStateOf<Int?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf<String?>(null) }
    var website by remember { mutableStateOf<Uri?>(null) }
    var latLng by remember { mutableStateOf<com.google.android.gms.maps.model.LatLng?>(null) }
    var openingNow by remember { mutableStateOf<Boolean?>(null) }

    var photos by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var editorial by remember { mutableStateOf<String?>(null) }

    // Reviews (Firestore live)
    var reviews by remember { mutableStateOf<List<Review>>(emptyList()) }

    // Editor card state
    var yourName by remember { mutableStateOf("") }
    var myRating by remember { mutableStateOf(0) }
    var myReviewText by remember { mutableStateOf("") }
    var mySubmitting by remember { mutableStateOf(false) }

    val tripVm: TripItinerariesViewModel = viewModel()
    val itineraries by tripVm.itineraries.collectAsState()
    var showTripDialog by remember { mutableStateOf(false) }

    // True if this place appears in any of the user's itineraries
    val isInAnyTrip = itineraries.any { it.placeIds.contains(placeId) }

    /* ---------- Places fetch (only small optimization here) ---------- */
    LaunchedEffect(placeId) {
        loading = true
        error = null
        photos = emptyList()

        val client = Places.createClient(ctx)

        runCatching {
            // 1) Fetch place (on IO thread)
            val p = withContext(Dispatchers.IO) {
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
                val fetched = Tasks.await(client.fetchPlace(fetch))
                fetched.place
            }

            // 2) Fill basics (back on main thread)
            name = p.name
            rating = p.rating
            ratingsTotal = p.userRatingsTotal
            address = p.address
            phone = p.phoneNumber
            website = p.websiteUri
            latLng = p.latLng

            // Robust open/closed detection
            openingNow = try {
                val openHours = p.openingHours
                val method = openHours?.javaClass?.methods?.find { it.name == "isOpenNow" }
                method?.invoke(openHours) as? Boolean
            } catch (_: Exception) {
                p.businessStatus?.name?.contains("OPEN", ignoreCase = true)
            }

            editorial = extractEditorialSummary(p)?.trim()
                ?: buildFallbackDescription(
                    name = p.name,
                    address = p.address,
                    types = p.types,
                    rating = p.rating,
                    ratingsTotal = p.userRatingsTotal,
                    openingNow = openingNow,
                    priceLevel = p.priceLevel
                )

            // 👉 We already have enough to show UI, stop the spinner now
            loading = false

            // 3) Fetch photos AFTER turning off loading (still on background thread)
            val metas: List<PhotoMetadata> = p.photoMetadatas ?: emptyList()
            if (metas.isNotEmpty()) {
                val bitmaps = withContext(Dispatchers.IO) {
                    val out = mutableListOf<Bitmap>()
                    for (m in metas.take(4)) { // limit to 4 to keep it lighter
                        runCatching {
                            val photoReq = FetchPhotoRequest.builder(m)
                                .setMaxWidth(1200)
                                .setMaxHeight(800)
                                .build()
                            val resp = Tasks.await(client.fetchPhoto(photoReq))
                            out += resp.bitmap
                        }
                    }
                    out.toList()
                }
                if (bitmaps.isNotEmpty()) {
                    photos = bitmaps
                }
            }
        }.onFailure {
            error = it.message ?: "Failed to load place"
            loading = false
        }
    }

    /* ---------- Firestore: live Top 5 reviews listener ---------- */
    DisposableEffect(placeId) {
        val db = Firebase.firestore
        val ref = db.collection("places")
            .document(placeId)
            .collection("reviews")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(5)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                reviews = emptyList()
                return@addSnapshotListener
            }
            reviews = snap?.documents?.map { doc ->
                Review(
                    id = doc.id,
                    author = doc.getString("author") ?: "Anonymous",
                    rating = (doc.getLong("rating") ?: 0L).toInt(),
                    text = doc.getString("text") ?: "",
                    createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
                )
            } ?: emptyList()
        }

        onDispose { reg.remove() }
    }

    fun postReview(placeName: String) {
        val ctxScope = scope
        ctxScope.launch {
            mySubmitting = true
            runCatching {
                val auth = Firebase.auth
                if (auth.currentUser == null) auth.signInAnonymously().await()
                val uid = Firebase.auth.currentUser!!.uid

                val author = yourName.ifBlank { "Anonymous" }
                val ratingSafe = myRating.coerceIn(1, 5)
                val text = myReviewText.trim()
                val placeNameForNotification = name ?: "a place"

                val reviewData = mapOf(
                    "userId" to uid,
                    "author" to author,
                    "rating" to ratingSafe,
                    "text" to text,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                val db = Firebase.firestore

                // 1) Store under the place (existing behavior)
                db.collection("places")
                    .document(placeId)
                    .collection("reviews")
                    .add(reviewData)
                    .await()

                // 2) Store in global reviews_all for Feed
                val globalData = reviewData + mapOf(
                    "placeId" to placeId,
                    "placeName" to placeNameForNotification
                )

                db.collection("reviews_all")
                    .add(globalData)
                    .await()

                return@runCatching placeNameForNotification
            }.onSuccess { pn ->
                yourName = ""
                myRating = 0
                myReviewText = ""
                snackbarHostState.showSnackbar("Thanks for your review!")
                NotificationRepositoryFirebase.createNotification(
                    title = "Review Posted",
                    message = "Your review for '$pn' has been published."
                )
            }.onFailure { e ->
                snackbarHostState.showSnackbar("Failed to post review: ${e.localizedMessage ?: "Unknown error"}")
                android.util.Log.e("PlaceDetailsScreen", "Review create failed", e)
            }
            mySubmitting = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = name ?: "Place details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },

                colors =  if(!isSystemInDarkTheme()){ TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xfff8f0e3),
                        titleContentColor = Color(0xFF000000)
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { inner ->
        when {
            loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Loading place details…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                }
            }

            error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                    colors = CardDefaults.elevatedCardColors(cardColor),
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Unable to load place",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .verticalScroll(rememberScrollState())
                        .padding(inner)
                ) {
                    /* ------------------------ Carousel ------------------------ */
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

                    /* ------------------------ Info card ------------------------ */
                    ElevatedCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(Color(0xfff8f0e3)),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                name.orEmpty(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                rating?.let { r ->
                                    Text(
                                        "⭐ ${"%.1f".format(r)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Black
                                    )
                                    ratingsTotal?.let { t ->
                                        Text(
                                            "  ·  $t reviews",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Black
                                        )
                                    }
                                } ?: Text(
                                    "No rating yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Black
                                )
                            }

                            address?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    it,
                                    color = Color.Black,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            openingNow?.let { open ->
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    tonalElevation = 1.dp
                                ) {
                                    val text = if (open) "Open now" else "Closed now"
                                    Text(
                                        text = text,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    /* ---------------- Uniform actions in a card ---------------- */
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Quick actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))

                        ElevatedCard(
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.elevatedCardColors(Color(0xfff8f0e3)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val shape = RoundedCornerShape(14.dp)

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // --- Add / Remove Trip button (TOGGLE) ---
                                item {
                                    FilledTonalButton(
                                        onClick = {
                                            if (isInAnyTrip) {
                                                // Remove this place from all itineraries where it appears
                                                val affected = itineraries.filter { it.placeIds.contains(placeId) }
                                                affected.forEach { trip ->
                                                    tripVm.removePlaceFromItinerary(trip.id, placeId)
                                                }
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Removed from trip itineraries")
                                                }
                                            } else {
                                                // Not in any trip yet → open picker dialog
                                                showTripDialog = true
                                            }
                                        },
                                        shape = shape
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = if (isInAnyTrip) "Remove from trips" else "Add to trip itinerary"
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isInAnyTrip) "In Trips" else "Add to Trip"
                                        )
                                    }
                                }

                                // Share
                                item {
                                    FilledTonalButton(
                                        onClick = {
                                            val ll = latLng
                                            val gmaps = if (ll != null) {
                                                "https://www.google.com/maps/search/?api=1&query=${ll.latitude},${ll.longitude}&query_place_id=$placeId"
                                            } else {
                                                "https://www.google.com/maps/search/?api=1&query=${Uri.encode(name ?: address ?: "")}"
                                            }
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, name ?: "Place")
                                                putExtra(Intent.EXTRA_TEXT, "${name ?: ""}\n$gmaps")
                                            }
                                            startActivity(ctx, Intent.createChooser(shareIntent, "Share place"), null)
                                        },
                                        shape = shape
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = "Share")
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share")
                                    }
                                }

                                // Call
                                item {
                                    FilledTonalButton(
                                        onClick = {
                                            phone?.let { tel ->
                                                val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$tel") }
                                                startActivity(ctx, intent, null)
                                            }
                                        },
                                        enabled = phone != null,
                                        shape = shape
                                    ) {
                                        Icon(Icons.Filled.Phone, contentDescription = "Call")
                                        Spacer(Modifier.width(6.dp))
                                        Text("Call")
                                    }
                                }

                                // Website
                                item {
                                    FilledTonalButton(
                                        onClick = {
                                            website?.let { uri ->
                                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                                startActivity(ctx, intent, null)
                                            }
                                        },
                                        enabled = website != null,
                                        shape = shape
                                    ) {
                                        Icon(Icons.Filled.Language, contentDescription = "Website")
                                        Spacer(Modifier.width(6.dp))
                                        Text("Website")
                                    }
                                }

                                // Directions
                                item {
                                    FilledTonalButton(
                                        onClick = {
                                            latLng?.let { ll ->
                                                val gmm = Uri.parse("geo:${ll.latitude},${ll.longitude}?q=${Uri.encode(name ?: "")}")
                                                val intent = Intent(Intent.ACTION_VIEW, gmm)
                                                startActivity(ctx, intent, null)
                                            }
                                        },
                                        enabled = latLng != null,
                                        shape = shape
                                    ) {
                                        Icon(Icons.Filled.Directions, contentDescription = "Directions")
                                        Spacer(Modifier.width(6.dp))
                                        Text("Directions", color = Color.Black)
                                    }
                                }
                            }
                        }
                    }

                    /* ----------------------------- About ----------------------------- */
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            "About",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        ElevatedCard(
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.elevatedCardColors(Color(0xfff8f0e3)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = editorial ?: "No description available.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    /* ---------------------------- Trip Card (navigate) ---------------------------- */
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            "Trip Card",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        ElevatedCard(
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                            colors = CardDefaults.elevatedCardColors(Color(0xfff8f0e3)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenTripCard(name.orEmpty()) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create Trip Card")
                            }
                        }
                    }

                    /* ------------------------- Top Reviews (Firestore) ------------------------ */
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Top Reviews",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))

                        if (reviews.isEmpty()) {
                            ElevatedCard(
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.elevatedCardColors(Color(0xfff8f0e3)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        "No reviews yet. Be the first to write one!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                reviews.forEach { r ->
                                    ElevatedCard(
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                                        colors = CardDefaults.elevatedCardColors(Color(0xfff8f0e3)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
                                            Row {
                                                (1..5).forEach { i ->
                                                    Icon(
                                                        imageVector = if (i <= r.rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = r.author.ifBlank { "Anonymous" },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.Black
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            if (r.text.isNotBlank()) {
                                                Text(r.text,color = Color.Black, style = MaterialTheme.typography.bodyMedium)
                                                Spacer(Modifier.height(4.dp))
                                            }
                                            Text(
                                                formatShortDateTime(r.createdAt.toDate()),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    /* ---------------------- Write a review (editor card) -------------------- */
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Write a review",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))

                        ElevatedCard(
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                            colors = CardDefaults.elevatedCardColors(Color(0xfff8f0e3)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                OutlinedTextField(
                                    value = yourName,
                                    onValueChange = { yourName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Your name (optional)",color = Color(0xff333333)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(Color.Black)
                                )

                                Spacer(Modifier.height(10.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    (1..5).forEach { i ->
                                        IconButton(onClick = { myRating = i }) {
                                            Icon(
                                                imageVector = if (i <= myRating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                                contentDescription = "Rate $i",
                                                tint = Color(0xff333333)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = myReviewText,
                                    onValueChange = { myReviewText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Add your comment",color = Color(0xff333333)) },
                                    placeholder = { Text("What did you like? Any tips for others?") },
                                    minLines = 3,
                                    maxLines = 6,
                                    colors = OutlinedTextFieldDefaults.colors(Color.Black)
                                )

                                Spacer(Modifier.height(10.dp))

                                Button(
                                    onClick = { postReview(name ?: "this place") },
                                    enabled = !mySubmitting && myRating in 1..5 && myReviewText.isNotBlank(),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(if (mySubmitting) "Posting…" else "Post", color = Color.Black)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    // ---------------------- Add to Trip Dialog ----------------------
    if (showTripDialog) {
        AlertDialog(
            onDismissRequest = { showTripDialog = false },
            title = { Text("Add to Trip Itinerary") },
            text = {
                if (itineraries.isEmpty()) {
                    Text(
                        "You don't have any trip itineraries yet.\n" +
                                "Create one in the Trips section, then come back and add this place.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Column {
                        Text(
                            "Choose a trip to add this place:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        itineraries.forEach { itinerary ->
                            FilledTonalButton(
                                onClick = {
                                    tripVm.addPlaceToItinerary(itinerary.id, placeId)
                                    showTripDialog = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(itinerary.name)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTripDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/* ---------------------- Date helper ---------------------- */

private fun formatShortDateTime(date: Date): String {
    val fmt = SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
    return fmt.format(date)
}
