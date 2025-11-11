package com.unh.hoppin_android_app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ResetStep {
    EnterEmail,
    EnterOtp,
    EnterNewPassword,
    Success
}

data class ResetPasswordUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentStep: ResetStep = ResetStep.EnterEmail,
    val email: String = "" // Store the email across steps
)

class ResetPasswordViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState = _uiState.asStateFlow()

    fun sendOtp(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Email cannot be empty.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _uiState.value = ResetPasswordUiState(
                currentStep = ResetStep.EnterOtp,
                email = email
            )
        }
    }

    fun verifyOtp(otp: String) {
        if (otp.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "OTP cannot be empty.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            if (otp == "123456") {
                _uiState.value = _uiState.value.copy(
                    currentStep = ResetStep.EnterNewPassword,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Invalid OTP. Please try again.",
                    isLoading = false
                )
            }
        }
    }

    fun resetPassword(newPassword: String, confirmPassword: String) {
        if (newPassword != confirmPassword) {
            _uiState.value = _uiState.value.copy(errorMessage = "Passwords do not match.")
            return
        }
        if (newPassword.length < 6) {
            _uiState.value = _uiState.value.copy(errorMessage = "Password must be at least 6 characters.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _uiState.value = _uiState.value.copy(
                currentStep = ResetStep.Success,
                isLoading = false
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}