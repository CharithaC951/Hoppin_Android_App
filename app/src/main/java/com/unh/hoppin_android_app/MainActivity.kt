package com.unh.hoppin_android_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.unh.hoppin_android_app.chat.ChatScreen // <<< Make sure to import your ChatScreen
import com.unh.hoppin_android_app.ui.theme.Hoppin_Android_AppTheme
import kotlinx.coroutines.launch

const val USER_NAME_ARG = "userName"
const val HOME_ROUTE_PATTERN = "Home/{$USER_NAME_ARG}"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        var keepOn = true

        // --- CHANGE 1: This temporary setContent block has been removed ---
        // setContent {
        //     ChatScreen(onNavigateBack = {
        //         finish()
        //     })
        // }

        lifecycleScope.launch {
            kotlinx.coroutines.delay(250)
            keepOn = false
        }
        splash.setKeepOnScreenCondition { keepOn }

        // This is your main content block
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
                        // Your existing login route (unchanged)
                        composable("login") {
                            LoginScreen(navController = navController)
                        }

                        // Your existing home route (unchanged)
                        composable(
                            HOME_ROUTE_PATTERN,
                            arguments = listOf(
                                navArgument(USER_NAME_ARG) {
                                    type = NavType.StringType
                                    nullable = false
                                }
                            )) { backStackEntry ->
                            val userName = backStackEntry.arguments?.getString(USER_NAME_ARG) ?: "Guest"
                            HomeScreen(navController = navController, userName = userName)
                        }

                        // --- CHANGE 2: Add the new route for the Chat Screen ---
                        composable("chat") {
                            ChatScreen(onNavigateBack = {
                                navController.popBackStack() // Navigates back to the previous screen (HomeScreen)
                            })
                        }
                    }
                }
            }
        }
    }
}