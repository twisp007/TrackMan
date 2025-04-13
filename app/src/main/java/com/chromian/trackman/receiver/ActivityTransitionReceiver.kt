package com.chromian.trackman.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chromian.trackman.data.repo.VehicleStatusRepository
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityTransitionReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "ActivityTransitionRec"
        const val TRANSITIONS_RECEIVER_ACTION = "com.chromian.trackman.TRANSITION_ACTION"
    }

    // Get repository instance (safe as it's a Singleton object)
    private val repository = VehicleStatusRepository

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "onReceive triggered for action: ${intent?.action}")

        if (intent == null || intent.action != TRANSITIONS_RECEIVER_ACTION) {
            Log.w(TAG, "Received unknown intent action: ${intent?.action}")
            return
        }

        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)
            Log.d(TAG, "ActivityTransitionResult extracted.")
            result?.let { processTransitionResult(it) }
        } else {
            Log.w(TAG, "Intent did not contain ActivityTransitionResult.")
        }
    }

    private fun processTransitionResult(result: ActivityTransitionResult) {
        for (event in result.transitionEvents) {
            val activityTypeStr = activityTypeToString(event.activityType)
            val transitionTypeStr = transitionTypeToString(event.transitionType)
            Log.i(TAG, "Transition event: Activity=$activityTypeStr, Transition=$transitionTypeStr")

            repository.updateStatus(event.activityType)
        }
    }

    // Helper functions (activityTypeToString, transitionTypeToString) remain the same...
    private fun activityTypeToString(activityType: Int): String {
        return when (activityType) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            else -> "UNKNOWN ($activityType)"
        }
    }

    private fun transitionTypeToString(transitionType: Int): String {
        return when (transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
            else -> "UNKNOWN ($transitionType)"
        }
    }
}