package com.badens.guitartuner.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.badens.guitartuner.R
import com.badens.guitartuner.ui.theme.TunerColors

/**
 * Shows [content] once microphone access is granted. Before that, explains why the app needs
 * the mic, then asks; if the user has permanently declined, offers the app's system settings.
 */
@Composable
fun MicPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasMicPermission()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
        denied = !isGranted
        // Android stops showing the dialog after repeated denials; only settings can help then.
        permanentlyDenied = !isGranted &&
            context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false
    }

    // Re-check on resume: the user may have changed the permission in system settings.
    LifecycleResumeEffect(Unit) {
        granted = context.hasMicPermission()
        onPauseOrDispose { }
    }

    if (granted) {
        content()
    } else {
        PermissionScreen(
            denied = denied,
            permanentlyDenied = permanentlyDenied,
            onAllow = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )
    }
}

@Composable
private fun PermissionScreen(
    denied: Boolean,
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TunerColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            color = TunerColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                when {
                    permanentlyDenied -> R.string.permission_blocked
                    denied -> R.string.permission_denied
                    else -> R.string.permission_rationale
                },
            ),
            color = TunerColors.TextSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.permission_open_settings)) }
        } else {
            Button(onClick = onAllow) { Text(stringResource(R.string.permission_allow)) }
        }
    }
}

private fun Context.hasMicPermission(): Boolean =
    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
