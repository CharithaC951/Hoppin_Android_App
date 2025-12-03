package com.unh.hoppin_android_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/* ----------------------------- Gradient & Categories ----------------------------- */

val gradientColors = listOf(
    Color(0xFFFF930F),
    Color(0xFFFFF95B)
)

/**
 * Application categories loaded from the CategoriesRepository. Kept as a top-level val
 * so the HomeScreen UI can reference the canonical set used elsewhere in the app.
 */
val categories = CategoriesRepository.allCategories()

/* ----------------------------- Home location cache ----------------------------- */

private const val HOME_LOCATION_CACHE_MAX_AGE_MS = 5 * 60 * 1000L // 5 minutes

private object HomeLocationCache {
    var lastLatLng: LatLng? = null
    var lastAddress: String? = null
    var lastTimestamp: Long = 0L

    fun isFresh(): Boolean {
        val latLng = lastLatLng ?: return false
        val age = System.currentTimeMillis() - lastTimestamp
        return age in 0..HOME_LOCATION_CACHE_MAX_AGE_MS
    }

    fun update(latLng: LatLng, address: String?) {
        lastLatLng = latLng
        lastAddress = address
        lastTimestamp = System.currentTimeMillis()
    }
}

/* ----------------------------- User name cache ----------------------------- */

private object UserNameCache {
    var cached: String? = null
}

/* ----------------------------- Public Home entry ----------------------------- */

/**
 * Public entry point for the Home screen.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    userName: String,
    placesApiKey: String,
    recoVm: RecommendationViewModel
) {
    HomeScreenContent(
        navController = navController,
        userName = userName,
        placesApiKey = placesApiKey,
        recoVm = recoVm
    )
}

/* ----------------------------- Home content ----------------------------- */

@Composable
private fun HomeScreenContent(
    navController: NavController,
    userName: String,
    placesApiKey: String,
    recoVm: RecommendationViewModel
) {
    val context = LocalContext.current

    // Cache the display name the first time we see it (so it never changes mid-session)
    val displayName by remember {
        mutableStateOf(
            UserNameCache.cached
                ?: userName.also { if (it.isNotBlank()) UserNameCache.cached = it }
        )
    }

    // FusedLocationProviderClient used to fetch device location.
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    val hasLocationPermission by rememberLocationPermissionState()

    // Local UI state
    var deviceLatLng by remember { mutableStateOf<LatLng?>(null) }   // device coordinates
    var streetCity by remember { mutableStateOf<String?>(null) }     // human-readable street/city
    var locationError by remember { mutableStateOf<String?>(null) }  // error text
    var loading by remember { mutableStateOf(true) }                 // location fetch in progress

    val recoUi by recoVm.ui.collectAsState()

    /* ---- Immediately use cached location/address if fresh ---- */
    LaunchedEffect(Unit) {
        if (HomeLocationCache.isFresh()) {
            deviceLatLng = HomeLocationCache.lastLatLng
            streetCity = HomeLocationCache.lastAddress
            locationError = null
            loading = false
        }
    }

    /**
     * When permission becomes available, fetch location.
     * Strategy:
     *  1) If cache is fresh -> reuse, no GPS call.
     *  2) Else:
     *     a) Try fused.lastLocation (fast).
     *     b) Use it immediately if present (update UI + cache).
     *     c) If lastLocation is null, fall back to slower path
     *        (getCurrentLocation / one-shot fix).
     */
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect

        // Fast path: still-fresh cache
        if (HomeLocationCache.isFresh()) {
            deviceLatLng = HomeLocationCache.lastLatLng
            streetCity = HomeLocationCache.lastAddress
            locationError = null
            loading = false
            return@LaunchedEffect
        }

        loading = true
        try {
            // 1) Try last known location first (very fast)
            val last = fused.lastLocation.await()
            if (last != null) {
                val latLng = LatLng(last.latitude, last.longitude)
                deviceLatLng = latLng

                val addr = reverseGeocodeStreetCity(context, latLng) ?: "Locating..."
                streetCity = addr
                locationError = null
                HomeLocationCache.update(latLng, addr)
                loading = false
            } else {
                // 2) If lastLocation was null, use slower but robust path
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
                    if (HomeLocationCache.lastLatLng != null) {
                        deviceLatLng = HomeLocationCache.lastLatLng
                        streetCity = HomeLocationCache.lastAddress
                        locationError = null
                    } else {
                        locationError = "Fetching.."
                        deviceLatLng = null
                        streetCity = null
                    }
                } else {
                    deviceLatLng = latLng
                    val addr = reverseGeocodeStreetCity(context, latLng) ?: "Locating..."
                    streetCity = addr
                    locationError = null
                    HomeLocationCache.update(latLng, addr)
                }
                loading = false
            }
        } catch (e: SecurityException) {
            if (HomeLocationCache.lastLatLng != null) {
                deviceLatLng = HomeLocationCache.lastLatLng
                streetCity = HomeLocationCache.lastAddress
                locationError = null
            } else {
                locationError = "Location permission not granted"
                deviceLatLng = null
                streetCity = null
            }
            loading = false
        } catch (e: Exception) {
            if (HomeLocationCache.lastLatLng != null) {
                deviceLatLng = HomeLocationCache.lastLatLng
                streetCity = HomeLocationCache.lastAddress
                locationError = null
            } else {
                locationError = "Location error"
                deviceLatLng = null
                streetCity = null
            }
            loading = false
        }
    }

    /**
     * When we have a deviceLatLng, trigger the RecommendationViewModel to load
     * recommendation tiles based on the categories we want (exclude emergency/services here).
     *
     * RecommendationViewModel itself has caching, so this is cheap after first load.
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

    /* ----------------------------- UI ----------------------------- */

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
                            text = "Hello $displayName",
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
                            locationError != null && streetCity == null -> "Unavailable"
                            streetCity != null -> streetCity!!
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
        FloatingActionButton(
            onClick = {
                navController.navigate("chat")
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF6B4D9C)
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = "Open Chatbot",
                tint = Color.White
            )
        }
    }
}

/* ----------------------------- Permission helper ----------------------------- */

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

/* ----------------------------- One-shot location helper ----------------------------- */

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

/* ----------------------------- Reverse geocode helper ----------------------------- */

private suspend fun reverseGeocodeStreetCity(
    context: Context,
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
