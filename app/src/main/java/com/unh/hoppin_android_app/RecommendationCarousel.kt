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

/**
 * RecommendationsBlock
 *
 * High-level composable that renders the "Recommendations" section used on the Home screen.
 *
 * Behavior:
 *  - Shows a header ("Recommendations")
 *  - Handles loading / error / empty states
 *  - Renders a horizontally scrolling list of recommendation cards where each card is sized
 *    to be full-width relative to the screen (minus outer padding), producing a "peek" style
 *    carousel when used inside a LazyRow.
 *
 * @param ui The UI state object produced by the RecommendationViewModel.
 * @param outerHorizontalPadding Padding applied around the list in the parent layout; used
 *        to compute an effective card width so cards appear full-bleed inside that padding.
 */
@Composable
fun RecommendationsBlock(
    ui: RecommendationsUiState,
    outerHorizontalPadding: Dp = 24.dp
) {
    Text(
        text = "Recommendations",
        style = MaterialTheme.typography.headlineSmall,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    // Simple state handling: loading, error, empty
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
            Text(
                "No recommendations found.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            return
        }
    }

    // Compute card width to make a "full-width card" inside the parent's horizontal padding
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = screenWidthDp - (outerHorizontalPadding * 2)

    // Horizontal list of recommendation cards. Each item uses the computed width.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(ui.flatItems) { item ->
            FullWidthRecommendationCard(
                title = item.title,
                categoryTitle = item.categoryTitle,
                distanceMeters = item.distanceMeters,
                bitmap = item.bitmap,
                width = cardWidth
            )
        }
    }
}

/**
 * FullWidthRecommendationCard
 *
 * A single recommendation card designed to occupy a full content width (provided via [width]).
 * The card shows:
 *  - optional category label (small, colored)
 *  - title (two lines max)
 *  - large image area (or a "No photo" placeholder)
 *  - a distance chip in the top-right of the image
 *
 * @param title The place/title to display.
 * @param categoryTitle Small category label shown above the title (optional).
 * @param distanceMeters Distance in meters used to populate the DistanceChip.
 * @param bitmap Optional image to render; if null a placeholder text is shown.
 * @param width The card width (typically computed to fill the content area).
 * @param imageHeight Height of the image area inside the card (default = 260.dp).
 */
@Composable
private fun FullWidthRecommendationCard(
    title: String,
    categoryTitle: String,
    distanceMeters: Double,
    bitmap: android.graphics.Bitmap?,
    width: Dp,
    imageHeight: Dp = 260.dp
) {
    Card(
        modifier = Modifier.width(width),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {


            if (categoryTitle.isNotBlank()) {
                Text(
                    text = categoryTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
            }

            // Primary title — limit to two lines and ellipsize if needed
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Image area — the image fills the card width and the specified height.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (bitmap != null) {
                // Render the provided bitmap, clipped to the theme's medium shape
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

/**
 * DistanceChip
 *
 * Small surface that displays a human-friendly distance string.
 * - Converts meters >= 1609 into miles (1 mile = 1609.34 m) and rounds to one decimal.
 * - For distances < 1609, renders feet as an integer (1 meter = 3.28084 feet).
 *
 * @param meters Distance in meters.
 * @param modifier Modifier for positioning (default = Modifier).
 */
@Composable
private fun DistanceChip(meters: Double, modifier: Modifier = Modifier) {
    // Convert meters to a human-friendly string and unit in imperial (mi/ft)
    val (value, unit, text) = if (meters >= 1609) {
        val miles = ((meters / 1609.34) * 10).roundToInt() / 10.0
        Triple(miles, "mi", "$miles mi")
    } else {
        val feet = (meters * 3.28084).roundToInt()
        Triple(feet.toDouble(), "ft", "$feet ft")
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
