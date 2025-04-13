package com.chromian.trackman.ui.screens // Make sure this package name is correct for your project

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log // Keep Logcat logging as well, it's useful
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState // Import for scrolling
import androidx.compose.foundation.verticalScroll // Import for scrolling
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily // For monospace font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // For font size
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chromian.trackman.service.MyPersistentService // Import your service
import com.chromian.trackman.ui.screens.permissions.PermissionRationale // Import your Rationale composable
import com.chromian.trackman.ui.screens.permissions.checkInitialPermission // Import helper
import com.chromian.trackman.ui.screens.permissions.openAppSettings // Import helper
import com.chromian.trackman.viewModel.VehicleStatusViewModel // Import your ViewModel
import kotlinx.coroutines.launch // For scrolling coroutine

// Keep TAG for Logcat
private const val TAG = "VehicleDetectScreen"
// MAX_LOG_LINES constant is now in ViewModel

@Composable
fun VehicleDetectionScreen(viewModel: VehicleStatusViewModel) { // Receive ViewModel
    val context = LocalContext.current
    var permissionRationaleState by remember { mutableStateOf(mapOf<String, Boolean>()) }

    // --- Keep Scroll State & Scope ---
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // --- Observe State from ViewModel ---
    val currentDetectedActivity by viewModel.currentActivity.collectAsStateWithLifecycle()
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val updateCounter by viewModel.updateCounter.collectAsStateWithLifecycle()
    // Observe logs from ViewModel
    val uiLogList by viewModel.uiLogs.collectAsStateWithLifecycle()


    // --- Permission State & Logic ---
    var hasNotificationPerm by remember { mutableStateOf(checkInitialPermission(context, Manifest.permission.POST_NOTIFICATIONS)) }
    var hasActivityRecPerm by remember { mutableStateOf(checkInitialPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // Log to Logcat AND ViewModel
            val resultMsg = "Permissions result: $permissions"
            Log.d(TAG, resultMsg)
            viewModel.addLogMessage(resultMsg)

            hasNotificationPerm = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPerm
            hasActivityRecPerm = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: hasActivityRecPerm

            val deniedPermissions = permissions.filterValues { !it }.keys
            if (deniedPermissions.isNotEmpty()) {
                val deniedMsg = "Denied permissions: $deniedPermissions"
                Log.w(TAG, deniedMsg)
                viewModel.addLogMessage("WARN: $deniedMsg") // Add prefix for level
            }

            // Update rationale state (remains the same)
            val newRationaleState = permissionRationaleState.toMutableMap()
            deniedPermissions.forEach { perm -> newRationaleState[perm] = true }
            permissionRationaleState = newRationaleState
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
            val msg = "ACTION: Attempting to start tracking service (Permissions OK)."
            Log.i(TAG, msg) // Keep Logcat Info
            viewModel.addLogMessage(msg) // Add to ViewModel logs

            val serviceIntent = Intent(context, MyPersistentService::class.java).apply {
                action = MyPersistentService.ACTION_START_TRACKING_SERVICE
            }
            ContextCompat.startForegroundService(context, serviceIntent) // Use ContextCompat
        } else {
            val msg = "ACTION: Cannot start service, requesting missing permissions: $missingPermissions"
            Log.w(TAG, msg) // Keep Logcat Warn
            viewModel.addLogMessage("WARN: $msg") // Add to ViewModel logs

            permissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    fun stopTrackingService() {
        val msg = "ACTION: Attempting to stop tracking service."
        Log.i(TAG, msg) // Keep Logcat Info
        viewModel.addLogMessage(msg) // Add to ViewModel logs

        val serviceIntent = Intent(context, MyPersistentService::class.java).apply {
            action = MyPersistentService.ACTION_STOP_TRACKING_SERVICE
        }
        ContextCompat.startForegroundService(context, serviceIntent) // Use ContextCompat
    }

    // --- Logging Effect for State Changes ---
    LaunchedEffect(isTracking, currentDetectedActivity, updateCounter) {
        val msg = "STATE_UPDATE -> isTracking: $isTracking | Activity: ${currentDetectedActivity?.title ?: "NULL"} | UpdateCount: $updateCounter"
        Log.d(TAG, msg) // Keep Logcat Debug
        viewModel.addLogMessage(msg) // Add to ViewModel logs
    }

    // --- Effect for Auto-Scrolling ---
    // Trigger scroll when the *number* of log lines changes
    LaunchedEffect(uiLogList.size) {
        if (uiLogList.isNotEmpty()) { // Only scroll if there are logs
            // Scroll suspend function needs to be called from a coroutine scope
            coroutineScope.launch {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }


    // --- UI Layout ---
    Scaffold { paddingValues ->
        Column( // Outer column for stacking main content and logs
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply scaffold padding first
        ) {
            // --- Main Content Area ---
            Column(
                modifier = Modifier
                    .weight(1f) // Takes up available space above logs
                    .padding(horizontal = 16.dp) // Padding for main content
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween // Pushes top/bottom elements apart
            ) {
                // Top Title
                Box(
                    modifier = Modifier
                        .padding(vertical = 16.dp), // Adjusted padding
                ) {
                    Text(
                        "Activity Tracking",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                // Center Status Display
                Column(
                    modifier = Modifier
                        .padding(bottom = 16.dp) // Padding below status
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center, // Center vertically
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isTracking) {
                            "${currentDetectedActivity?.title ?: "Detecting..."}"
                        } else {
                            "Tracking Off"
                        },
                        style = MaterialTheme.typography.displaySmall.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.W700
                        )
                    )
                    Text(
                        text = if (isTracking) "Current Activity" else "",
                        style = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Update Count: $updateCounter",
                        style = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center)
                    )
                }


                // Bottom Controls (Switch & Status)
                Column(
                    modifier = Modifier
                        .padding(bottom = 16.dp) // Padding below controls
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center, // Center vertically
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Switch(
                        checked = isTracking,
                        onCheckedChange = { shouldTrack ->
                            val switchMsg = "Switch toggled: $shouldTrack"
                            Log.d(TAG, switchMsg) // Logcat only for quick interaction maybe
                            viewModel.addLogMessage(switchMsg) // Log to ViewModel

                            if (shouldTrack) {
                                startTrackingService()
                            } else {
                                stopTrackingService()
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(4.dp)) // Reduced space
                    Text(
                        text = if (isTracking) "Tracking Enabled" else "Tracking Disabled",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } // End of Main Content Area

            // --- Permission Rationale Area --- (Placed above logs, below main content)
            if (missingPermissions.isNotEmpty()) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    PermissionRationale(
                        missingPermissions = missingPermissions,
                        rationaleState = permissionRationaleState,
                        onRequestAgain = {
                            val msg = "Rationale: Requesting permissions again."
                            Log.d(TAG, msg)
                            viewModel.addLogMessage(msg)
                            permissionsLauncher.launch(missingPermissions.toTypedArray())
                        },
                        onOpenSettings = {
                            val msg = "Rationale: Opening app settings."
                            Log.d(TAG, msg)
                            viewModel.addLogMessage(msg)
                            openAppSettings(context)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp)) // Space between rationale and logs
            }


            // --- UI Logging Area --- (Now reads from viewModel.uiLogs)
            Text(
                "Internal Logs:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
            Divider(modifier = Modifier.padding(horizontal = 16.dp)) // Separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 200.dp) // Give logs a defined, limited height
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(scrollState) // Make this Box scrollable
                    .padding(end = 8.dp) // Inner padding after scrollbar
            ) {
                Column { // Column to hold individual log lines
                    // Iterate over the list collected from the ViewModel
                    uiLogList.forEach { logMsg ->
                        Text(
                            text = logMsg,
                            fontFamily = FontFamily.Monospace, // Easier to read logs
                            fontSize = 11.sp, // Smaller font size
                            color = MaterialTheme.colorScheme.onSurfaceVariant // Slightly muted color
                        )
                    }
                }
            } // End of UI Logging Area
        } // End of Outer Column
    } // End of Scaffold
} // End of Composable function VehicleDetectionScreen