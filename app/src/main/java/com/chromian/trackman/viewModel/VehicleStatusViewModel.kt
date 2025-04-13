package com.chromian.trackman.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chromian.trackman.data.repo.ACTIVITY_TYPE
import com.chromian.trackman.data.repo.VehicleStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow // Import MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow // Import asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update // Import update
import java.text.SimpleDateFormat // For timestamp
import java.util.Date // For timestamp
import java.util.Locale // For timestamp

// Define max lines constant, accessible within the ViewModel
private const val MAX_LOG_LINES = 100

// ViewModel observes the Repository and exposes the state to the UI
class VehicleStatusViewModel : ViewModel() {
    // Get the singleton instance of the repository
    private val repository = VehicleStatusRepository

    val isTracking: StateFlow<Boolean> = repository.isTracking
        .stateIn( /* ... same ... */
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = false
        )

    val updateCounter: StateFlow<Int> = repository.updateCount
        .stateIn( /* Add stateIn if not already present from repo */
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Or Lazily
            initialValue = repository.updateCount.value // Get initial value
        )


    val currentActivity: StateFlow<ACTIVITY_TYPE?> = repository.currentActivity
        .stateIn( /* ... same ... */
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    val isInVehicle: StateFlow<Boolean> = repository.currentActivity // Observe repo directly
        .combine(isTracking) { activity, tracking ->
            tracking && activity == ACTIVITY_TYPE.IN_VEHICLE
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )


    val isOnBicycle: StateFlow<Boolean> = repository.currentActivity // Observe repo directly
        .combine(isTracking) { activity, tracking ->
            tracking && activity == ACTIVITY_TYPE.ON_BICYCLE
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )


    // --- UI Log State ---
    private val _uiLogs = MutableStateFlow<List<String>>(emptyList())
    val uiLogs: StateFlow<List<String>> = _uiLogs.asStateFlow()

    // Function to add log messages, callable from UI
    fun addLogMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logLine = "$timestamp: $message"

        _uiLogs.update { currentLogs ->
            // Create a mutable copy to modify
            val updatedLogs = currentLogs.toMutableList()
            // Add the new log line
            updatedLogs.add(logLine)
            // Ensure the list doesn't exceed the max size
            while (updatedLogs.size > MAX_LOG_LINES) {
                updatedLogs.removeFirst() // Remove the oldest log line
            }
            // Return the updated immutable list
            updatedLogs.toList()
        }
    }
}