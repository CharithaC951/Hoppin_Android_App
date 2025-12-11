package com.unh.hoppin_android_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.unh.hoppin_android_app.ui.theme.Hoppin_Android_AppTheme
import com.unh.hoppin_android_app.viewmodels.ChatViewModel
import kotlinx.coroutines.launch
import com.unh.hoppin_android_app.viewmodels.RecommendationViewModel

const val USER_NAME_ARG = "Hopper"
const val HOME_ROUTE_PATTERN = "Home/{$USER_NAME_ARG}"
const val GAMIFICATION_ROUTE_PATTERN = "gamification/{$USER_NAME_ARG}"

class MainActivity : ComponentActivity() {

    private val PLACES_API_KEY = "AIzaSyDiNZujsy2lzuMOmacqsm4yrcWg2RFqobw"
    private val MAPS_API_KEY = "AIzaSyBZ5BHvRW4P9pLWKwGQh_WiyfMOQKfLONA"

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        createNotificationChannel()

        if (!Places.isInitialized()) {
            Places.initialize(this, PLACES_API_KEY)
        }

        splash.setKeepOnScreenCondition { false }

        setContent {
            Hoppin_Android_AppTheme() {
                // Global background image for the entire app
                Box(modifier = Modifier.fillMaxSize()) {
                    if(isSystemInDarkTheme())
                    {
                        Image(
                            painter = painterResource(id = R.drawable.dark_bg),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else {
                        Image(
                            painter = painterResource(id = R.drawable.test_bg),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // All app content sits on top of the background
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        val navController = rememberNavController()
                        val currentBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = currentBackStackEntry?.destination?.route
                        val showBottomBar = currentRoute != "login"

                        val context = this@MainActivity
                        val activity = this@MainActivity

                        val recoVm: RecommendationViewModel = viewModel()
                        // Shared PlacesClient
                        val placesClient: PlacesClient = remember {
                            Places.createClient(context)
                        }

                        // Fused location client
                        val fused = remember {
                            LocationServices.getFusedLocationProviderClient(context)
                        }

                        // Visit tracker
                        val visitTracker = remember {
                            VisitTracker(
                                context = context,
                                fusedClient = fused
                            )
                        }

                        var deviceCenter by remember { mutableStateOf<LatLng?>(null) }

                        // One-time prefetch guard
                        var hasPrefetched by remember { mutableStateOf(false) }

                        fun maybePrefetchForCenter(center: LatLng) {
                            if (hasPrefetched) return
                            hasPrefetched = true
                            activity.lifecycleScope.launch {
                                try {
                                    prefetchNearbyForCenter(
                                        client = placesClient,
                                        center = center
                                    )
                                } catch (_: Exception) {
                                    // ignore prefetch errors
                                }
                            }
                        }

                        val permissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestMultiplePermissions()
                        ) { granted ->
                            val allowed =
                                granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                        granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                            if (allowed) {
                                visitTracker.start()

                                val token = CancellationTokenSource()
                                fused.getCurrentLocation(
                                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                    token.token
                                ).addOnSuccessListener { loc ->
                                    if (loc != null) {
                                        val center = LatLng(loc.latitude, loc.longitude)
                                        deviceCenter = center
                                        maybePrefetchForCenter(center)
                                    } else {
                                        fused.lastLocation.addOnSuccessListener { last ->
                                            if (last != null) {
                                                val center = LatLng(
                                                    last.latitude,
                                                    last.longitude
                                                )
                                                deviceCenter = center
                                                maybePrefetchForCenter(center)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        LaunchedEffect(Unit) {
                            val fine = ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                            val coarse = ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            val granted = fine == PackageManager.PERMISSION_GRANTED ||
                                    coarse == PackageManager.PERMISSION_GRANTED

                            if (granted) {
                                visitTracker.start()

                                val token = CancellationTokenSource()
                                fused.getCurrentLocation(
                                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                    token.token
                                ).addOnSuccessListener { loc ->
                                    if (loc != null) {
                                        val center = LatLng(loc.latitude, loc.longitude)
                                        deviceCenter = center
                                        maybePrefetchForCenter(center)
                                    } else {
                                        fused.lastLocation.addOnSuccessListener { last ->
                                            if (last != null) {
                                                val center = LatLng(
                                                    last.latitude,
                                                    last.longitude
                                                )
                                                deviceCenter = center
                                                maybePrefetchForCenter(center)
                                            }
                                        }
                                    }
                                }
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }

                        DisposableEffect(Unit) {
                            onDispose {
                                visitTracker.stop()
                            }
                        }

                        if (showBottomBar) {
                            Scaffold(
                                bottomBar = {
                                    AppBottomBar(
                                        currentRoute = currentRoute,
                                        onItemClick = { route ->
                                            navController.navigate(route) {
                                                popUpTo(HOME_ROUTE_PATTERN) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                },
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            ) { innerPadding ->
                                NavHost(
                                    navController = navController,
                                    startDestination = "login",
                                    modifier = Modifier.padding(innerPadding)
                                ) {
                                    composable("login") {
                                        LoginScreen(navController)
                                    }
                                    composable(
                                        HOME_ROUTE_PATTERN,
                                        arguments = listOf(
                                            navArgument(USER_NAME_ARG) {
                                                type = NavType.StringType
                                                nullable = false
                                            }
                                        )
                                    ) { backStackEntry ->
                                        val userName =
                                            backStackEntry.arguments?.getString(USER_NAME_ARG)
                                                ?: "Guest"
                                        HomeScreen(
                                            navController = navController,
                                            userName = userName,
                                            recoVm = recoVm,
                                            placesApiKey = PLACES_API_KEY
                                        )
                                    }
                                    composable("map") {
                                        MapScreen(
                                            mapsApiKey = MAPS_API_KEY,
                                            navController = navController
                                        )
                                    }
                                    composable(
                                        GAMIFICATION_ROUTE_PATTERN,
                                        arguments = listOf(
                                            navArgument(USER_NAME_ARG) {
                                                type = NavType.StringType
                                                nullable = false
                                            }
                                        )
                                    ) { backStackEntry ->
                                        val userName =
                                            backStackEntry.arguments?.getString(USER_NAME_ARG)
                                                ?: "Guest"
                                        GamificationScreen(
                                            navController = navController,
                                            userName = userName
                                        )
                                    }
                                    composable("chat") {
                                        val chatViewModel: ChatViewModel = viewModel()

                                        LaunchedEffect(currentRoute) {
                                            if (currentRoute == "chat") {
                                                chatViewModel.showTopLevelReplies()
                                            }
                                        }
                                        ChatScreen(
                                            navController = navController,
                                            chatViewModel = chatViewModel,
                                            onNavigateBack = { navController.popBackStack() }
                                        )
                                    }
                                    composable(
                                        route = "sub/{catId}",
                                        arguments = listOf(
                                            navArgument("catId") { type = NavType.IntType }
                                        )
                                    ) { backStackEntry ->
                                        val catId = backStackEntry.arguments!!.getInt("catId")
                                        SubCategoriesScreen(
                                            navController = navController,
                                            catId = catId
                                        )
                                    }
                                    composable(
                                        route = "discover?type={type}&categoryId={categoryId}",
                                        arguments = listOf(
                                            navArgument("type") {
                                                type = NavType.StringType
                                                defaultValue = ""
                                            },
                                            navArgument("categoryId") {
                                                type = NavType.IntType
                                                defaultValue = -1
                                            }
                                        )
                                    ) { backStackEntry ->
                                        val typeArg =
                                            backStackEntry.arguments?.getString("type").orEmpty()
                                        val categoryId =
                                            backStackEntry.arguments?.getInt("categoryId") ?: -1
                                        DiscoverListScreen(
                                            selectedTypes = typeArg.split(',')
                                                .filter { it.isNotBlank() },
                                            selectedCategoryId = categoryId.takeIf { it != -1 },
                                            center = deviceCenter,
                                            placesClient = placesClient,
                                            onBack = { navController.popBackStack() },
                                            onPlaceClick = { uiPlace ->
                                                navController.navigate("place/${uiPlace.id}")
                                            },
                                            recoVm = recoVm,
                                            onOpenFavorites = {
                                                navController.navigate("favorites")
                                            }
                                        )
                                    }
                                    composable(
                                        route = "place/{placeId}",
                                        arguments = listOf(
                                            navArgument("placeId") {
                                                type = NavType.StringType
                                            }
                                        )
                                    ) { backStackEntry ->
                                        val placeId =
                                            backStackEntry.arguments!!.getString("placeId")!!
                                        PlaceDetailsScreen(
                                            placeId = placeId,
                                            onBack = { navController.popBackStack() },
                                            onOpenTripCard = { placeName ->
                                                val encodedName = Uri.encode(placeName)
                                                navController.navigate("tripcard/$encodedName")
                                            }
                                        )
                                    }
                                    composable(
                                        route = "tripCard/{placeName}",
                                        arguments = listOf(
                                            navArgument("placeName") { type = NavType.StringType }
                                        )
                                    ) { backStackEntry ->
                                        val placeNameArg =
                                            backStackEntry.arguments?.getString("placeName")
                                                ?: ""
                                        TripCardScreen(
                                            placeName = placeNameArg,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                    composable("feed") {
                                        FeedScreen(
                                            onBack = { navController.popBackStack() },
                                            onOpenItinerary = { itineraryId ->
                                                navController.navigate("itinerary/$itineraryId")
                                            }
                                        )
                                    }

                                    composable("favorites") {
                                        FavoritesScreen(
                                            onBack = { navController.popBackStack() },
                                            onPlaceClick = { uiPlace ->
                                                navController.navigate("place/${uiPlace.id}")
                                            }
                                        )
                                    }
                                    composable("notifications") {
                                        NotificationsScreen(navController = navController)
                                    }
                                    composable("settings") {
                                        SettingsScreen(
                                            navController = navController,
                                            onBack = { navController.popBackStack() },
                                            onOpenTrips = { navController.navigate("itineraries") }
                                        )
                                    }

                                    composable("itineraries") {
                                        TripItinerariesScreen(
                                            onBack = { navController.popBackStack() },
                                            onOpenItinerary = { itineraryId ->
                                                navController.navigate("itinerary/$itineraryId")
                                            }
                                        )
                                    }

                                    composable(
                                        "itinerary/{itineraryId}",
                                        arguments = listOf(
                                            navArgument("itineraryId") { type = NavType.StringType }
                                        )
                                    ) { backStackEntry ->
                                        val itineraryId =
                                            backStackEntry.arguments?.getString("itineraryId")
                                                ?: ""
                                        ItineraryDetailScreen(
                                            itineraryId = itineraryId,
                                            onBack = { navController.popBackStack() },
                                            onPlaceClick = { placeId ->
                                                navController.navigate("place/$placeId")
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            // No bottom bar (e.g. login)
                            NavHost(
                                navController = navController,
                                startDestination = "login"
                            ) {
                                composable("login") {
                                    LoginScreen(navController)
                                }
                                composable(
                                    HOME_ROUTE_PATTERN,
                                    arguments = listOf(
                                        navArgument(USER_NAME_ARG) {
                                            type = NavType.StringType
                                            nullable = false
                                        }
                                    )
                                ) { backStackEntry ->
                                    val userName =
                                        backStackEntry.arguments?.getString(USER_NAME_ARG)
                                            ?: "Guest"
                                    HomeScreen(
                                        navController = navController,
                                        userName = userName,
                                        placesApiKey = PLACES_API_KEY,
                                        recoVm = recoVm
                                    )
                                }
                                composable("map") {
                                    MapScreen(
                                        mapsApiKey = MAPS_API_KEY,
                                        navController = navController
                                    )
                                }
                                composable(
                                    GAMIFICATION_ROUTE_PATTERN,
                                    arguments = listOf(
                                        navArgument(USER_NAME_ARG) {
                                            type = NavType.StringType
                                            nullable = false
                                        }
                                    )
                                ) { backStackEntry ->
                                    val userName =
                                        backStackEntry.arguments?.getString(USER_NAME_ARG)
                                            ?: "Guest"
                                    GamificationScreen(
                                        navController = navController,
                                        userName = userName
                                    )
                                }
                                composable("chat") {
                                    val chatViewModel: ChatViewModel = viewModel()

                                    LaunchedEffect(currentRoute) {
                                        if (currentRoute == "chat") {
                                            chatViewModel.showTopLevelReplies()
                                        }
                                    }
                                    ChatScreen(
                                        navController = navController,
                                        chatViewModel = chatViewModel,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                                composable(
                                    route = "sub/{catId}",
                                    arguments = listOf(
                                        navArgument("catId") { type = NavType.IntType }
                                    )
                                ) { backStackEntry ->
                                    val catId = backStackEntry.arguments!!.getInt("catId")
                                    SubCategoriesScreen(
                                        navController = navController,
                                        catId = catId
                                    )
                                }
                                composable(
                                    route = "discover?type={type}&categoryId={categoryId}",
                                    arguments = listOf(
                                        navArgument("type") {
                                            type = NavType.StringType
                                            defaultValue = ""
                                        },
                                        navArgument("categoryId") {
                                            type = NavType.IntType
                                            defaultValue = -1
                                        }
                                    )
                                ) { backStackEntry ->
                                    val typeArg =
                                        backStackEntry.arguments?.getString("type").orEmpty()
                                    val categoryId =
                                        backStackEntry.arguments?.getInt("categoryId") ?: -1
                                    DiscoverListScreen(
                                        selectedTypes = typeArg.split(',')
                                            .filter { it.isNotBlank() },
                                        selectedCategoryId = categoryId.takeIf { it != -1 },
                                        center = deviceCenter,
                                        placesClient = placesClient,
                                        recoVm = recoVm,   // 🔹 pass shared VM
                                        onBack = { navController.popBackStack() },
                                        onPlaceClick = { uiPlace ->
                                            navController.navigate("place/${uiPlace.id}")
                                        },
                                        onOpenFavorites = {
                                            navController.navigate("favorites")
                                        }
                                    )
                                }
                                composable(
                                    route = "place/{placeId}",
                                    arguments = listOf(
                                        navArgument("placeId") {
                                            type = NavType.StringType
                                        }
                                    )
                                ) { backStackEntry ->
                                    val placeId =
                                        backStackEntry.arguments!!.getString("placeId")!!
                                    PlaceDetailsScreen(
                                        placeId = placeId,
                                        onBack = { navController.popBackStack() },
                                        onOpenTripCard = { placeName ->
                                            val encodedName = Uri.encode(placeName)
                                            navController.navigate("tripcard/$encodedName")
                                        }
                                    )
                                }
                                composable(
                                    route = "tripCard/{placeName}",
                                    arguments = listOf(
                                        navArgument("placeName") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val placeNameArg =
                                        backStackEntry.arguments?.getString("placeName") ?: ""
                                    TripCardScreen(
                                        placeName = placeNameArg,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable("feed") {
                                    FeedScreen(
                                        onBack = { navController.popBackStack() },
                                        onOpenItinerary = { itineraryId ->
                                            navController.navigate("itinerary/$itineraryId")
                                        }
                                    )
                                }

                                composable("favorites") {
                                    FavoritesScreen(
                                        onBack = { navController.popBackStack() },
                                        onPlaceClick = { uiPlace ->
                                            navController.navigate("place/${uiPlace.id}")
                                        }
                                    )
                                }
                                composable("notifications") {
                                    NotificationsScreen(navController = navController)
                                }
                                composable("settings") {
                                    SettingsScreen(
                                        navController = navController,
                                        onBack = { navController.popBackStack() },
                                        onOpenTrips = { navController.navigate("itineraries") }
                                    )
                                }

                                composable("itineraries") {
                                    TripItinerariesScreen(
                                        onBack = { navController.popBackStack() },
                                        onOpenItinerary = { itineraryId ->
                                            navController.navigate("itinerary/$itineraryId")
                                        }
                                    )
                                }
                                composable(
                                    "itinerary/{itineraryId}",
                                    arguments = listOf(
                                        navArgument("itineraryId") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val itineraryId =
                                        backStackEntry.arguments?.getString("itineraryId")
                                            ?: ""
                                    ItineraryDetailScreen(
                                        itineraryId = itineraryId,
                                        onBack = { navController.popBackStack() },
                                        onPlaceClick = { placeId ->
                                            navController.navigate("place/$placeId")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // No-op prefetch to satisfy reference; safe to extend later if you want real prefetching.
    private fun prefetchNearbyForCenter(client: PlacesClient, center: LatLng) {
        // Intentionally left blank
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Hoppin App Notifications"
            val descriptionText = "Channel for general app notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("HOPPIN_CHANNEL_ID", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
