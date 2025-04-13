package com.chromian.trackman.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
// ... other imports
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.chromian.trackman.MainActivity
import com.chromian.trackman.R
import com.chromian.trackman.data.repo.ACTIVITY_TYPE
import com.chromian.trackman.data.repo.VehicleStatusRepository
import com.chromian.trackman.integration.triggerForActivity
import com.chromian.trackman.receiver.ActivityTransitionReceiver
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
// ... other location imports
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged


class MyPersistentService : LifecycleService() {
    // Get repository instance
    private val repository = VehicleStatusRepository

    private var isServiceRunning = false
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var activityUpdatePI: PendingIntent? = null

    init {
        repository.updateIsTracking(isServiceRunning)
    }


    companion object {
        // Consider moving Channel ID/Name to a Constants file or companion of the Service
        const val CHANNEL_ID = "ActivityTrackingChannel" // Use a descriptive Channel ID
        const val NOTIFICATION_ID = 2 // Use a unique ID for this notification
        // Make actions specific to your app's domain
        const val ACTION_START_TRACKING_SERVICE = "com.chromian.trackman.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING_SERVICE = "com.chromian.trackman.ACTION_STOP_TRACKING"
        private const val TAG = "ActivityTrackingService" // More specific Tag
    }

    // --- Lifecycle Methods ---

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        activityRecognitionClient = ActivityRecognition.getClient(this)
        createNotificationChannel()
        observeActivityStatusFromRepository() // Start observing the repository state
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId) // Essential for LifecycleService
        Log.i(TAG, "Service onStartCommand received - Action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_TRACKING_SERVICE -> {
                if (!isServiceRunning) {
                    Log.i(TAG, "Attempting to start tracking service...")
                    if (hasActivityPermission()) {
                        // Start foreground using initial state from repo
                        startForegroundServiceInternal(repository.currentActivity.value)
                        registerForActivityTransitions()
                        isServiceRunning = true
                        repository.updateIsTracking(isServiceRunning)
                        Log.i(TAG, "Service started successfully and registered for transitions.")
                    } else {
                        Log.e(TAG, "ACTIVITY_RECOGNITION permission not granted. Stopping service.")
                        // Immediately stop if permission is missing
                        stopSelf() // Use stopSelf() as we might be in the process of starting
                    }
                } else {
                    Log.w(TAG, "Service already running, ignoring start command.")
                }
            }
            ACTION_STOP_TRACKING_SERVICE -> {
                Log.i(TAG, "Received stop command.")
                stopServiceInternal()
            }
            else -> {
                Log.w(TAG, "Received unknown or null action.")
            }
        }
        // If the service is killed, START_STICKY will ask the system to restart it
        // with the last intent, allowing tracking to resume (if permissions allow).
        return START_STICKY
    }

    override fun onDestroy() {
        // Ensure cleanup happens if the service is destroyed unexpectedly
        stopServiceInternal()
        super.onDestroy() // Essential for LifecycleService
        Log.i(TAG, "Service onDestroy")
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent) // Essential for LifecycleService
        // We don't provide binding, so return null
        return null
    }

    // --- Core Logic Methods ---

    private fun hasActivityPermission(): Boolean {
        val permission = Manifest.permission.ACTIVITY_RECOGNITION
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "Permission check failed: $permission not granted.")
        }
        return granted
    }

    // Observe the activity status from the Repository
    private fun observeActivityStatusFromRepository() {
        repository.currentActivity
            .onEach { currentActivity ->
                Log.d(TAG, "Observed repository activity status change: ${currentActivity?.title ?: "Unknown"}")
                // Update notification whenever the state changes if the service is running
                if (isServiceRunning) {
                    updateNotification(currentActivity)

                    // start tracking if in vehicle
                    triggerForActivity(
                        context = this,
                        currentActivity = currentActivity
                    )
                }
            }
            .launchIn(lifecycleScope) // Use the service's lifecycleScope
        Log.d(TAG, "Started observing repository state.")
    }

    // Start the service in the foreground with initial notification state
    private fun startForegroundServiceInternal(initialActivity: ACTIVITY_TYPE?) {
        Log.d(TAG, "Starting foreground service with initial activity: ${initialActivity?.title ?: "Unknown"}")
        val notification = createNotification(initialActivity)
        try {
            // Use the type declared in the manifest for Android Q (API 29) and above.
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use the constant matching your 'android:foregroundServiceType="health"' declaration
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else 0 // Type parameter not used before Android Q

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Provide the determined service type
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                // Call the older version without the type parameter
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "Service successfully started in foreground with type: $serviceType")
        } catch (e: Exception) {
            // Catch potential exceptions (e.g., SecurityException, InvalidForegroundServiceTypeException on future OS)
            Log.e(TAG, "Error starting foreground service", e)
            stopSelf() // Stop the service if it cannot run in the foreground
        }
    }

    // --- Activity Recognition Registration ---

    private fun registerForActivityTransitions() {
        // Permission Check - Redundant if start sequence checks, but good safety measure
        if (!hasActivityPermission()) {
            Log.e(TAG, "Cannot register transitions: ACTIVITY_RECOGNITION permission missing.")
            stopServiceInternal() // Ensure service stops if permission lost mid-way
            return
        }

        Log.i(TAG, "Registering for activity transitions...")
        val transitions = buildTransitionList() // Encapsulate transition list creation
        if (transitions.isEmpty()) {
            Log.w(TAG, "No activity transitions defined. Skipping registration.")
            return
        }

        val request = ActivityTransitionRequest(transitions)

        // Create PendingIntent for the BroadcastReceiver
        val intent = Intent(this, ActivityTransitionReceiver::class.java).apply {
            action = ActivityTransitionReceiver.TRANSITIONS_RECEIVER_ACTION // Use constant from Receiver
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT // MUTABLE needed if receiver modifies intent extras (unlikely here)
        }
        // Assign to nullable class member
        activityUpdatePI = PendingIntent.getBroadcast(
            this,
            101, // Unique request code for this PendingIntent
            intent,
            flags
        )

        // Safely attempt registration using the created PendingIntent
        activityUpdatePI?.let { currentPendingIntent ->
            try {
                Log.d(TAG, "Calling requestActivityTransitionUpdates...")
                val task = activityRecognitionClient.requestActivityTransitionUpdates(request, currentPendingIntent)

                task.addOnSuccessListener {
                    Log.i(TAG, "Successfully registered for activity transition updates.")
                }
                task.addOnFailureListener { e ->
                    handleRegistrationFailure(e)
                    // Attempt to clean up the PI reference even on failure
                    activityUpdatePI = null
                    stopServiceInternal() // Stop service if registration fails
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException during requestActivityTransitionUpdates.", e)
                handleRegistrationFailure(e)
                activityUpdatePI = null // Clear PI on security error
                stopServiceInternal()
            }
        } ?: run {
            Log.e(TAG, "Failed to create PendingIntent. Cannot register activity updates.")
            stopServiceInternal() // Stop if PI creation failed
        }
    }

    // Helper to build the list of transitions to monitor
    private fun buildTransitionList(): List<ActivityTransition> {
        val transitions = mutableListOf<ActivityTransition>()
        val activityTypesToMonitor = listOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.RUNNING,
            DetectedActivity.WALKING,
            DetectedActivity.STILL
            // Add/remove activities as needed
        )

        activityTypesToMonitor.forEach { activityType ->
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build()
            )
        }
        Log.d(TAG, "Built transition list for ${transitions.size / 2} activity types.")
        return transitions
    }

    // Centralized handler for registration failures
    private fun handleRegistrationFailure(e: Exception) {
        if (e is SecurityException) {
            Log.e(TAG,"FAILED registration: SecurityException (Permission likely revoked/missing).", e)
        } else {
            Log.e(TAG, "FAILED registration for activity transition updates.", e)
        }
    }

    private fun unregisterFromActivityTransitions() {
        if (!isServiceRunning && activityUpdatePI == null) {
            Log.d(TAG, "Unregistration not needed: Service not running or PI already null.")
            return
        }
        Log.i(TAG, "Attempting to unregister from activity transitions...")

        activityUpdatePI?.let { pendingIntent ->
            // Optional: Check permission again, though less critical for removal
            if (!hasActivityPermission()) {
                Log.w(TAG, "Attempting to unregister without ACTIVITY_RECOGNITION permission.")
            }

            Log.d(TAG, "Calling removeActivityTransitionUpdates...")
            try {
                val task = activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent)

                task.addOnSuccessListener {
                    Log.i(TAG, "Successfully unregistered from activity updates.")
                    // Cancel the PendingIntent itself
                    try {
                        pendingIntent.cancel()
                        Log.d(TAG, "Cancelled the PendingIntent.")
                    } catch (cancelEx: Exception) {
                        Log.w(TAG, "Exception while cancelling PendingIntent.", cancelEx)
                    }
                }
                task.addOnFailureListener { e ->
                    if (e is SecurityException) {
                        Log.e(TAG, "FAILED unregistration: SecurityException.", e)
                    } else {
                        Log.e(TAG, "FAILED to unregister from activity updates.", e)
                    }
                    // Still nullify local reference below
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException during removeActivityTransitionUpdates.", e)
            } finally {
                // Always clear the local reference after attempting removal
                activityUpdatePI = null
                Log.d(TAG, "Cleared local PendingIntent reference.")
            }
        } ?: run {
            // This block runs if activityUpdatePI was already null
            Log.w(TAG, "PendingIntent was already null. No unregistration attempt needed.")
        }
    }

    // --- Notification Management ---

    // Update the existing foreground notification content
    private fun updateNotification(currentActivity: ACTIVITY_TYPE?) {
        if (!isServiceRunning) {
            Log.w(TAG,"Service not running, skipping notification update.")
            return
        }
        Log.d(TAG, "Updating notification for activity: ${currentActivity?.title ?: "Unknown"}")
        val notification = createNotification(currentActivity)
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Ensure channel exists (though created in onCreate)
            createNotificationChannel()
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Catch errors during notification update (e.g., invalid context, rare issues)
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    // Create the notification object with current status
    private fun createNotification(currentActivity: ACTIVITY_TYPE?): Notification {
        // Determine the text to display based on the current activity
        val statusText = currentActivity?.title ?: "Tracking activity..." // Default text if null
        Log.d(TAG, "Creating notification with text: $statusText")

        // Intent to launch MainActivity when notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            // Flags to bring existing task to front or start new one if needed
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Or FLAG_ACTIVITY_SINGLE_TOP etc.
        }
        // Use FLAG_IMMUTABLE for PendingIntents when possible (Android M+)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT // Need to allow updates if reusing intent
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, // Request code for the PendingIntent
            notificationIntent,
            pendingIntentFlags
        )

        // Use a relevant icon for your app (replace ic_notification if needed)
        val icon = R.drawable.ic_launcher_foreground // Make sure this exists

        // Build the notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Activity Tracking") // Concise title
            .setContentText(statusText) // Dynamic status text
            .setSmallIcon(icon) // Small icon shown in status bar
            .setContentIntent(pendingIntent) // Action when tapped
            .setOnlyAlertOnce(true) // Don't repeatedly vibrate/sound on updates
            .setOngoing(true) // Makes it non-dismissible by swiping
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE) // Show immediately
            .setPriority(NotificationCompat.PRIORITY_LOW) // Reduce intrusiveness (no sound/vibration typically)
            // Optional: Add actions like a "Stop Tracking" button
            // .addAction(R.drawable.ic_stop, "Stop", stopServicePendingIntent)
            .build()
    }

    // Create the notification channel (required for Android O+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Activity Tracking" // User-visible channel name
            val channelDescription = "Notifications showing current detected activity"
            // Use LOW importance for less intrusive notifications (no sound/peeking)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, channelName, importance).apply {
                description = channelDescription
                // Optionally configure lights, vibration pattern (but LOW usually disables)
                enableLights(false)
                enableVibration(false)
                setShowBadge(false) // Don't show a badge for this ongoing notification
            }
            // Register the channel with the system
            val notificationManager: NotificationManager? =
                getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            if (notificationManager == null) {
                Log.e(TAG, "Failed to get NotificationManager service.")
                return
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel '$CHANNEL_ID' created or already exists.")
        }
    }

    // --- Service Stop Logic ---

    // Stops the service gracefully, ensuring cleanup
    private fun stopServiceInternal() {
        if (!isServiceRunning) {
            Log.w(TAG, "Stop command ignored: Service wasn't running.")
            return
        }
        Log.i(TAG, "Stopping service internally...")
        isServiceRunning = false // Mark as stopped first
        repository.updateIsTracking(isServiceRunning)

        // Unregister from activity transitions *before* stopping foreground/service
        unregisterFromActivityTransitions()

        // Cancel any ongoing coroutines managed by lifecycleScope automatically on service stop
        // lifecycleScope.cancel() // Usually not needed explicitly for LifecycleService

        // Stop the foreground state and remove the notification
        try {
            Log.d(TAG, "Stopping foreground state...")
            // Use the recommended way based on SDK version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                // Deprecated but necessary for older versions
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            Log.i(TAG, "Service stopped foreground state.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground state", e)
        }

        // Finally, request the system to stop the service itself
        stopSelf()
        Log.i(TAG, "stopSelf() called.")
    }
}
