package com.unh.hoppin_android_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

@Composable
fun SplashScreen(logoRes: Int, appName: String, holdMillis: Long, onTimeout: () -> Unit) {

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(holdMillis)
        onTimeout()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(id = logoRes), contentDescription = "App Logo")
            Text(appName, style = MaterialTheme.typography.headlineMedium)
        }
    }
}