package com.unh.hoppin_android_app

import android.util.Log
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Handles automatic daily streak updates for logged-in users.
 * Should be called once when the app starts (e.g., inside MainActivity).
 */
@Composable
fun UserAutoStreakHandler(
    navToHome: (String) -> Unit,
    streakVM: GamificationStreakViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            if (user != null) {
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("users").document(user.uid).get().await()

                val userName =
                    doc.getString("name")
                        ?: user.displayName
                        ?: doc.getString("displayName")
                        ?: "User"

                // Idempotent daily check-in — safe to call multiple times
                streakVM.dailyCheckIn { _, _ ->
                    navToHome(userName)
                }
            }
        } catch (e: Exception) {
            Log.e("UserAutoStreakHandler", "Auto check-in failed: ${e.message}")
        }
    }
}
