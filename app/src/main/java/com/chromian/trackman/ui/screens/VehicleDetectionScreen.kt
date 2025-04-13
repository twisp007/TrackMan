package com.chromian.trackman.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
// ... other imports
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Recommended for collecting flows in UI
import com.chromian.trackman.data.repo.VehicleStatusRepository
import com.chromian.trackman.integration.OpenTracksAction
import com.chromian.trackman.integration.OpenTracksPackages
import com.chromian.trackman.integration.controlOpenTracksRecording
import com.chromian.trackman.service.MyPersistentService
import com.chromian.trackman.ui.screens.permissions.PermissionRationale
import com.chromian.trackman.ui.screens.permissions.checkInitialPermission
import com.chromian.trackman.ui.screens.permissions.openAppSettings
import com.chromian.trackman.viewModel.VehicleStatusViewModel


@Composable
fun VehicleDetectionScreen(viewModel: VehicleStatusViewModel) {
    val context = LocalContext.current
    var permissionRationaleState by remember { mutableStateOf(mapOf<String, Boolean>()) }

    // --- Observe State from ViewModel ---
    val currentDetectedActivity by viewModel.currentActivity.collectAsStateWithLifecycle()

    // val isInVehicle by viewModel.isInVehicle.collectAsStateWithLifecycle()
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()

    // update count when receiver detects new activity
    val updateCounter by viewModel.updateCounter.collectAsStateWithLifecycle()


    // --- Permission State & Logic ---
    var hasNotificationPerm by remember {
        mutableStateOf(
            checkInitialPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }
    var hasActivityRecPerm by remember {
        mutableStateOf(
            checkInitialPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            Log.d("Permission", "Permissions result: $permissions")
            hasNotificationPerm =
                permissions[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPerm
            hasActivityRecPerm =
                permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: hasActivityRecPerm
            val deniedPermissions = permissions.filterValues { !it }.keys
            val newRationaleState = permissionRationaleState.toMutableMap()
            deniedPermissions.forEach { perm -> newRationaleState[perm] = true }
            permissionRationaleState = newRationaleState
            if (deniedPermissions.isNotEmpty()) Log.w("Permission", "Denied: $deniedPermissions")
        }
    )

    val requiredPermissions = remember {
        mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }.toTypedArray()
    }

    val missingPermissions = remember(hasNotificationPerm, hasActivityRecPerm) {
        requiredPermissions.filter {
            ContextCompat.checkSelfPermission(
                context,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }
    }

    // --- Service Control Functions ---
    fun startTrackingService() {
        if (missingPermissions.isEmpty()) {
            Log.d("VehicleDetectionScreen", "All permissions granted. Starting service.")
            val serviceIntent = Intent(context, MyPersistentService::class.java).apply {
                action = MyPersistentService.ACTION_START_TRACKING_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(
                serviceIntent
            )
            else context.startService(serviceIntent)
        } else {
            Log.w(
                "VehicleDetectionScreen",
                "Cannot start service, missing permissions: $missingPermissions"
            )
            permissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    fun stopTrackingService() {
        Log.d("VehicleDetectionScreen", "Stopping service.")
        val serviceIntent = Intent(context, MyPersistentService::class.java).apply {
            action = MyPersistentService.ACTION_STOP_TRACKING_SERVICE
        }
        context.startService(serviceIntent)
    }

    // --- UI Layout ---
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(vertical = 32.dp),
                ) {
                    Text(
                        "Activity Tracking",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isTracking) {
                            "${currentDetectedActivity?.title ?: "None"}"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.displaySmall.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.W700
                        )
                    )
                    Text(
                        text = if (isTracking) {
                            "Current Activity"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            textAlign = TextAlign.Center
                        )
                    )

                    Text(
                        text = "Update Count: ${updateCounter}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            textAlign = TextAlign.Center
                        )
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Switch(
                        checked = isTracking,
                        onCheckedChange = {
                            if (it) {
                                startTrackingService()
                            } else {
                                stopTrackingService()
                            }
                        },
                        /*colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outline
                        )*/
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (isTracking) "Tracking Enabled" else "Tracking Disabled",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (missingPermissions.isNotEmpty()) {
                // get permissions from the user
                PermissionRationale(
                    missingPermissions = missingPermissions,
                    rationaleState = permissionRationaleState,
                    onRequestAgain = { permissionsLauncher.launch(missingPermissions.toTypedArray()) },
                    onOpenSettings = { openAppSettings(context) }
                )
            }
        }
    }
}
