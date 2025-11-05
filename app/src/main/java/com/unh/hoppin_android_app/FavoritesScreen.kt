package com.unh.hoppin_android_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.unh.hoppin_android_app.UiPlace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onPlaceClick: (UiPlace) -> Unit,
    center: LatLng = LatLng(41.3083, -72.9279) // not strictly needed here
) {
    val favs by FavoritesStore.items.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favourites") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        val list = favs.values.toList()
        when {
            list.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center
            ) { Text("No favourites yet") }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(list, key = { it.id }) { p ->
                    FavoriteCard(
                        place = p,
                        onClick = { onPlaceClick(p) },
                        onRemove = { FavoritesStore.remove(p.id) }
                    )
                }
            }
        }
    }
}

/* Same card layout as Discover, but heart is replaced with a Delete icon */
@Composable
private fun FavoriteCard(
    place: UiPlace,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(4.dp),
        onClick = onClick
    ) {
        Column {
            if (place.photo != null) {
                Image(
                    bitmap = place.photo.asImageBitmap(),
                    contentDescription = place.title,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFFEAEAEA))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        place.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    place.distanceMeters?.let {
                        Text(
                            if (it >= 1000) String.format("%.1f km away", it / 1000.0)
                            else "$it m away",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                // 🔁 Replaced heart with Delete icon
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.Red)
                }
            }
        }
    }
}
