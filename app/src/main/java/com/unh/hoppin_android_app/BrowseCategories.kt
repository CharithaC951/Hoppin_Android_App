package com.unh.hoppin_android_app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

/**
 * Represents a single category model.
 *
 * @param id Unique identifier for the category.
 * @param title Display title of the category.
 * @param image Drawable resource ID representing the category icon.
 */
data class Category(
    val id: Int,
    val title: String,
    val image: Int
)

/**
 * Displays an individual category item with an icon and title.
 *
 * When clicked, it navigates to a subcategory screen
 * using the given [NavController].
 *
 * @param navController Used to handle navigation actions.
 * @param category The category data to display.
 * @param modifier Optional modifier for layout customization.
 */
@Composable
fun CategoryItem(
    navController: NavController,
    category: Category,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(80.dp)
            .clickable {
                // Navigate to the subcategory screen with the category ID
                navController.navigate("sub/${category.id}")
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
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
            maxLines = 1 // Ensures text doesn’t overflow
        )
    }
}

/**
 * A horizontally scrollable section displaying multiple categories.
 *
 * Includes a section title ("Categories") and a [LazyRow]
 * of clickable [CategoryItem]s.
 *
 * @param navController Used to handle navigation when a category is selected.
 * @param categories The list of categories to display.
 */
@Composable
fun BrowseCategoriesSection(
    navController: NavController,
    categories: List<Category>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section header
        Text(
            text = "Categories",
            style = MaterialTheme.typography.headlineSmall,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Horizontal list of category items
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories) { category ->
                CategoryItem(navController = navController, category = category)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BrowseCategoriesPreview() {
    val mockCategories = listOf(
        Category(1, "Title 1", R.drawable.binoculars),
        Category(2, "Title 2", R.drawable.binoculars),
        Category(3, "Title 3", R.drawable.binoculars),
        Category(4, "Title 4", R.drawable.binoculars),
        Category(5, "Title 5", R.drawable.binoculars),
        Category(6, "Title 6", R.drawable.binoculars)
    )

    val navController = rememberNavController()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        BrowseCategoriesSection(
            navController = navController,
            categories = mockCategories
        )
    }
}
