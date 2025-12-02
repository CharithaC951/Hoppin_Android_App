package com.unh.hoppin_android_app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            CenterAlignedTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Community Feed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Trips & reviews from Hoppin users",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->

        when {
            uiState.loading -> Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Loading community feed…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            uiState.error != null -> Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Unable to load feed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiState.error ?: "Something went wrong",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // -------------------- Header --------------------
                    item {
                        Column {
                            Text(
                                text = "Discover Trips from the Community",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "See what others are exploring nearby and get inspired for your next outing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val tripCount = uiState.itineraries.size
                            val reviewCount = uiState.reviews.size
                            if (tripCount > 0 || reviewCount > 0) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = buildString {
                                        append("Community activity: ")
                                        append("$tripCount shared trip")
                                        if (tripCount != 1) append("s")
                                        append(" · ")
                                        append("$reviewCount review")
                                        if (reviewCount != 1) append("s")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // -------------------- Shared itineraries section label --------------------
                    if (uiState.itineraries.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Shared Trips",
                                subtitle = "Curated itineraries from the Hoppin community"
                            )
                        }
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
                            EmptyStateCard(
                                title = "No shared trips yet",
                                message = "Share one of your itineraries so others can discover your favorite spots."
                            )
                        }
                    }

                    // -------------------- All reviews section --------------------
                    item {
                        Spacer(Modifier.height(8.dp))
                        Divider()
                        Spacer(Modifier.height(16.dp))

                        SectionHeader(
                            title = "All Community Reviews",
                            subtitle = "Latest reviews across all places in Hoppin"
                        )
                    }

                    if (uiState.reviews.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No reviews yet",
                                message = "Start by reviewing a place you visited and help others decide where to go."
                            )
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

/* -------------------- Small UI helpers -------------------- */

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    message: String
) {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/* -------------------- Cards -------------------- */

@Composable
private fun SharedItineraryCard(
    itinerary: SharedItinerary,
    reviews: List<CommonReview>,
    onOpen: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        onClick = onOpen
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: name + meta
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = itinerary.name.ifBlank { "Untitled Trip" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                }

                AssistChip(
                    onClick = onOpen,
                    label = { Text("View trip") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            if (itinerary.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = itinerary.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

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
                reviews.forEachIndexed { index, review ->
                    ReviewSnippetRow(review = review)
                    if (index != reviews.lastIndex) {
                        Spacer(Modifier.height(6.dp))
                        Divider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewFeedCard(review: CommonReview) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            ReviewSnippetRow(review = review)
        }
    }
}

/* -------------------- Review snippet -------------------- */

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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (review.text.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = review.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        review.createdAt?.toDate()?.let { date ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatShortDateTime(date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* -------------------- Date helpers -------------------- */

private fun formatShortDate(date: Date): String {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    return fmt.format(date)
}

private fun formatShortDateTime(date: Date): String {
    val fmt = SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
    return fmt.format(date)
}
