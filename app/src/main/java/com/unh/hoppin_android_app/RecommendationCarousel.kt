// ui/home/SingleRecommendationsCarousel.kt
package com.unh.hoppin_android_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unh.hoppin_android_app.viewmodels.RecommendationsUiState
import kotlin.math.roundToInt

@Composable
fun RecommendationsBlock(
    ui: RecommendationsUiState,
    outerHorizontalPadding: Dp = 24.dp // match your screen padding for flush edges
) {
    Text(
        text = "Recommendations",
        style = MaterialTheme.typography.headlineSmall,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    when {
        ui.loading -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return
        }
        ui.error != null -> {
            Text(text = ui.error, color = MaterialTheme.colorScheme.error)
            return
        }
        ui.flatItems.isEmpty() -> {
            Text("No recommendations found.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            return
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = screenWidthDp - (outerHorizontalPadding * 2) // full-bleed inside padding

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(ui.flatItems) { item ->
            FullWidthRecommendationCard(
                title = item.title,
                categoryTitle = item.categoryTitle, // optional to show; see below
                distanceMeters = item.distanceMeters,
                bitmap = item.bitmap,
                width = cardWidth
            )
        }
    }
}

@Composable
private fun FullWidthRecommendationCard(
    title: String,
    categoryTitle: String,
    distanceMeters: Double,
    bitmap: android.graphics.Bitmap?,
    width: Dp,
    imageHeight: Dp = 260.dp // increased height
) {
    Card(
        modifier = Modifier.width(width),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        // Title (top). If you also want category as a small label, add it above/beside title.
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Optional category label (small)
            if (categoryTitle.isNotBlank()) {
                Text(
                    text = categoryTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Image underneath + distance chip top-right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No photo", style = MaterialTheme.typography.bodyMedium)
                }
            }

            DistanceChip(
                meters = distanceMeters,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DistanceChip(meters: Double, modifier: Modifier = Modifier) {
    val (value, unit, text) = if (meters >= 1000) {
        val km = (meters / 100.0).roundToInt() / 10.0 // 1 decimal
        Triple(km, "km", "$km km")
    } else {
        val m = meters.roundToInt()
        Triple(m.toDouble(), "m", "$m m")
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 2.dp,
        tonalElevation = 2.dp,
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
