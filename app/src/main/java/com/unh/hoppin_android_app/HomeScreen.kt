package com.unh.hoppin_android_app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
import com.unh.hoppin_android_app.viewmodels.RecommendationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.graphics.Color

val gradientColors = listOf(
    Color(0xFFFF930F),
    Color(0xFFFFF95B)
)

/**
 * Application categories loaded from the CategoriesRepository. Kept as a top-level val
 * so the HomeScreen UI can reference the canonical set used elsewhere in the app.
 */
val categories = CategoriesRepository.allCategories()

/**
 * Public entry point for the Home screen.
 *
 * This small wrapper exists so callers (NavHost) can pass navigation, username,
 * and the Places API key explicitly.
 *
 * @param navController Navigation controller used to route to other screens.
 * @param userName Display name used in the greeting.
 * @param placesApiKey API key string forwarded to recommendation loader.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    userName: String,
    placesApiKey: String
) {
    HomeScreenContent(
        navController = navController,
        userName = userName,
        placesApiKey = placesApiKey
    )
}

/**
 * The main Home screen content.
 *
 * Responsibilities:
 *  - Request and display device location
 *  - Show a top greeting row with profile and quick map action
 *  - Render category row and recommendations block
 *  - Display a floating chat action button
 *
 * Notes:
 *  - Location permission handling is simplified inside rememberLocationPermissionState().
 *  - Reverse geocoding runs off the main thread.
 *  - RecommendationViewModel is invoked when a device location is available.
 */
@Composable
private fun HomeScreenContent(
    navController: NavController,
    userName: String,
    placesApiKey: String
) {
    val context = LocalContext.current

    // FusedLocationProviderClient used to fetch device location.
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    val hasLocationPermission by rememberLocationPermissionState()

    // Local UI state
    var deviceLatLng by remember { mutableStateOf<LatLng?>(null) }   // device coordinates
    var streetCity by remember { mutableStateOf<String?>(null) }     // human-readable street/city
    var locationError by remember { mutableStateOf<String?>(null) }  // error text for debugging / display
    var loading by remember { mutableStateOf(true) }                 // whether location fetch is in progress

    val recoVm: RecommendationViewModel = viewModel()
    val recoUi by recoVm.ui.collectAsState()

    /**
     * When permission becomes available, try to fetch the current device location.
     * This block:
     *  - Attempts getCurrentLocation()
     *  - Falls back to lastLocation or a single accurate location fix if needed
     *  - Reverse-geocodes to a street/city string for display
     *  - On failure, we do NOT use any hardcoded fallback; we just show "Unavailable"/"Locating..."
     */
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
                    // try last known location, otherwise request a single high-accuracy update
                    fused.lastLocation.await()?.let { LatLng(it.latitude, it.longitude) }
                        ?: awaitOneLocationFix(fused)
                }
            }

            if (latLng == null) {
                // ❌ No GPS fix — don't force any default city
                locationError = "Fetching.."
                deviceLatLng = null
                streetCity = null
            } else {
                // ✅ Got a location: keep it and attempt reverse-geocoding
                deviceLatLng = latLng
                streetCity = reverseGeocodeStreetCity(context, latLng) ?: "Locating..."
                locationError = null
            }
        } catch (e: SecurityException) {
            // Permission missing or revoked while running
            locationError = "Location permission not granted"
            deviceLatLng = null
            streetCity = null
        } catch (e: Exception) {
            // Generic failure (network, geocoder, etc.)
            locationError = "Location error"
            deviceLatLng = null
            streetCity = null
        } finally {
            loading = false
        }
    }

    /**
     * When we have a deviceLatLng, trigger the RecommendationViewModel to load
     * recommendation tiles based on the categories we want (exclude emergency/services here).
     */
    LaunchedEffect(deviceLatLng) {
        val center = deviceLatLng ?: return@LaunchedEffect
        val recoCats = categories.filter { it.id !in setOf(7, 8) } // omit Emergency & Services
        recoVm.load(
            context = context,
            apiKey = placesApiKey,
            center = center,
            categories = recoCats
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.hoppinbackground),
            contentDescription = "Background image",
            contentScale = ContentScale.Crop,
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
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { navController.navigate("gamification") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(8.dp)
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
                    IconButton(
                        onClick = {
                            navController.navigate("map")
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Open Map",
                            tint = Color.Black
                        )
                    }

                    val parts = streetCity?.split(",", limit = 2)?.map { it.trim() }

                    Text(
                        text = when {
                            parts != null -> listOfNotNull(parts.getOrNull(0), parts.getOrNull(1))
                                .joinToString("\n")
                            loading && locationError == null -> "Fetching address…"
                            locationError != null -> "Unavailable"
                            else -> "Locating..."
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
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
            Spacer(modifier = Modifier.height(20.dp))

            BrowseCategoriesSection(navController = navController, categories = categories)
            Spacer(modifier = Modifier.height(20.dp))
            RecommendationsBlock(
                ui = recoUi,
                outerHorizontalPadding = 24.dp,
                onPlaceClick = { placeId ->
                    navController.navigate("place/$placeId")
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        ChatBubbleButton(
            onClick = { navController.navigate("chat") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        )

    }
}

/**
 * Small helper that tracks whether location permission is granted.
 *
 * Implementation details:
 *  - Uses ActivityResult launcher to request ACCESS_FINE/COARSE if needed.
 *  - Returns a derived [State<Boolean>] that updates when the result changes.
 *
 * Note: This is intentionally simple — production apps may want a more robust
 * permission UX (explainers, permanently denied flows, settings link, etc.).
 */
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
        else launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    return remember { derivedStateOf { granted } }
}

/**
 * Waits for a single high-accuracy location fix by registering a one-shot listener.
 *
 * This uses suspendCancellableCoroutine to bridge the callback-based Location API
 * with coroutines and ensures we clean up listeners on cancellation.
 *
 * @param fused FusedLocationProviderClient used to request updates.
 * @return The LatLng of the first location result, or null if none arrived.
 */
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

/**
 * Perform a reverse-geocode lookup (street + city) using Android's Geocoder on an IO dispatcher.
 *
 * If Geocoder isn't present or the lookup fails, this function returns null.
 *
 * @param context Application context for the Geocoder.
 * @param latLng Coordinates to reverse geocode.
 * @return A compact "street, city" string or null on failure.
 */
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

@Composable
fun ChatBubbleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(50),
        color = Color(0xFF2bb7c4).copy(alpha = 0.95f),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "Chatbot",
                    tint = Color(color = 0xFFF4b91D),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

