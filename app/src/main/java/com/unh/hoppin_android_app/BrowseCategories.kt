package com.unh.hoppin_android_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.unh.hoppin_android_app.ui.theme.cardColor

/**
 * Represents a single category model.
 */
data class Category(
    val id: Int,
    val title: String,
    val image: Int
)

/**
 * Single category pill (icon + label).
 * Navigation behaviour unchanged.
 */
@Composable
fun CategoryItem(
    navController: NavController,
    category: Category,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(72.dp),          // small enough so 4 fit in the card
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(60.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.9f),
            shadowElevation = 0.dp,
            onClick = { navController.navigate("sub/${category.id}") }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(category.image),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = category.title,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            color = Color.Black,
            maxLines = 1
        )
    }
}


/**
 * BrowseCategoriesSection
 *
 * Heading is outside the white card.
 * White card margin/width matches Recommendations card.
 */
@Composable
fun BrowseCategoriesSection(
    navController: NavController,
    categories: List<Category>
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val outerHorizontalPadding = 24.dp           // match Recommendations
    val cardWidth = screenWidthDp - (outerHorizontalPadding * 2)

    Column(modifier = Modifier.fillMaxWidth()) {

        // Heading outside the card
        Text(
            text = "Categories",
            style = MaterialTheme.typography.headlineSmall,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Center the card with same width as Recommendations
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFC857),
                                Color(0xFFF7B267)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(2.dp) // gradient border
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = cardColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 16.dp)
                    ) {
                        // Scrollable row; with width 72dp & spacing, 4 fit inside the card
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(categories) { category ->
                                CategoryItem(
                                    navController = navController,
                                    category = category
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BrowseCategoriesPreview() {
    val mockCategories = listOf(
        Category(1, "Wellbeing", R.drawable.binoculars),
        Category(2, "Emergency", R.drawable.binoculars),
        Category(3, "Services", R.drawable.binoculars),
        Category(4, "Explore", R.drawable.binoculars)
    )

    val navController = rememberNavController()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEDEDED))
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        BrowseCategoriesSection(
            navController = navController,
            categories = mockCategories
        )
    }
}
