package com.unh.hoppin_android_app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamificationScreen(
    navController: NavController,
    userName: String = "Raghav",
    xp: Int = 420,
    level: Int = 4,
    levelProgress: Float = 0.35f,
    viewModel: GamificationStreakViewModel = viewModel()
) {
    val streakState by viewModel.streak.collectAsState()

    // Ensure auto check-in if the screen is opened directly (defensive; idempotent)
    LaunchedEffect(Unit) {
        viewModel.ensureTodayCheckIn()
    }

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
        ) {
            Spacer(Modifier.height(12.dp))

            // --- Centered Profile Header ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE0E0E0),
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = "Profile Badge",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "Keep exploring to earn more!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(16.dp))

            // XP / Level
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Level $level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { levelProgress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("$xp XP • ${((1f - levelProgress) * 100).toInt()}% to next level", color = Color.Gray)
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Streaks (realtime) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Streak Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                // No button needed – auto check-in handled by VM
                AssistChip(
                    onClick = { /* disabled */ },
                    enabled = false,
                    label = { Text("Auto") }
                )
            }

            Spacer(Modifier.height(8.dp))

            val targets = listOf(1, 3, 7, 14, 30)
            StreakRow(
                achieved = targets.filter { it <= streakState.currentStreak }.toSet(),
                streaks = targets
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "Current: ${streakState.currentStreak} • Best: ${streakState.bestStreak}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )

            Spacer(Modifier.height(20.dp))

            // --- Themed Badges (sample) ---
            Text("Themed Badges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            val themed = remember {
                listOf(
                    BadgeItem("Explorer", "Visit/save 5, 20, 50 places", Icons.Default.Explore, achieved = true),
                    BadgeItem("Foodie", "Review 10 restaurants", Icons.Default.LocalDining, achieved = true),
                    BadgeItem("Culture Buff", "Visit 3 museums", Icons.Default.Badge, achieved = false),
                    BadgeItem("Photographer", "Upload 10 photos", Icons.Default.CameraAlt, achieved = false),
                    BadgeItem("Itinerary Maker", "Create 3 custom trips", Icons.Default.Map, achieved = false),
                    BadgeItem("Aficionado", "Become a local expert", Icons.Default.Badge, achieved = false)
                )
            }

            themed.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    row.forEach { item ->
                        BadgeCircle(
                            item = item,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 10.dp)
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ===== Your helpers from the original screen =====

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
                onClick = { /* no-op */ },
                label = { Text("${d}d") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    labelColor = if (isOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

private data class BadgeItem(
    val title: String,
    val rule: String,
    val icon: ImageVector,
    val achieved: Boolean
)

@Composable
private fun BadgeCircle(
    item: BadgeItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val tint = if (item.achieved) MaterialTheme.colorScheme.primary else Color(0xFFB0BEC5)
        Surface(
            shape = CircleShape,
            tonalElevation = if (item.achieved) 2.dp else 0.dp,
            color = if (item.achieved) MaterialTheme.colorScheme.primaryContainer else Color(0xFFF5F5F5),
            border = BorderStroke(1.dp, if (item.achieved) MaterialTheme.colorScheme.primary else Color(0xFFCFD8DC)),
            modifier = Modifier.size(72.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, tint = tint)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(item.rule, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 2)
    }
}
