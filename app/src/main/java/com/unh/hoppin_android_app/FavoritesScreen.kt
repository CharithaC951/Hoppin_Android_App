package com.unh.hoppin_android_app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.*

data class UiPlaceFav(
    val id: String,
    val title: String,
    val photo: Bitmap?,
    val distanceMeters: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onPlaceClick: (UiPlaceFav) -> Unit,
    center: LatLng = LatLng(41.3083, -72.9279)
) {
    val ids by FavoritesRepositoryFirebase.favoriteIdsFlow().collectAsState(initial = emptySet())

    val context = LocalContext.current
    val client = remember { Places.createClient(context) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var places by remember { mutableStateOf<List<UiPlaceFav>>(emptyList()) }

    LaunchedEffect(ids, center) {
        loading = true; error = null
        try {
            places = hydratePlaces(client, ids.toList(), center)
        } catch (t: Throwable) {
            error = t.message ?: "Failed to load favourites"
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favourites") },
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
                Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null -> Box(
                Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center
            ) { Text(error!!) }

            else -> {
                val list = places
                if (list.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(inner),
                        contentAlignment = Alignment.Center
                    ) { Text("No favourites yet") }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(list, key = { it.id }) { p ->
                            FavoriteCard(
                                place = p,
                                onClick = { onPlaceClick(p) }
                            )
                        }
                    }
                }
            }
        }
    }
}


private suspend fun hydratePlaces(
    client: PlacesClient,
    placeIds: List<String>,
    center: LatLng
): List<UiPlaceFav> {
    if (placeIds.isEmpty()) return emptyList()
    val fields = listOf(
        Place.Field.ID,
        Place.Field.NAME,
        Place.Field.PHOTO_METADATAS,
        Place.Field.LAT_LNG
    )

    val results = mutableListOf<UiPlaceFav>()
    for (id in placeIds) {
        val fetched = runCatching {
            val req = FetchPlaceRequest.builder(id, fields).build()
            client.fetchPlace(req).await().place
        }.getOrNull() ?: continue

        val title = fetched.name ?: continue

        val meta: PhotoMetadata? = fetched.photoMetadatas?.firstOrNull()
        val bmp: Bitmap? = if (meta != null) {
            runCatching {
                val pr = FetchPhotoRequest.builder(meta)
                    .setMaxWidth(1280).setMaxHeight(720).build()
                client.fetchPhoto(pr).await().bitmap
            }.getOrNull()
        } else null

        val d = fetched.latLng?.let { ll ->
            distanceMeters(center.latitude, center.longitude, ll.latitude, ll.longitude).roundToInt()
        }

        results += UiPlaceFav(id = id, title = title, photo = bmp, distanceMeters = d)
    }
    return results.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
}


@Composable
private fun FavoriteCard(
    place: UiPlaceFav,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(4.dp),
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
                    modifier = Modifier
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
                        overflow = TextOverflow.Ellipsis
                    )
                    place.distanceMeters?.let {
                        Text(formatDistance(it), style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = {
                    scope.launch {
                        FavoritesRepositoryFirebase.toggle(place.id)
                    }
                }) {
                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Remove")
                }
            }
        }
    }
}


private fun distanceMeters(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun formatDistance(m: Int): String =
    if (m >= 1000) String.format("%.1f km away", m / 1000.0) else "$m m away"
