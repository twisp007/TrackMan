package com.chromian.trackman.integration

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// --- Constants for Geo Tracker API ---

object GeoTrackerPackages {
    // Package name for Geo Tracker app
    const val MAIN = "com.ilyabogdanovich.geotracker"
}

// Specific URI paths for Geo Tracker control actions
private const val GEO_TRACKER_URI_START = "geotracker://recorder/start"
private const val GEO_TRACKER_URI_STOP = "geotracker://recorder/stop"
private const val GEO_TRACKER_URI_PAUSE = "geotracker://recorder/pause"
private const val GEO_TRACKER_URI_RESUME = "geotracker://recorder/resume"

private const val TAG = "GeoTrackerController"

/**
 * Enum to define the control action for Geo Tracker.
 */
enum class GeoTrackerAction {
    START, STOP, PAUSE, RESUME
}

/**
 * Sends an Intent using ACTION_VIEW and a specific Geo Tracker URI to control recording.
 *
 * Assumes Geo Tracker app (package com.ilyabogdanovich.geotracker) is installed and
 * handles the 'geotracker://recorder/[action]' URI scheme.
 * NOTE: This method does not support passing track metadata (name, description, category).
 *
 * @param context The Android Context used to send the Intent.
 * @param action The desired action (START, STOP, PAUSE, RESUME).
 * @param geoTrackerPackageName The package name of the installed Geo Tracker app. Defaults to the common one.
 */
fun controlGeoTrackerRecording(
    context: Context,
    action: GeoTrackerAction,
    geoTrackerPackageName: String = GeoTrackerPackages.MAIN
) {
    // Select the correct URI string based on the action
    val uriString = when (action) {
        GeoTrackerAction.START -> GEO_TRACKER_URI_START
        GeoTrackerAction.STOP -> GEO_TRACKER_URI_STOP
        GeoTrackerAction.PAUSE -> GEO_TRACKER_URI_PAUSE
        GeoTrackerAction.RESUME -> GEO_TRACKER_URI_RESUME
    }

    Log.d(TAG, "Preparing ${action.name} intent with URI: $uriString for package: $geoTrackerPackageName")

    val controlUri = Uri.parse(uriString)
    val intent = Intent(Intent.ACTION_VIEW, controlUri)

    // Explicitly set the package to ensure the Intent goes only to Geo Tracker
    intent.setPackage(geoTrackerPackageName)

    // Add FLAG_ACTIVITY_NEW_TASK if starting from a non-Activity context (like a Service)
    if (context !is android.app.Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.d(TAG, "Adding FLAG_ACTIVITY_NEW_TASK as context is not an Activity.")
    }

    try {
        context.startActivity(intent)
        Log.i(TAG, "Successfully sent ${action.name} intent ($controlUri) to $geoTrackerPackageName")
        // Optional Toast - consider removing if called frequently from background service
        // Toast.makeText(context, "Geo Tracker ${action.name} signal sent.", Toast.LENGTH_SHORT).show()

    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "Error sending intent: Geo Tracker app ($geoTrackerPackageName) not found or doesn't handle URI scheme '$controlUri'.", e)
        Toast.makeText(context, "Geo Tracker not found or cannot handle action.", Toast.LENGTH_LONG).show()
    } catch (e: SecurityException) {
        Log.e(TAG, "Error sending intent: Security permission denied for URI '$controlUri'.", e)
        Toast.makeText(context, "Permission denied for Geo Tracker action.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        // Catch other potential exceptions
        Log.e(TAG, "An unexpected error occurred when sending intent to Geo Tracker.", e)
        Toast.makeText(context, "Failed to signal Geo Tracker.", Toast.LENGTH_LONG).show()
    }
}


// --- Example Usage in Jetpack Compose ---

@Composable
fun GeoTrackerControlScreen() {
    val context = LocalContext.current
    val targetPackage = GeoTrackerPackages.MAIN // Assuming standard package

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = {
            controlGeoTrackerRecording(
                context = context,
                action = GeoTrackerAction.START,
                geoTrackerPackageName = targetPackage
                // Note: trackName, description, category are no longer parameters
            )
        }) {
            Text("Start Geo Tracker Recording")
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            controlGeoTrackerRecording(
                context = context,
                action = GeoTrackerAction.STOP,
                geoTrackerPackageName = targetPackage
            )
        }) {
            Text("Stop Geo Tracker Recording")
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            controlGeoTrackerRecording(
                context = context,
                action = GeoTrackerAction.PAUSE,
                geoTrackerPackageName = targetPackage
            )
        }) {
            Text("Pause Geo Tracker Recording")
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            controlGeoTrackerRecording(
                context = context,
                action = GeoTrackerAction.RESUME,
                geoTrackerPackageName = targetPackage
            )
        }) {
            Text("Resume Geo Tracker Recording")
        }
    }
}