package com.unh.hoppin_android_app.viewmodels

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.unh.hoppin_android_app.dataStore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val notificationsEnabled: Boolean = true,
    val logoutSuccessful: Boolean = false,
    val showPasswordUpdateNotification: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val dataStore = application.dataStore

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    companion object {
        val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
    }

    init {
        loadNotificationPreference()
    }

    private fun loadNotificationPreference() {
        viewModelScope.launch {
            val notificationsEnabled = dataStore.data.map { preferences ->
                preferences[NOTIFICATIONS_ENABLED_KEY] ?: true
            }.first()
            _uiState.value = _uiState.value.copy(notificationsEnabled = notificationsEnabled)
        }
    }

    fun setNotificationPreference(isEnabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[NOTIFICATIONS_ENABLED_KEY] = isEnabled
            }
            _uiState.value = _uiState.value.copy(notificationsEnabled = isEnabled)
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        if (currentPassword.isBlank() || newPassword.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Passwords cannot be empty.")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, successMessage = null)
        val user = auth.currentUser
        val email = user?.email

        if (user != null && email != null) {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    user.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                successMessage = "Password updated successfully!",
                                showPasswordUpdateNotification = true
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = updateTask.exception?.message ?: "Failed to update password."
                            )
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Authentication failed. Please check your current password."
                    )
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "User not found.")
        }
    }

    fun logOut() {
        auth.signOut()
        _uiState.value = _uiState.value.copy(logoutSuccessful = true)
    }

    fun passwordNotificationShown() {
        _uiState.value = _uiState.value.copy(showPasswordUpdateNotification = false)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}