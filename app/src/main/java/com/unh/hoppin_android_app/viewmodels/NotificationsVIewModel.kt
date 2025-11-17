// Create new file: NotificationsViewModel.kt
package com.unh.hoppin_android_app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NotificationsViewModel : ViewModel() {
    val notifications: StateFlow<List<AppNotification>> =
        NotificationRepositoryFirebase.getNotificationsFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
}