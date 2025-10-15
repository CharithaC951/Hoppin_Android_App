package com.unh.hoppin_android_app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.LocationRestriction
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPhotoResponse
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.libraries.places.api.net.SearchNearbyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

val gradientColors = listOf(
    Color(0xFFFF930F),
    Color(0xFFFFF95B))

val categories = listOf<Category>(
    Category(1,"Explore",R.drawable.binoculars),
    Category(2,"Refresh",R.drawable.hamburger_soda),
    Category(3,"Entertain",R.drawable.theater_masks),
    Category(4,"ShopStop",R.drawable.shop),
    Category(5,"Relax",R.drawable.dorm_room),
    Category(6,"Wellbeing",R.drawable.hands_brain),
    Category(7,"Emergency",R.drawable.light_emergency_on),
    Category(8,"Services",R.drawable.holding_hand_delivery)
)
@Composable
fun HomeScreen(
    navController: NavController,
    locationViewModel: LocationViewModel = viewModel(),
    userName: String,
    placesApiKey: String
) {
    HomeScreenContent(
        navController = navController,
        locationViewModel = locationViewModel,
        userName = userName,
        placesApiKey = placesApiKey
    )
}

@Composable
private fun HomeScreenContent(
    navController: NavController,
    locationViewModel: LocationViewModel,
    userName: String,
    placesApiKey: String
) {
    val context = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationData by locationViewModel.locationState.collectAsState()

    // Runtime permission (minimal)
    val hasLocationPermission by rememberLocationPermissionState()

    // Device location & address (street + city only)
    var deviceLatLng by remember { mutableStateOf<LatLng?>(null) }
    var streetCity by remember { mutableStateOf<String?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }

    // Nearby restaurant UI state
    var loading by remember { mutableStateOf(true) }
    var nearby by remember { mutableStateOf<NearbyRestaurantResult?>(null) }

    // Get location once permission is granted
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect
        loading = true
        try {
            val cts = CancellationTokenSource()
            val current = fused.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).await()

            val latLng = when {
                current != null -> LatLng(current.latitude, current.longitude)
                else -> {
                    fused.lastLocation.await()?.let { LatLng(it.latitude, it.longitude) }
                        ?: awaitOneLocationFix(fused)
                }
            }

            if (latLng == null) {
                // Fallback only if we truly couldn't get a fix
                locationError = "Location unavailable"
                deviceLatLng = LatLng(41.3083, -72.9279) // New Haven fallback
                streetCity = "New Haven"
            } else {
                deviceLatLng = latLng
                streetCity = reverseGeocodeStreetCity(context, latLng) ?: "Locating..."
            }
        } catch (e: SecurityException) {
            locationError = "Location permission not granted"
            deviceLatLng = LatLng(41.3083, -72.9279)
            streetCity = "New Haven"
        } catch (e: Exception) {
            locationError = "Location error"
            deviceLatLng = LatLng(41.3083, -72.9279)
            streetCity = "New Haven"
        }
    }

    // Fire Places with the SAME coordinates (radius = 1 km)
    LaunchedEffect(deviceLatLng) {
        val center = deviceLatLng ?: return@LaunchedEffect
        nearby = fetchNearbyRestaurant(
            context = context,
            apiKey = placesApiKey,
            center = center
        )
        loading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.hoppinbackground), // Placeholder for your background image
            contentDescription = "Background image",
            contentScale = ContentScale.Crop, // Scales the image to fill the bounds, cropping if necessary
            modifier = Modifier.fillMaxSize(),
            alpha = 0.2f
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE0E0E0),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp)
                                .graphicsLayer(alpha = 0.99f)
                                .drawWithCache {
                                    val brush = Brush.linearGradient(gradientColors)
                                    onDrawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = brush,
                                            blendMode = BlendMode.SrcIn
                                        )
                                    }
                                }
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Hello $userName",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "Ready to Hoppin ?",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp)
                    )

                    val parts = streetCity?.split(",", limit = 2)?.map { it.trim() }

                    Text(
                        text = when {
                            parts != null -> listOfNotNull(parts.getOrNull(0), parts.getOrNull(1))
                                .joinToString("\n") // 👈 single Text with a newline
                            loading && locationError == null -> "Fetching address…"
                            locationError != null -> "Unavailable"
                            else -> "Locating..."
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp // controls minimal vertical gap
                    )
                }

            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "Explore. Travel.\nInspire.",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 30.sp,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = "",
                onValueChange = { },
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF0F0F0),
                    unfocusedContainerColor = Color(0xFFF0F0F0),
                    disabledContainerColor = Color(0xFFF0F0F0),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            BrowseCategoriesSection(categories)
            when {
                loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                nearby?.error != null -> {
                    Text(
                        text = nearby?.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                else -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = nearby?.name ?: "No result",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            val bmp = nearby?.bitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Nearby restaurant photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                )
                            } else {
                                Text(
                                    "No photo available",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberLocationPermissionState(): State<Boolean> {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        granted = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) granted = true
        else launcher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }
    return remember { derivedStateOf { granted } }
}

@Suppress("MissingPermission")
private suspend fun awaitOneLocationFix(
    fused: FusedLocationProviderClient
): LatLng? = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { cont ->
        val req = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000L
        ).setWaitForAccurateLocation(true)
            .setMaxUpdates(1)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fused.removeLocationUpdates(this)
                val loc = result.lastLocation
                cont.resume(loc?.let { LatLng(it.latitude, it.longitude) })
            }
        }
        fused.requestLocationUpdates(req, cb, Looper.getMainLooper())
        cont.invokeOnCancellation { fused.removeLocationUpdates(cb) }
    }
}

private suspend fun reverseGeocodeStreetCity(
    context: android.content.Context,
    latLng: LatLng
): String? = withContext(Dispatchers.IO) {
    try {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val results = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
        val a = results?.firstOrNull() ?: return@withContext null

        val street = listOfNotNull(a.subThoroughfare, a.thoroughfare)
            .joinToString(" ")
            .ifBlank { a.thoroughfare ?: "" }
        val city = a.locality ?: a.subAdminArea ?: ""

        listOfNotNull(street.ifBlank { null }, city.ifBlank { null })
            .joinToString(", ")
            .ifBlank { a.getAddressLine(0) ?: "" }
    } catch (_: Exception) {
        null
    }
}

/* ---------------- Places (1 km radius) ---------------- */

private const val SEARCH_RADIUS_METERS = 1_000.0

private fun getPlacesClient(context: android.content.Context, apiKey: String): PlacesClient {
    if (!Places.isInitialized()) {
        Places.initialize(context.applicationContext, apiKey)
    }
    return Places.createClient(context)
}

private data class NearbyRestaurantResult(
    val name: String,
    val bitmap: Bitmap?,
    val error: String? = null
)

private suspend fun fetchNearbyRestaurant(
    context: android.content.Context,
    apiKey: String,
    center: LatLng
): NearbyRestaurantResult = withContext(Dispatchers.IO) {
    try {
        val client = getPlacesClient(context, apiKey)

        val placeFields = listOf(
            Place.Field.NAME,
            Place.Field.PHOTO_METADATAS,
            Place.Field.TYPES
        )

        val restriction: LocationRestriction =
            CircularBounds.newInstance(center, SEARCH_RADIUS_METERS)

        val request = SearchNearbyRequest.builder(restriction, placeFields)
            .setIncludedTypes(listOf("restaurant"))
            .setMaxResultCount(10)
            .setRankPreference(SearchNearbyRequest.RankPreference.POPULARITY)
            .build()

        val resp: SearchNearbyResponse = Tasks.await(client.searchNearby(request))
        val place = resp.places.firstOrNull()
        if (place == null || place.name.isNullOrBlank()) {
            return@withContext NearbyRestaurantResult(
                name = "No Nearby Restaurant Found within ${SEARCH_RADIUS_METERS.toInt()} m",
                bitmap = null
            )
        }

        val name = place.name!!
        val meta: PhotoMetadata? = place.photoMetadatas?.firstOrNull()

        val bitmap: Bitmap? = if (meta != null) {
            val photoReq = FetchPhotoRequest.builder(meta)
                .setMaxWidth(600)
                .setMaxHeight(600)
                .build()
            val photoResp: FetchPhotoResponse = Tasks.await(client.fetchPhoto(photoReq))
            photoResp.bitmap
        } else null

        NearbyRestaurantResult(name = name, bitmap = bitmap)
    } catch (e: Exception) {
        e.printStackTrace()
        NearbyRestaurantResult(
            name = "Error",
            bitmap = null,
            error = "API Search Failed: ${e.message}"
        )
    }
}
