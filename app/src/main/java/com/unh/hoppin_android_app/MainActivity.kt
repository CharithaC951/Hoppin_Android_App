package com.unh.hoppin_android_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.libraries.places.api.Places
import com.unh.hoppin_android_app.ui.DiscoverListScreen
import com.unh.hoppin_android_app.ui.theme.Hoppin_Android_AppTheme
import kotlinx.coroutines.launch


const val USER_NAME_ARG = "Hopper"
const val HOME_ROUTE_PATTERN = "Home/{$USER_NAME_ARG}"

class MainActivity : ComponentActivity() {

    private val PLACES_API_KEY = "AIzaSyDiNZujsy2lzuMOmacqsm4yrcWg2RFqobw"

    private val MAPS_API_KEY = "AIzaSyBZ5BHvRW4P9pLWKwGQh_WiyfMOQKfLONA"

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
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
                    if (showBottomBar) {
                        Scaffold(
                            bottomBar = {
                                AppBottomBar(
                                    currentRoute = currentRoute,
                                    onItemClick = { route ->
                                        navController.navigate(route) {
                                            // Avoid duplicate copies in the stack
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
                                        onBack = { navController.popBackStack() }
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
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
