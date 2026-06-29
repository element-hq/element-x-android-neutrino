/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.root

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.node.node
import io.element.android.appnav.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * The startup splash for the embedded Neutrino homeserver. It hard-gates startup on
 * the BLE runtime permissions: the server binds its iroh-over-BLE federation transport
 * when it starts, so [onBlePermissionsGranted] (which the parent flow uses to trigger
 * the start) is only invoked once the user has granted the Bluetooth permissions. While
 * the permission is pending the spinner stays up; while denied a permission prompt is
 * shown. The node remains until [io.element.android.appnav.RootFlowNode] routes onward
 * after the server starts and the headless login completes.
 */
fun loadingNode(
    buildContext: BuildContext,
    onBlePermissionsGranted: () -> Unit = {},
): Node = node(buildContext) { modifier ->
    NeutrinoStartupView(onBlePermissionsGranted, modifier)
}

// BLE runtime permissions only exist on Android 12 (API 31)+; below that they are
// install-time and need no prompt, so the array is empty and the gate passes through.
private val blePermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )
    } else {
        emptyArray()
    }

@Composable
private fun NeutrinoStartupView(
    onBlePermissionsGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasBlePermissions()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = context.hasBlePermissions()
    }
    // Request once on entry if not already granted. On pre-31 `granted` is already
    // true (empty permission set), so we never launch and pass straight through.
    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(blePermissions)
        }
    }
    // Signal readiness exactly once, as soon as the permissions are granted.
    LaunchedEffect(granted) {
        if (granted) {
            onBlePermissionsGranted()
        }
    }
    if (granted) {
        LoadingView(modifier)
    } else {
        BlePermissionGate(
            onGrant = { launcher.launch(blePermissions) },
            onOpenSettings = { context.startActivity(context.appDetailsSettingsIntent()) },
            modifier = modifier,
        )
    }
}

private fun Context.hasBlePermissions(): Boolean = blePermissions.all {
    checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
}

private fun Context.appDetailsSettingsIntent(): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", packageName, null),
)

@Composable
private fun LoadingView(
    modifier: Modifier = Modifier,
) = Box(
    modifier = modifier
        .fillMaxSize()
        .background(ElementTheme.colors.bgCanvasDefault),
    contentAlignment = Alignment.Center,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator()
        Text(text = stringResource(id = R.string.screen_loading_neutrino))
    }
}

@Composable
private fun BlePermissionGate(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    modifier = modifier
        .fillMaxSize()
        .background(ElementTheme.colors.bgCanvasDefault),
    contentAlignment = Alignment.Center,
) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(id = R.string.screen_neutrino_ble_permission_required),
            textAlign = TextAlign.Center,
        )
        Button(
            text = stringResource(id = R.string.screen_neutrino_ble_permission_grant),
            onClick = onGrant,
        )
        OutlinedButton(
            text = stringResource(id = R.string.screen_neutrino_ble_permission_settings),
            onClick = onOpenSettings,
        )
    }
}
