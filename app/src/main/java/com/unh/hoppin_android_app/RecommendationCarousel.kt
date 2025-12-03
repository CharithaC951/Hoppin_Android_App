package com.unh.hoppin_android_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unh.hoppin_android_app.ui.theme.cardColor
import com.unh.hoppin_android_app.viewmodels.RecommendationsUiState
import kotlin.math.roundToInt

/**
 * RecommendationsBlock
 */
@Composable
fun RecommendationsBlock(
    ui: RecommendationsUiState,
    outerHorizontalPadding: Dp = 24.dp,
    onPlaceClick: (String) -> Unit = {}
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
            Text(
                "No recommendations found.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            return
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = screenWidthDp - (outerHorizontalPadding * 2)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(ui.flatItems, key = { it.placeId }) { item ->
            FullWidthRecommendationCard(
                title = item.title,
                categoryTitle = item.categoryTitle,
                distanceMeters = item.distanceMeters,
                bitmap = item.bitmap,
                width = cardWidth,
                onClick = { onPlaceClick(item.placeId) }
            )
        }
    }
}

/**
 * FullWidthRecommendationCard
 */
@Composable
private fun FullWidthRecommendationCard(
    title: String,
    categoryTitle: String,
    distanceMeters: Double,
    bitmap: android.graphics.Bitmap?,
    width: Dp,
    imageHeight: Dp = 220.dp,
    onClick: () -> Unit = {}
) {
    // Outer gradient frame
    Box(
        modifier = Modifier
            .width(width)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFC857), // softer golden
                        Color(0xFFF7B267)  // warm peach
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(2.dp) // gradient border thickness
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            onClick = onClick
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
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
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
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

            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * DistanceChip
 */
@Composable
private fun DistanceChip(meters: Double, modifier: Modifier = Modifier) {
    val text = if (meters >= 1609) {
        val miles = ((meters / 1609.34) * 10).roundToInt() / 10.0
        "$miles mi"
    } else {
        val feet = (meters * 3.28084).roundToInt()
        "$feet ft"
    }

    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.92f),
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 4.dp,
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
