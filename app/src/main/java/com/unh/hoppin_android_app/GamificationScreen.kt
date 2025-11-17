package com.unh.hoppin_android_app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamificationScreen(
    navController: NavController,
    userName: String = "User"
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "no-user"

    // Streak state (already implemented)
    val streakViewModel: GamificationStreakViewModel = viewModel(key = uid)
    val streakState by streakViewModel.streak.collectAsState()

    // Category progress state (backed by Firestore /gamification/categoryProgress)
    val categoryVm: CategoryProgressViewModel = viewModel(key = "category-$uid")
    val categoryState by categoryVm.state.collectAsState()

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gamification") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.Badge, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(12.dp))

            // ---------------- Profile + Intro ----------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE0E0E0),
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        Icons.Default.Badge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "Earn streaks & badges by visiting places!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---------------- Streak Hero Card ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Current Streak",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${streakState.currentStreak} days",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Best: ${streakState.bestStreak} days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        tonalElevation = 4.dp,
                        modifier = Modifier.size(56.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔥",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- Streak Milestones ----------------
            Text(
                "Streak Milestones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            val targets = listOf(1, 3, 7, 14, 30)
            StreakRow(
                achieved = targets.filter { it <= streakState.currentStreak }.toSet(),
                streaks = targets
            )

            Spacer(Modifier.height(24.dp))

            // ---------------- Category Badges ----------------
            Text(
                "Category Badges",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))

            Text(
                text = "Visit distinct real-world places to unlock badges for each category.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(12.dp))

            // Helper to read tier from state
            fun tier(catId: Int): Int = categoryState.tiers[catId] ?: 0

            // Define your badges: category + minimum tier required
            val themed = remember {
                listOf(
                    // Category 1 (Explore)
                    BadgeItem(
                        title = "Explorer",
                        rule = "Bronze: Explore places",
                        icon = Icons.Default.Explore,
                        categoryId = 1,
                        minTier = 1 // Bronze+
                    ),
                    BadgeItem(
                        title = "Trailblazer",
                        rule = "Gold: Explore a lot",
                        icon = Icons.Default.Explore,
                        categoryId = 1,
                        minTier = 3 // Gold+
                    ),

                    // Category 2 (Refresh)
                    BadgeItem(
                        title = "Foodie",
                        rule = "Bronze: Restaurants, cafes & bars",
                        icon = Icons.Default.LocalDining,
                        categoryId = 2,
                        minTier = 1
                    ),
                    BadgeItem(
                        title = "Gourmet",
                        rule = "Gold: Many Refresh spots",
                        icon = Icons.Default.LocalDining,
                        categoryId = 2,
                        minTier = 3
                    ),

                    // Category 4 (ShopStop)
                    BadgeItem(
                        title = "ShopStopper",
                        rule = "Bronze: Shopping & stores",
                        icon = Icons.Default.Map,
                        categoryId = 4,
                        minTier = 1
                    ),

                    // Category 6 (Wellbeing)
                    BadgeItem(
                        title = "Wellbeing Hero",
                        rule = "Bronze: Gyms, clinics, salons",
                        icon = Icons.Default.Badge,
                        categoryId = 6,
                        minTier = 1
                    ),

                    // Generic travel/photography (based on Explore category)
                    BadgeItem(
                        title = "Photographer",
                        rule = "Silver: Capture your trips",
                        icon = Icons.Default.CameraAlt,
                        categoryId = 1,
                        minTier = 2 // Silver+
                    )
                )
            }

            // Grid layout – 3 per row
            themed.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    row.forEach { item ->
                        val achieved = tier(item.categoryId) >= item.minTier
                        BadgeCircle(
                            item = item,
                            achieved = achieved,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 10.dp)
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------- Helper UI pieces ----------------

@Composable
private fun StreakRow(
    achieved: Set<Int>,
    streaks: List<Int>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        streaks.forEach { d ->
            val isOn = d in achieved
            AssistChip(
                onClick = { },
                label = {
                    Text(
                        text = "${d}d",
                        fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                leadingIcon = if (isOn) {
                    { Text("✅") }
                } else null,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isOn)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface,
                    labelColor = if (isOn)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/**
 * One badge definition: which category it belongs to & min tier required.
 */
private data class BadgeItem(
    val title: String,
    val rule: String,
    val icon: ImageVector,
    val categoryId: Int,
    val minTier: Int
)

@Composable
private fun BadgeCircle(
    item: BadgeItem,
    achieved: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val tint = if (achieved) MaterialTheme.colorScheme.primary else Color(0xFFB0BEC5)

        Surface(
            shape = CircleShape,
            tonalElevation = if (achieved) 4.dp else 0.dp,
            color = if (achieved)
                MaterialTheme.colorScheme.primaryContainer
            else
                Color(0xFFF5F5F5),
            border = BorderStroke(
                1.dp,
                if (achieved) MaterialTheme.colorScheme.primary else Color(0xFFCFD8DC)
            ),
            modifier = Modifier.size(72.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = tint
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            item.rule,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            maxLines = 2
        )
    }
}
