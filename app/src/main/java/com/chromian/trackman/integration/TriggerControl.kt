package com.chromian.trackman.integration

import android.content.Context
import com.chromian.trackman.data.repo.ACTIVITY_TYPE

fun triggerForActivity(context: Context, currentActivity: ACTIVITY_TYPE?) {
    if(currentActivity == ACTIVITY_TYPE.IN_VEHICLE) {
        // start OPEN TRACKS recording
        controlOpenTracksRecording(
            context = context,
            action = OpenTracksAction.START,
            openTracksPackageName = OpenTracksPackages.FDROID,
            trackCategory = "In Vehicle Detection"
        )

        // start GEO TRACKER recording
        controlGeoTrackerRecording(
            context = context,
            action = GeoTrackerAction.START,
            geoTrackerPackageName = GeoTrackerPackages.MAIN
        )

    } else {
        // stop OPEN TRACKS recording
        controlOpenTracksRecording(
            context = context,
            action = OpenTracksAction.STOP,
            openTracksPackageName = OpenTracksPackages.FDROID
        )

        // stop GEO TRACKER recording
        controlGeoTrackerRecording(
            context = context,
            action = GeoTrackerAction.STOP,
            geoTrackerPackageName = GeoTrackerPackages.MAIN
        )
    }
}