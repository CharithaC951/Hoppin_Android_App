package com.unh.hoppin_android_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.unh.hoppin_android_app.ui.theme.Hoppin_Android_AppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            Hoppin_Android_AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LocationTextScreen()
                    //MapScreen()
                    /*
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "Splash"
                    ) {
                        composable("Splash") {
                            SplashScreen(
                                logoRes = R.drawable.hoppin_logo, // Make sure ic_launcher is suitable or use a different logo
                                appName = "Hoppin",
                                holdMillis = 1000L
                            ) {
                                navController.navigate("login") {
                                    popUpTo("Splash") { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }

                        composable("login") {
                            // Pass the navController if LoginScreen needs to navigate (e.g., after successful login)
                            LoginScreen(navController = navController)
                        }
                        composable("Home") {
                            // Pass the navController so HomeScreen can navigate to other places
                            HomeScreen(navController = navController)
                        }
                    }
                    */
                }
            }
        }
    }
}