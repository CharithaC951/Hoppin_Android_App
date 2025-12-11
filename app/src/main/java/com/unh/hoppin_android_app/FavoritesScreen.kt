package com.unh.hoppin_android_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onPlaceClick: (UiPlace) -> Unit,
    center: LatLng = LatLng(41.3083, -72.9279)
) {
    val ids by FavoritesRepositoryFirebase.favoriteIdsFlow().collectAsState(initial = emptySet())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    // Create PlacesClient once per context
    val client: PlacesClient = remember(context) { Places.createClient(context) }

    var loading by remember { mutableStateOf(false) }
    var places by remember { mutableStateOf<List<UiPlace>>(emptyList()) }

    LaunchedEffect(ids, center, client) {
        loading = true
        // Heavy work off the main thread
        places = withContext(Dispatchers.IO) {
            hydrate(client, ids.toList(), center)
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favourites") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null)
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
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent
    ) { inner ->
        when {
            loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            places.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) {
                Text("No favourites yet")
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(places, key = { it.id }) { p ->
                    FavoriteCard(
                        place = p,
                        onClick = { onPlaceClick(p) },
                        onRemove = {
                            scope.launch {
                                runCatching { FavoritesRepositoryFirebase.remove(p.id) }
                                snackbar.currentSnackbarData?.dismiss()
                                snackbar.showSnackbar("Removed from favourites")
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Faster hydrate:
 *  - Runs network calls in parallel using coroutines
 *  - Uses smaller photos for faster download
 */
private suspend fun hydrate(
    client: PlacesClient,
    placeIds: List<String>,
    center: LatLng
): List<UiPlace> = coroutineScope {
    if (placeIds.isEmpty()) return@coroutineScope emptyList()

    val fields = listOf(
        Place.Field.ID,
        Place.Field.NAME,
        Place.Field.PHOTO_METADATAS,
        Place.Field.LAT_LNG
    )

    // Launch one async job per place ID (in IO dispatcher)
    val jobs = placeIds.map { id ->
        async(Dispatchers.IO) {
            // Fetch place
            val place = runCatching {
                client.fetchPlace(
                    FetchPlaceRequest.builder(id, fields).build()
                ).await().place
            }.getOrNull() ?: return@async null

            val meta: PhotoMetadata? = place.photoMetadatas?.firstOrNull()

            // Fetch a smaller photo (faster) if available
            val bmp = if (meta != null) {
                runCatching {
                    client.fetchPhoto(
                        FetchPhotoRequest
                            .builder(meta)
                            .setMaxWidth(600)   // was 1280
                            .setMaxHeight(400)  // was 720
                            .build()
                    ).await().bitmap
                }.getOrNull()
            } else {
                null
            }

            val d = place.latLng?.let { ll ->
                distanceMeters(
                    center.latitude,
                    center.longitude,
                    ll.latitude,
                    ll.longitude
                ).roundToInt()
            }

            val title = place.name ?: return@async null

            UiPlace(
                id = id,
                title = title,
                photo = bmp,
                distanceMeters = d
            )
        }
    }

    // Wait for all async jobs to finish, drop nulls, and sort by distance
    jobs.mapNotNull { it.await() }
        .sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
}

@Composable
private fun FavoriteCard(
    place: UiPlace,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.elevatedCardColors(Color(0xfff8f0e3)),
        onClick = onClick
    ) {
        Column {
            if (place.photo != null) {
                Image(
                    bitmap = place.photo.asImageBitmap(),
                    contentDescription = place.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFFEAEAEA))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        place.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.Black
                    )
                    place.distanceMeters?.let {
                        val label =
                            if (it >= 1000) String.format("%.1f km away", it / 1000.0)
                            else "$it m away"
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color= Color.Black
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

private fun distanceMeters(
    aLat: Double,
    aLon: Double,
    bLat: Double,
    bLon: Double
): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLon - aLon)
    val x = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(aLat)) *
            cos(Math.toRadians(bLat)) *
            sin(dLon / 2) * sin(dLon / 2)
    return R * 2 * atan2(sqrt(x), sqrt(1 - x))
}
