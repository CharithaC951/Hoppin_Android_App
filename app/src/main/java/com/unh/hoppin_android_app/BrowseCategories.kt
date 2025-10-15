package com.unh.hoppin_android_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Category(
    val id: Int,
    val title: String,
    val image: Int
)

/**
 * Composable for a single category card (icon placeholder + title).
 */
@Composable
fun CategoryItem(category: Category, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(80.dp), // Fixed width for consistent card size
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = Color.Transparent, // Light purple background
                    shape = RoundedCornerShape(12.dp) // Rounded corners
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(category.image),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = category.title,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black,
            maxLines = 1
        )
    }
}

@Composable
fun BrowseCategoriesSection(categories: List<Category>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header Text
        Text(
            text = "Categories",
            style = MaterialTheme.typography.headlineSmall,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Horizontal Scrolling List
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp) // Spacing between cards
        ) {
            items(categories) { category ->
                CategoryItem(category = category)
            }
        }
    }
}

// --- Preview ---

@Preview(showBackground = true)
@Composable
fun BrowseCategoriesPreview() {
    val mockCategories = listOf(
        Category(1, "Title 1", R.drawable.binoculars),
        Category(2, "Title 2",R.drawable.binoculars),
        Category(3, "Title 3",R.drawable.binoculars),
        Category(4, "Title 4",R.drawable.binoculars),
        Category(5, "Title 5",R.drawable.binoculars),
        Category(6, "Title 6",R.drawable.binoculars)
    )

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // You would typically place this section inside a larger screen composable
        Spacer(modifier = Modifier.height(20.dp))
        BrowseCategoriesSection(categories = mockCategories)
    }
}
