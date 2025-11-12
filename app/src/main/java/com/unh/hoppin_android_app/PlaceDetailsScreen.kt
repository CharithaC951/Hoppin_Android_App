@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION")

package com.unh.hoppin_android_app

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import coil.compose.AsyncImage
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
    onOpenTripCard: () -> Unit
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
    var isFav by remember { mutableStateOf(false) }

    // Reviews (Firestore live)
    var reviews by remember { mutableStateOf<List<Review>>(emptyList()) }

    // Editor card state
    var yourName by remember { mutableStateOf("") }
    var myRating by remember { mutableStateOf(0) }
    var myReviewText by remember { mutableStateOf("") }
    var mySubmitting by remember { mutableStateOf(false) }

    /* ---------- Favorites init whenever details load ---------- */
    LaunchedEffect(placeId, name, address, rating, ratingsTotal) {
        isFav = FavoritesStore.contains(placeId)
    }

    /* ---------- Places fetch ---------- */
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

            // Photos → carousel (up to 7)
            val metas: List<PhotoMetadata> = p.photoMetadatas ?: emptyList()
            if (metas.isNotEmpty()) {
                val bitmaps = mutableListOf<Bitmap>()
                for (m in metas.take(7)) {
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
            }
        }.onFailure {
            error = it.message ?: "Failed to load place"
        }
        loading = false
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

    fun buildUiPlaceForFavorites(): UiPlace =
        UiPlace(
            id = placeId,
            title = name ?: "",
            photo = photos.firstOrNull() ?: null,
        )

    fun postReview() {
        val ctxScope = scope
        ctxScope.launch {
            mySubmitting = true
            runCatching {
                // Ensure we have an auth user (anonymous ok)
                val auth = Firebase.auth
                if (auth.currentUser == null) auth.signInAnonymously().await()
                val uid = Firebase.auth.currentUser!!.uid

                val author = yourName.ifBlank { "Anonymous" }
                val ratingSafe = myRating.coerceIn(1, 5)
                val text = myReviewText.trim()

                val data = mapOf(
                    "userId" to uid,                           // REQUIRED BY YOUR RULES
                    "author" to author,
                    "rating" to ratingSafe,
                    "text" to text,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                Firebase.firestore.collection("places")
                    .document(placeId)
                    .collection("reviews")
                    .add(data)
                    .await()
            }.onSuccess {
                yourName = ""
                myRating = 0
                myReviewText = ""
                snackbarHostState.showSnackbar("Thanks for your review!")
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
                title = { Text(name ?: "Place details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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

                    // Title + rating + address
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

                    /* ---------------- Uniform actions in a LazyRow ---------------- */
                    val shape = RoundedCornerShape(14.dp)

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Favorite (toggle)
                        item {
                            FilledTonalButton(
                                onClick = {
                                    if (isFav) {
                                        FavoritesStore.remove(placeId)
                                        isFav = false
                                    } else {
                                        FavoritesStore.add(buildUiPlaceForFavorites())
                                        isFav = true
                                    }
                                },
                                shape = shape
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (isFav) "Remove from favorites" else "Add to favorites"
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (isFav) "Favorited" else "Favorite")
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
                                Text("Directions")
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    /* ----------------------------- About ----------------------------- */
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = editorial ?: "No description available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    /* ---------------------------- Trip Card (navigate) ---------------------------- */
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Trip Card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { onOpenTripCard() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null) // reuse share icon, or swap to an image icon you prefer
                            Spacer(Modifier.width(8.dp))
                            Text("Create Trip Card")
                        }
                    }


                    /* --------------------------- Open now chip --------------------------- */
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

                    /* ------------------------- Top Reviews (Firestore) ------------------------ */
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Top Reviews",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (reviews.isEmpty()) {
                            ElevatedCard(
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        "No reviews yet. Be the first to write one!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            reviews.forEach { r ->
                                ElevatedCard(
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
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
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        if (r.text.isNotBlank()) {
                                            Text(r.text, style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.height(4.dp))
                                        }
                                        Text(
                                            r.createdAt.toDate().toString(), // format as needed
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    /* ---------------------- Write a review (editor card) -------------------- */
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Write a review",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))

                    ElevatedCard(
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            var yourName by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = yourName,
                                onValueChange = { yourName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Your name (optional)") },
                                singleLine = true
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (1..5).forEach { i ->
                                    IconButton(onClick = { myRating = i }) {
                                        Icon(
                                            imageVector = if (i <= myRating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Rate $i"
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = myReviewText,
                                onValueChange = { myReviewText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Add your comment") },
                                placeholder = { Text("What did you like? Any tips for others?") },
                                minLines = 3,
                                maxLines = 6
                            )

                            Spacer(Modifier.height(10.dp))

                            Button(
                                onClick = { postReview() },
                                enabled = !mySubmitting && myRating in 1..5 && myReviewText.isNotBlank(),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(if (mySubmitting) "Posting…" else "Post")
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}
