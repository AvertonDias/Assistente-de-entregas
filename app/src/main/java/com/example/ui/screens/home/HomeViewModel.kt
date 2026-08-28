package com.example.ui.screens.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.accessibility.AccessibilityAutomationEngine
import com.example.data.repository.AppSettings
import com.example.data.repository.DeliveryRepository
import com.example.data.repository.DeliveryStats
import com.example.data.repository.SettingsRepository
import com.example.service.FloatingBubbleService
import com.example.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isOverlayGranted: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isBubbleServiceRunning: Boolean = false
)

class HomeViewModel(
    private val deliveryRepository: DeliveryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val deliveryStats: StateFlow<DeliveryStats> = deliveryRepository.getDeliveryStats()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DeliveryStats()
        )

    val settings: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun refreshPermissions(context: Context) {
        val overlay = PermissionUtils.hasOverlayPermission(context)
        val accessibility = PermissionUtils.isAccessibilityServiceEnabled(context) ||
                AccessibilityAutomationEngine.isServiceActive()

        _uiState.value = _uiState.value.copy(
            isOverlayGranted = overlay,
            isAccessibilityEnabled = accessibility
        )
    }

    fun toggleFloatingBubble(context: Context, start: Boolean) {
        val intent = Intent(context, FloatingBubbleService::class.java)
        if (start && PermissionUtils.hasOverlayPermission(context)) {
            try {
                context.startService(intent)
                _uiState.value = _uiState.value.copy(isBubbleServiceRunning = true)
                viewModelScope.launch {
                    settingsRepository.setBubbleEnabled(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            context.stopService(intent)
            _uiState.value = _uiState.value.copy(isBubbleServiceRunning = false)
            viewModelScope.launch {
                settingsRepository.setBubbleEnabled(false)
            }
        }
    }

    class Factory(
        private val deliveryRepository: DeliveryRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(deliveryRepository, settingsRepository) as T
        }
    }
}
