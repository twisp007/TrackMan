package com.chromian.trackman.viewModel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chromian.trackman.data.repo.ACTIVITY_TYPE
import com.chromian.trackman.data.repo.VehicleStatusRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// ViewModel observes the Repository and exposes the state to the UI
class VehicleStatusViewModel : ViewModel() {
    // Get the singleton instance of the repository
    private val repository = VehicleStatusRepository

    val isTracking: StateFlow<Boolean> = repository.isTracking
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = false
        )

    val updateCounter: StateFlow<Int> = repository.updateCount

    // Expose the StateFlow from the repository to the UI
    // Use stateIn to make it lifecycle-aware within the ViewModel scope
    // and share the subscription across multiple UI observers if necessary.
    val currentActivity: StateFlow<ACTIVITY_TYPE?> = repository.currentActivity
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    val isInVehicle: StateFlow<Boolean> = combine(currentActivity) { args ->
        val currentActivity = args[0]
        currentActivity == ACTIVITY_TYPE.IN_VEHICLE
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = false
    )

    val isOnBicycle: StateFlow<Boolean> = combine(currentActivity) { args ->
        val currentActivity = args[0]
        currentActivity == ACTIVITY_TYPE.ON_BICYCLE
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = false
    )
}