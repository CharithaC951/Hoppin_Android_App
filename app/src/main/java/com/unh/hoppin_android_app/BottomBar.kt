package com.unh.hoppin_android_app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.RemoveRedEye
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

/**
 * A reusable bottom navigation bar for the app.
 *
 * This composable draws a simple, full-width bottom navigation bar
 * with icons for Home, Favorites, Alerts, and Settings.
 *
 * It highlights the currently selected item and invokes a callback
 * whenever a new item is tapped.
 *
 * @param currentRoute The route name of the currently active screen.
 * @param onItemClick A lambda function triggered when a navigation item is clicked.
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onItemClick: (String) -> Unit
) {
    val items = listOf(
        BottomItem(
            route = "Home/{$USER_NAME_ARG}",
            label = "Home",
            icon = Icons.Outlined.Home,
            selectedIcon = Icons.Filled.Home
        ),
        BottomItem(
            route = "favorites",
            label = "Favorites",
            icon = Icons.Outlined.FavoriteBorder,
            selectedIcon = Icons.Filled.FavoriteBorder
        ),
        BottomItem(
            route = "feed",
            label = "Feed",
            icon = Icons.Outlined.DynamicFeed,
            selectedIcon = Icons.Filled.DynamicFeed
        ),
        BottomItem(
            route = "notifications",
            label = "Notifications",
            icon = Icons.Outlined.Notifications,
            selectedIcon = Icons.Filled.Notifications
        ),
        BottomItem(
            route = "settings",
            label = "Settings",
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Filled.Settings
        ),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 6.dp
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.White,
            tonalElevation = 0.dp
        ) {
            // Loop through all navigation items
            items.forEach { item ->
                val selected = currentRoute == item.route

                NavigationBarItem(
                    selected = selected,
                    onClick = { onItemClick(item.route) },
                    icon = {
                        // Change icon color depending on whether it's selected or not
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

/**
 * Data class representing each bottom navigation item.
 *
 * @property route The navigation route for the destination screen.
 * @property label The readable label for the item.
 * @property icon The default (unselected) icon.
 * @property selectedIcon The icon to show when the item is selected.
 */
private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)
