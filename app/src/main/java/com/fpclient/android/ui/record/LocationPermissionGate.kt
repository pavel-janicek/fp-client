package com.fpclient.android.ui.record

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Runtime permission gate for track recording (Iteration 8a groundwork).
 *
 * Wraps its [content] behind ACCESS_FINE_LOCATION (+ COARSE, and POST_NOTIFICATIONS on
 * API 33+, so the ongoing notification is visible). Flow:
 *  1. permissions already granted -> content is shown;
 *  2. otherwise an explanation card is shown; pressing its button opens the system dialog
 *     via rememberLauncherForActivityResult(RequestMultiplePermissions) - preceded by an
 *     in-app rationale dialog when the system still allows showing one;
 *  3. if the user denied permanently ("don't ask again"), the only remaining path is the
 *     app's system settings page, offered as fallback.
 */
@Composable
fun LocationPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasRecordingPermissions(context)) }
    var showRationale by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    // Re-check on every resume: the permission dialog (or the system settings page)
    // takes the user away from the app, and the cached `granted` value would otherwise
    // stay stale — the Record screen would keep showing the explanation card even
    // though the permission was just granted. This is why recording only worked after
    // revisiting the screen (e.g. via the Me tab, which recreated the gate).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val nowGranted = hasRecordingPermissions(context)
                granted = nowGranted
                if (nowGranted) {
                    permanentlyDenied = false
                    showRationale = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        granted = hasRecordingPermissions(context)
        if (!granted) {
            // When the system no longer offers a rationale, it will never show its dialog
            // again - the settings page is the only way forward.
            permanentlyDenied = !shouldShowRationale(context)
        }
    }

    if (granted) {
        content()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Location access needed", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Recording a track requires your precise location, " +
                        "also while the screen is off. Your track is only shared " +
                        "when you choose to upload the finished activity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (permanentlyDenied) {
                    Text(
                        "The permission was denied permanently. Please enable location " +
                            "for this app in the system settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = {
                        if (shouldShowRationale(context) || permanentlyDenied) {
                            showRationale = true
                        } else {
                            launcher.launch(requiredPermissions())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Grant location access") }
                if (permanentlyDenied) {
                    OutlinedButton(
                        onClick = { openAppSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open app settings") }
                }
            }
        }
    }

    if (showRationale) {
        AlertDialog(
            // Resizable on large screens: not limited to the platform default width.
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showRationale = false },
            title = { Text("Why we need your location") },
            text = {
                Text(
                    "FP Client uses the device GPS to record your route while you exercise. " +
                        "Location is only read while a recording is running - you can stop it " +
                        "at any time. Nothing is uploaded without your confirmation.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRationale = false
                        launcher.launch(requiredPermissions())
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRationale = false }) { Text("Not now") }
            },
        )
    }
}

/** FINE (+ COARSE so the system dialog offers the approximate-only path). */
private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** Location permissions, plus POST_NOTIFICATIONS from API 33 so the FGS notification shows. */
private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        LOCATION_PERMISSIONS + Manifest.permission.POST_NOTIFICATIONS
    } else {
        LOCATION_PERMISSIONS
    }

private fun hasRecordingPermissions(context: Context): Boolean = LOCATION_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun shouldShowRationale(context: Context): Boolean {
    val activity = context as? Activity ?: return false
    return androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

