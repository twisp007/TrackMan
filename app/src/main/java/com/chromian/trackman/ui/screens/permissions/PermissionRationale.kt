package com.chromian.trackman.ui.screens.permissions

// ... other imports
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat


@Composable
fun PermissionRationale(
                         missingPermissions: List<String>,
                         rationaleState: Map<String, Boolean>,
                         onRequestAgain: () -> Unit,
                         onOpenSettings: () -> Unit
) {
    val showSettingsButton = missingPermissions.any { rationaleState[it] == true }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text("Permissions Required", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            missingPermissions.forEach { permission -> Text("• ${permissionToName(permission)}: Needed for tracking.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (showSettingsButton) { TextButton(onClick = onOpenSettings) { Text("Open Settings") }; Spacer(Modifier.width(8.dp)) }
                Button(onClick = onRequestAgain) { Text("Grant Permissions") }
            }
        }
    }
}
fun checkInitialPermission(context: Context, permission: String): Boolean {
    if (permission == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
fun openAppSettings(context: Context) {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null); context.startActivity(this) }
}
fun permissionToName(permission: String): String {
    return when (permission) {
        Manifest.permission.POST_NOTIFICATIONS -> "Notifications"; Manifest.permission.ACTIVITY_RECOGNITION -> "Activity Recognition"; else -> permission.substringAfterLast('.')
    }
}
