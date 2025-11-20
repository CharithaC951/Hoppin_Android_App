package com.unh.hoppin_android_app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unh.hoppin_android_app.viewmodels.CommonReview
import com.unh.hoppin_android_app.viewmodels.FeedViewModel
import com.unh.hoppin_android_app.viewmodels.SharedItinerary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenItinerary: (String) -> Unit = {}
) {
    val vm: FeedViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community Feed") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->

        when {
            uiState.loading -> Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            uiState.error != null -> Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.error ?: "Something went wrong",
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> {
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.surface),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // -------------------- Header --------------------
                    item {
                        Text(
                            text = "Discover Trips from the Community",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "See what others are exploring and read their latest reviews.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // -------------------- Shared itineraries --------------------
                    items(uiState.itineraries, key = { it.id }) { itinerary ->
                        val reviewsForTrip = uiState.reviews
                            .filter { r -> itinerary.placeIds.contains(r.placeId) }
                            .take(3)

                        SharedItineraryCard(
                            itinerary = itinerary,
                            reviews = reviewsForTrip,
                            onOpen = { onOpenItinerary(itinerary.id) }
                        )
                    }

                    if (uiState.itineraries.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No shared trips yet.\nShare one of your itineraries to see it here!",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // -------------------- All reviews section --------------------
                    item {
                        Spacer(Modifier.height(12.dp))
                        Divider()
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "All community reviews",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Latest reviews from Hoppin users across all places.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (uiState.reviews.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No reviews yet.\nStart by reviewing a place you visited!",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        items(uiState.reviews, key = { it.id }) { review ->
                            ReviewFeedCard(review = review)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedItineraryCard(
    itinerary: SharedItinerary,
    reviews: List<CommonReview>,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        onClick = onOpen
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: name + meta
            Text(
                text = itinerary.name.ifBlank { "Untitled Trip" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val placeCount = itinerary.placeIds.size
                Text(
                    text = "$placeCount place" + if (placeCount == 1) "" else "s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                itinerary.createdAt?.let { ts ->
                    Text(
                        text = " • " + formatShortDate(ts.toDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (itinerary.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = itinerary.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))

            Divider()

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (reviews.isEmpty()) "No reviews yet" else "Recent reviews from this trip",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(6.dp))

            if (reviews.isEmpty()) {
                Text(
                    text = "Be the first to review places in this trip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                reviews.forEach { review ->
                    ReviewSnippetRow(review = review)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ReviewFeedCard(review: CommonReview) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            ReviewSnippetRow(review = review)
        }
    }
}

@Composable
private fun ReviewSnippetRow(review: CommonReview) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Stars
            Row {
                repeat(5) { idx ->
                    val filled = idx < review.rating
                    Icon(
                        imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = review.author.ifBlank { "Anonymous" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )

            if (review.placeName.isNotBlank()) {
                Text(
                    text = "• " + review.placeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (review.text.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = review.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        review.createdAt?.toDate()?.let { date ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatShortDateTime(date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatShortDate(date: Date): String {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    return fmt.format(date)
}

private fun formatShortDateTime(date: Date): String {
    val fmt = SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
    return fmt.format(date)
}
