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

data class Category(
    val id: Int,
    val title: String,
    val image: Int
)

@Composable
fun CategoryItem(navController: NavController, category: Category, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(80.dp)
            .clickable{
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
            maxLines = 1
        )
    }
}

@Composable
fun BrowseCategoriesSection(navController: NavController, categories: List<Category>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.headlineSmall,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories) { category ->
                CategoryItem(navController = navController,category = category)
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

    val navController = rememberNavController()
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Spacer(modifier = Modifier.height(20.dp))
        BrowseCategoriesSection(navController = navController, categories = mockCategories)
    }
}
