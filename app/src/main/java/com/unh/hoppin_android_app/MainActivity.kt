package com.unh.hoppin_android_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
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
import com.unh.hoppin_android_app.ui.theme.Hoppin_Android_AppTheme
import kotlinx.coroutines.launch
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val USER_NAME_ARG = "Hopper"
const val HOME_ROUTE_PATTERN = "Home/{$USER_NAME_ARG}"

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
        var keepOn = true
        lifecycleScope.launch {
            kotlinx.coroutines.delay(250)
            keepOn = false
        }
        splash.setKeepOnScreenCondition { keepOn }

        setContent {
            Hoppin_Android_AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route
                    val showBottomBar = currentRoute != "login"

                    // ---- Location plumbing to pass to DiscoverListScreen ----
                    val context = this@MainActivity
                    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
                    var deviceCenter by remember { mutableStateOf<LatLng?>(null) }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { granted ->
                        val allowed = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                        if (allowed) {
                            val token = CancellationTokenSource()
                            fused.getCurrentLocation(
                                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                token.token
                            ).addOnSuccessListener { loc ->
                                if (loc != null) {
                                    deviceCenter = LatLng(loc.latitude, loc.longitude)
                                } else {
                                    fused.lastLocation.addOnSuccessListener { last ->
                                        if (last != null) {
                                            deviceCenter = LatLng(last.latitude, last.longitude)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LaunchedEffect(Unit) {
                        val fine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        val coarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        val granted = fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val token = CancellationTokenSource()
                            fused.getCurrentLocation(
                                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                token.token
                            ).addOnSuccessListener { loc ->
                                if (loc != null) {
                                    deviceCenter = LatLng(loc.latitude, loc.longitude)
                                } else {
                                    fused.lastLocation.addOnSuccessListener { last ->
                                        if (last != null) {
                                            deviceCenter = LatLng(last.latitude, last.longitude)
                                        }
                                    }
                                }
                            }
                        } else {
                            permissionLauncher.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        }
                    }
                    // ---------------------------------------------------------

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
                            }
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
                                    arguments = listOf(navArgument(USER_NAME_ARG) {
                                        type = NavType.StringType
                                        nullable = false
                                    })
                                ) { backStackEntry ->
                                    val userName = backStackEntry.arguments?.getString(USER_NAME_ARG) ?: "Guest"
                                    HomeScreen(
                                        navController = navController,
                                        userName = userName,
                                        placesApiKey = PLACES_API_KEY
                                    )
                                }
                                composable("map") {
                                    MapScreen(
                                        mapsApiKey = MAPS_API_KEY,
                                        navController = navController
                                    )
                                }
                                composable("gamification") {
                                    GamificationScreen(navController = navController)
                                }
                                composable("chat") {
                                    ChatScreen(onNavigateBack = { navController.popBackStack() })
                                }
                                composable(
                                    route = "sub/{catId}",
                                    arguments = listOf(navArgument("catId") { type = NavType.IntType })
                                ) { backStackEntry ->
                                    val catId = backStackEntry.arguments!!.getInt("catId")
                                    SubCategoriesScreen(navController = navController, catId = catId)
                                }
                                composable(
                                    route = "discover?type={type}&categoryId={categoryId}",
                                    arguments = listOf(
                                        navArgument("type") { type = NavType.StringType; defaultValue = "" },
                                        navArgument("categoryId") { type = NavType.IntType; defaultValue = -1 }
                                    )
                                ) { backStackEntry ->
                                    val typeArg = backStackEntry.arguments?.getString("type").orEmpty()
                                    val categoryId = backStackEntry.arguments?.getInt("categoryId") ?: -1
                                    DiscoverListScreen(
                                        selectedTypes = typeArg.split(',').filter { it.isNotBlank() },
                                        selectedCategoryId = categoryId.takeIf { it != -1 },
                                        center = deviceCenter, // ✅ pass device location when available
                                        onBack = { navController.popBackStack() },
                                        onPlaceClick = { uiPlace ->
                                            navController.navigate("place/${uiPlace.id}")
                                        },
                                        onOpenFavorites = { navController.navigate("favorites") }
                                    )
                                }
                                composable(
                                    route = "place/{placeId}",
                                    arguments = listOf(navArgument("placeId") { type = NavType.StringType })
                                ) { backStackEntry ->
                                    val placeId = backStackEntry.arguments!!.getString("placeId")!!
                                    PlaceDetailsScreen(
                                        placeId = placeId,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                // Favourites page
                                composable("favorites") {
                                    FavoritesScreen(
                                        onBack = { navController.popBackStack() },
                                        onPlaceClick = { uiPlace ->
                                            navController.navigate("place/${uiPlace.id}")
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = "login"
                        ) {
                            composable("login") {
                                LoginScreen(navController)
                            }
                            composable(
                                HOME_ROUTE_PATTERN,
                                arguments = listOf(navArgument(USER_NAME_ARG) {
                                    type = NavType.StringType
                                    nullable = false
                                })
                            ) { backStackEntry ->
                                val userName = backStackEntry.arguments?.getString(USER_NAME_ARG) ?: "Guest"
                                HomeScreen(
                                    navController = navController,
                                    userName = userName,
                                    placesApiKey = PLACES_API_KEY
                                )
                            }
                            composable("map") {
                                MapScreen(
                                    mapsApiKey = MAPS_API_KEY,
                                    navController = navController
                                )
                            }
                            composable("gamification") {
                                GamificationScreen(navController = navController)
                            }
                            composable("chat") {
                                ChatScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable(
                                route = "sub/{catId}",
                                arguments = listOf(navArgument("catId") { type = NavType.IntType })
                            ) { backStackEntry ->
                                val catId = backStackEntry.arguments!!.getInt("catId")
                                SubCategoriesScreen(navController = navController, catId = catId)
                            }
                            composable(
                                route = "discover?type={type}&categoryId={categoryId}",
                                arguments = listOf(
                                    navArgument("type") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("categoryId") { type = NavType.IntType; defaultValue = -1 }
                                )
                            ) { backStackEntry ->
                                val typeArg = backStackEntry.arguments?.getString("type").orEmpty()
                                val categoryId = backStackEntry.arguments?.getInt("categoryId") ?: -1
                                DiscoverListScreen(
                                    selectedTypes = typeArg.split(',').filter { it.isNotBlank() },
                                    selectedCategoryId = categoryId.takeIf { it != -1 },
                                    center = deviceCenter, // ✅ pass device location here as well
                                    onBack = { navController.popBackStack() },
                                    onPlaceClick = { uiPlace ->
                                        navController.navigate("place/${uiPlace.id}")
                                    },
                                    onOpenFavorites = { navController.navigate("favorites") }
                                )
                            }
                            composable(
                                route = "place/{placeId}",
                                arguments = listOf(navArgument("placeId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val placeId = backStackEntry.arguments!!.getString("placeId")!!
                                PlaceDetailsScreen(
                                    placeId = placeId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            // Favourites page
                            composable("favorites") {
                                FavoritesScreen(
                                    onBack = { navController.popBackStack() },
                                    onPlaceClick = { uiPlace ->
                                        navController.navigate("place/${uiPlace.id}")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
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
