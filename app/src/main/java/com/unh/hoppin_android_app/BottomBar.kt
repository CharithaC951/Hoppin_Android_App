package com.unh.hoppin_android_app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun AppBottomBar(
    currentRoute: String?,
    onItemClick: (String) -> Unit
) {
    val items = listOf(
        BottomItem("Home/{$USER_NAME_ARG}", "Home", Icons.Outlined.Home, Icons.Filled.Home),
        BottomItem("favorites", "Favorites", Icons.Outlined.FavoriteBorder, Icons.Filled.FavoriteBorder),
        BottomItem("alerts", "Alerts", Icons.Outlined.Notifications, Icons.Filled.Notifications),
        BottomItem("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
    )

    // Full-width bottom bar (no rounded background, no outer padding)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,      // background of the bar
        shadowElevation = 6.dp    // optional small shadow
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.White,
            tonalElevation = 0.dp
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onItemClick(item.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = item.label,
                            tint = if (selected) Color(0xFF111111) else Color(0xFF9E9E9E)
                        )
                    },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = Color(0xFF111111),
                        unselectedIconColor = Color.Black
                    )
                )
            }
        }
    }
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)
