package com.chromian.trackman.data.repo


import android.util.Log
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ACTIVITY_TYPE(val title: String) {
    IN_VEHICLE("In Vehicle"),
    ON_BICYCLE("On Bicycle"),
    RUNNING("Running"),
    STILL("Still"),
    WALKING("Walking"),
    ON_FOOT("On Foot")
}

// Singleton Repository holding the application-wide vehicle status
object VehicleStatusRepository {
    private const val TAG = "VehicleStatusRepo"

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    private val _updateCount = MutableStateFlow(0)
    val updateCount = _updateCount.asStateFlow()

    fun updateIsTracking(isTracking: Boolean) {
        _isTracking.update {
            isTracking
        }
        Log.d(TAG, "Updating isTracking to: $isTracking")
    }

    private val _currentActivity = MutableStateFlow<ACTIVITY_TYPE?>(null)
    val currentActivity: StateFlow<ACTIVITY_TYPE?> = _currentActivity.asStateFlow()

    // Function to update the status (called by Receiver)
    fun updateStatus(detectedActivity: Int?) {
        _updateCount.update { it + 1 }

        val currentDetectedActivity = when(detectedActivity) {
            DetectedActivity.IN_VEHICLE -> ACTIVITY_TYPE.IN_VEHICLE
            DetectedActivity.ON_BICYCLE -> ACTIVITY_TYPE.ON_BICYCLE
            DetectedActivity.RUNNING -> ACTIVITY_TYPE.RUNNING
            DetectedActivity.STILL -> ACTIVITY_TYPE.STILL
            DetectedActivity.WALKING -> ACTIVITY_TYPE.WALKING
            DetectedActivity.ON_FOOT -> ACTIVITY_TYPE.ON_FOOT
            else -> null
        }

        _currentActivity.update {
            Log.d(TAG, "Updating status to: $currentDetectedActivity")
            currentDetectedActivity
        }
    }

    // Optional: Could add functions here to fetch initial state from storage, etc.
    // init {
    //     // Load initial state maybe?
    // }
}