package com.unh.hoppin_android_app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Build
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

    // Streak state (already implemented in your VM)
    val streakViewModel: GamificationStreakViewModel = viewModel(key = uid)
    val streakState by streakViewModel.streak.collectAsState()

    // Category progress state (backed by Firestore /gamification/categoryProgress)
    val categoryVm: CategoryProgressViewModel = viewModel(key = "category-$uid")
    val categoryState by categoryVm.state.collectAsState()

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = Color(0xFFFFFDE7), // 🌼 pale warm yellow background
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
                // Profile icon – warm & strong
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFB300), // bright amber
                    shadowElevation = 8.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3E2723)
                )
                Text(
                    text = "Earn streaks & badges by visiting places!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6D4C41)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---------------- Streak Hero Card (blue theme) ----------------
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E88E5) // strong blue
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
                            color = Color(0xFFE3F2FD) // very light blue
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${streakState.currentStreak} days",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Best: ${streakState.bestStreak} days",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFBBDEFB)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF0D47A1), // deeper blue for accent
                        tonalElevation = 4.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(
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

            // ---------------- Streak Milestones (blue-ish chips) ----------------
            Text(
                "Streak Milestones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A237E)
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
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF3E2723)
            )
            Spacer(Modifier.height(4.dp))

            Text(
                text = "Visit distinct real-world places to level up each category badge.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6D4C41)
            )

            Spacer(Modifier.height(12.dp))

            // Helper to read visits for a category from state
            fun visitsFor(catId: Int): Int = categoryState.visits[catId] ?: 0

            // One badge per category, using your 1–8 mapping
            val badges = remember {
                listOf(
                    BadgeItem("Explore", Icons.Default.Explore, categoryId = 1),
                    BadgeItem("Refresh", Icons.Default.LocalDining, categoryId = 2),
                    BadgeItem("Entertain", Icons.Default.Map, categoryId = 3),
                    BadgeItem("ShopStop", Icons.Default.Map, categoryId = 4),
                    BadgeItem("Relax", Icons.Default.Spa, categoryId = 5),
                    BadgeItem("Wellbeing", Icons.Default.HealthAndSafety, categoryId = 6),
                    BadgeItem("Emergency", Icons.Default.LocalHospital, categoryId = 7),
                    BadgeItem("Services", Icons.Default.Build, categoryId = 8)
                )
            }

            // Grid layout – 2 per row
            badges.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    row.forEach { item ->
                        val visits = visitsFor(item.categoryId)
                        BadgeCircle(
                            item = item,
                            visits = visits,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 10.dp)
                        )
                    }
                    if (row.size == 1) {
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
                        Color(0xFFE3F2FD) // light blue
                    else
                        Color(0xFFFFFFFF),
                    labelColor = if (isOn)
                        Color(0xFF0D47A1)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/**
 * One badge definition: one per category.
 *
 * 1 – Explore
 * 2 – Refresh
 * 3 – Entertain
 * 4 – ShopStop
 * 5 – Relax
 * 6 – Wellbeing
 * 7 – Emergency
 * 8 – Services
 */
private data class BadgeItem(
    val title: String,
    val icon: ImageVector,
    val categoryId: Int
)

@Composable
private fun BadgeCircle(
    item: BadgeItem,
    visits: Int,
    modifier: Modifier = Modifier
) {
    val locked = visits <= 0
    val tierLabel = tierLabelForVisits(visits)
    val bgColor = if (locked) Color(0xFFF5F5F5) else categoryColor(item.categoryId)
    val borderColor = if (locked) Color(0xFFCFD8DC) else categoryColor(item.categoryId)
    val iconTint = if (locked) Color(0xFF9E9E9E) else Color.White

    val statusText = when {
        locked -> "Locked"
        visits == 1 -> "$tierLabel · 1 visit"
        else -> "$tierLabel · $visits visits"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            tonalElevation = if (!locked) 6.dp else 0.dp,
            shadowElevation = if (!locked) 6.dp else 0.dp,
            color = bgColor,
            border = BorderStroke(2.dp, borderColor),
            modifier = Modifier.size(72.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.title,
                    tint = iconTint
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF3E2723)
        )
        Text(
            statusText,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF6D4C41),
            maxLines = 2
        )
    }
}

/* ---------------- Tier logic: thresholds + labels ---------------- */

private fun tierForVisits(visits: Int): Int = when {
    visits >= 5000 -> 10      // Mythic
    visits >= 1000 -> 9       // Legendary
    visits >= 500  -> 8       // Grandmaster
    visits >= 250  -> 7       // Master
    visits >= 100  -> 6       // Diamond
    visits >= 50   -> 5       // Platinum
    visits >= 25   -> 4       // Gold
    visits >= 10   -> 3       // Silver
    visits >= 5    -> 2       // Bronze
    visits >= 1    -> 1       // Unlocked
    else           -> 0       // Locked
}

private fun tierLabelForVisits(visits: Int): String = when (tierForVisits(visits)) {
    0 -> "Locked"
    1 -> "Unlocked"
    2 -> "Bronze"
    3 -> "Silver"
    4 -> "Gold"
    5 -> "Platinum"
    6 -> "Diamond"
    7 -> "Master"
    8 -> "Grandmaster"
    9 -> "Legendary"
    10 -> "Mythic"
    else -> "Unlocked"
}

/**
 * Category-based color mapping (matching your category semantics).
 *
 * 1 – Explore
 * 2 – Refresh
 * 3 – Entertain
 * 4 – ShopStop
 * 5 – Relax
 * 6 – Wellbeing
 * 7 – Emergency
 * 8 – Services
 */
private fun categoryColor(categoryId: Int): Color = when (categoryId) {
    1 -> Color(0xFF42A5F5) // Explore – bright blue
    2 -> Color(0xFFFF9800) // Refresh – orange
    3 -> Color(0xFFAB47BC) // Entertain – purple
    4 -> Color(0xFF8D6E63) // ShopStop – warm brown
    5 -> Color(0xFF26C6DA) // Relax – aqua / chill
    6 -> Color(0xFF66BB6A) // Wellbeing – green
    7 -> Color(0xFFE53935) // Emergency – red
    8 -> Color(0xFF5C6BC0) // Services – indigo
    else -> Color(0xFFFFB300)
}
