package com.unh.hoppin_android_app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class ForgotPasswordViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState = _uiState.asStateFlow()

    fun sendPasswordResetEmail(email: String) {
        if (email.isBlank()) {
            _uiState.value = ForgotPasswordUiState(errorMessage = "Email cannot be empty.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ForgotPasswordUiState(isLoading = true)
            auth.sendPasswordResetEmail(email.trim())
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        _uiState.value = ForgotPasswordUiState(
                            successMessage = "Password reset link sent! Please check your email."
                        )
                    } else {
                        _uiState.value = ForgotPasswordUiState(
                            errorMessage = task.exception?.message ?: "An unknown error occurred."
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _uiState.value = ForgotPasswordUiState()
    }
}