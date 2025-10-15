package com.unh.hoppin_android_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.unh.hoppin_android_app.ui.theme.Hoppin_Android_AppTheme
import kotlinx.coroutines.launch

const val USER_NAME_ARG = "userName"
const val HOME_ROUTE_PATTERN = "Home/{$USER_NAME_ARG}"

class MainActivity : ComponentActivity() {

    // Hardcode your Places API key here for now
    private val PLACES_API_KEY = "AIzaSyDiNZujsy2lzuMOmacqsm4yrcWg2RFqobw"

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
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

                    NavHost(
                        navController = navController,
                        startDestination = "login"
                    ) {
                        composable("login") {
                            LoginScreen(navController = navController)
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
                            val userName = backStackEntry.arguments?.getString(USER_NAME_ARG) ?: "Guest"
                            HomeScreen(
                                navController = navController,
                                userName = userName,
                                placesApiKey = PLACES_API_KEY
                            )
                        }
                    }
                }
            }
        }
    }
}
