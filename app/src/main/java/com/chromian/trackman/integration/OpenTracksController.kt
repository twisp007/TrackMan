package com.chromian.trackman.integration

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier

// --- Constants for OpenTracks API ---

// Package Names (Choose the one relevant to the user's installed version)
object OpenTracksPackages {
    const val FDROID = "de.dennisguse.opentracks"
    const val PLAY_STORE = "de.dennisguse.opentracks.playStore"
    const val DEBUG = "de.dennisguse.opentracks.debug"
    const val NIGHTLY = "de.dennisguse.opentracks.nightly"
    // Add more if needed
}

// Class Names for API Actions
private const val BASE_CLASS_PATH = "de.dennisguse.opentracks.publicapi"
private const val START_RECORDING_CLASS = "$BASE_CLASS_PATH.StartRecording"
private const val STOP_RECORDING_CLASS = "$BASE_CLASS_PATH.StopRecording"
// private const val CREATE_MARKER_CLASS = "$BASE_CLASS_PATH.CreateMarker" // If needed later

// Intent Extras for StartRecording
private const val EXTRA_TRACK_NAME = "TRACK_NAME"
private const val EXTRA_TRACK_DESCRIPTION = "TRACK_DESCRIPTION"
private const val EXTRA_TRACK_CATEGORY = "TRACK_CATEGORY"
private const val EXTRA_TRACK_ICON = "TRACK_ICON"
// private const val EXTRA_STATS_TARGET_PACKAGE = "STATS_TARGET_PACKAGE" // If needed
// private const val EXTRA_STATS_TARGET_CLASS = "STATS_TARGET_CLASS"     // If needed

private const val TAG = "OpenTracksController"

/**
 * Enum to define the control action for OpenTracks.
 */
enum class OpenTracksAction {
    START, STOP
}

/**
 * Sends an Intent to OpenTracks to start or stop track recording via its Public API.
 *
 * IMPORTANT: The OpenTracks Public API must be enabled in the OpenTracks app settings
 * under "Settings -> Integrations -> Public API" for this to work.
 *
 * @param context The Android Context used to send the Intent.
 * @param action The desired action (START or STOP).
 * @param openTracksPackageName The specific package name of the installed OpenTracks variant
 * (e.g., OpenTracksPackages.PLAY_STORE).
 * @param trackName (Optional, START only) The name for the new track.
 * @param trackDescription (Optional, START only) The description for the new track.
 * @param trackCategory (Optional, START only) The category for the new track. If trackIcon is
 * not provided, this might determine the icon (localized).
 * @param trackIcon (Optional, START only) A non-localized identifier for the track icon.
 * See OpenTracks source (TrackIconUtils.java) for valid identifiers
 * (e.g., "activity_run", "activity_bike", "activity_walk").
 */
fun controlOpenTracksRecording(
    context: Context,
    action: OpenTracksAction,
    openTracksPackageName: String,
    trackName: String? = null,
    trackDescription: String? = null,
    trackCategory: String? = null, // Often the activity type like "Running", "Cycling"
    trackIcon: String? = null      // e.g., "activity_run", "activity_bike"
) {
    val intent = Intent()
    val targetClassName = when (action) {
        OpenTracksAction.START -> START_RECORDING_CLASS
        OpenTracksAction.STOP -> STOP_RECORDING_CLASS
    }

    intent.setClassName(openTracksPackageName, targetClassName)

    if (action == OpenTracksAction.START) {
        // Add optional extras only if they are provided
        trackName?.let { intent.putExtra(EXTRA_TRACK_NAME, it) }
        trackDescription?.let { intent.putExtra(EXTRA_TRACK_DESCRIPTION, it) }
        trackCategory?.let { intent.putExtra(EXTRA_TRACK_CATEGORY, it) }
        trackIcon?.let { intent.putExtra(EXTRA_TRACK_ICON, it) }
        Log.d(TAG, "Preparing START intent for $openTracksPackageName with extras:" +
                " Name='$trackName', Desc='$trackDescription', Cat='$trackCategory', Icon='$trackIcon'")
    } else {
        Log.d(TAG, "Preparing STOP intent for $openTracksPackageName")
    }

    try {
        context.startActivity(intent)
        Log.i(TAG, "Successfully sent ${action.name} intent to $openTracksPackageName")
        // Optionally show a success message
        // Toast.makeText(context, "OpenTracks ${action.name} signal sent.", Toast.LENGTH_SHORT).show()

    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "Error sending intent: OpenTracks app ($openTracksPackageName) not found or API class ($targetClassName) not available.", e)
        Toast.makeText(context, "OpenTracks not found or API not available/enabled.", Toast.LENGTH_LONG).show()
    } catch (e: SecurityException) {
        Log.e(TAG, "Error sending intent: Permission denied. Is the OpenTracks Public API enabled in its settings?", e)
        Toast.makeText(context, "Permission denied. Enable OpenTracks Public API?", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        // Catch other potential exceptions
        Log.e(TAG, "An unexpected error occurred when sending intent to OpenTracks.", e)
        Toast.makeText(context, "Failed to signal OpenTracks.", Toast.LENGTH_LONG).show()
    }
}