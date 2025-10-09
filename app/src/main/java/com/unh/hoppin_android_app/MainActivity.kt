package com.unh.hoppin_android_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
                        composable(HOME_ROUTE_PATTERN,
                            arguments = listOf(
                                navArgument(USER_NAME_ARG) {
                                    type = NavType.StringType // Specify the expected type
                                    nullable = false
                                }
                            )) { backStackEntry ->
                            val userName = backStackEntry.arguments?.getString(USER_NAME_ARG)?:"Guest"

                            // Pass the name to your HomeScreen composable
                            HomeScreen(navController = navController,userName = userName)
                        }
                    }
                }
            }
        }
    }
}