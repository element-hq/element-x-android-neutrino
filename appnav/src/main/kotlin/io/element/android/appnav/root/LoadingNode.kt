/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.root

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.ContextCompat
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.node.node
import io.element.android.appnav.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The startup splash for the embedded Neutrino homeserver. It hard-gates startup on two
 * prerequisites: the BLE runtime permissions AND the Bluetooth adapter being fully ON.
 * The server binds its iroh-over-BLE federation transport when it starts, so
 * [onNeutrinoReadyToStart] (which the parent flow uses to trigger the start) is only
 * invoked once both are satisfied. While a permission is pending the spinner stays up;
 * while a prerequisite is missing the matching gate (grant permission / turn on Bluetooth)
 * is shown. The node remains until [io.element.android.appnav.RootFlowNode] routes onward
 * after the server starts and the headless login completes.
 */
fun loadingNode(
    buildContext: BuildContext,
    startupError: StateFlow<String?> = MutableStateFlow(null),
    onNeutrinoReadyToStart: () -> Unit = {},
): Node = node(buildContext) { modifier ->
    NeutrinoStartupView(startupError, onNeutrinoReadyToStart, modifier)
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
    startupError: StateFlow<String?>,
    onNeutrinoReadyToStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fatalError by startupError.collectAsState()
    var granted by remember { mutableStateOf(context.hasBlePermissions()) }
    var bluetoothOn by remember { mutableStateOf(context.isBluetoothOn()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = context.hasBlePermissions()
    }
    // The adapter-state receiver below flips `bluetoothOn` when the enable dialog
    // succeeds; we also refresh here in case the broadcast is missed.
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        bluetoothOn = context.isBluetoothOn()
    }
    // Track the adapter state live so the gate flips the instant the user turns
    // Bluetooth on, and re-blocks if it drops again while we are still on the splash.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    bluetoothOn = context.isBluetoothOn()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    // Request permissions once on entry if not already granted. On pre-31 `granted`
    // is already true (empty permission set), so we never launch. BLUETOOTH_CONNECT
    // (part of the set) is required before we can ask to enable the adapter, so we
    // gate on permissions first.
    LaunchedEffect(Unit) {
        if (!granted) {
            permissionLauncher.launch(blePermissions)
        }
    }
    // Signal readiness as soon as both prerequisites are met. The callback is
    // idempotent (a one-shot flag in the parent), so a later toggle is harmless.
    LaunchedEffect(granted, bluetoothOn) {
        if (granted && bluetoothOn) {
            onNeutrinoReadyToStart()
        }
    }
    when {
        !granted -> BlePermissionGate(
            onGrant = { permissionLauncher.launch(blePermissions) },
            onOpenSettings = { context.startActivity(context.appDetailsSettingsIntent()) },
            modifier = modifier,
        )
        !bluetoothOn -> BluetoothGate(
            onEnable = { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
            onOpenSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            modifier = modifier,
        )
        else -> LoadingView(modifier)
    }
    // A fatal startup failure the server reports asynchronously (e.g. a server_name
    // mismatch against the data already on disk) leaves the CS listener unbound, so
    // the spinner would otherwise stay up forever. Surface the message over it.
    var errorDismissed by remember { mutableStateOf(false) }
    fatalError?.let { error ->
        if (!errorDismissed) {
            ErrorDialog(
                content = error,
                onSubmit = { errorDismissed = true },
            )
        }
    }
}

private fun Context.hasBlePermissions(): Boolean = blePermissions.all {
    checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
}

// Only STATE_ON counts as usable: blew needs the classic adapter fully on, and treats
// the BLE-only transient STATE_BLE_ON as off. Reading the state can throw before the
// BLUETOOTH_CONNECT permission is granted; that is fine as we keep the user on the
// permission gate until then.
private fun Context.isBluetoothOn(): Boolean = try {
    val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    adapter?.state == BluetoothAdapter.STATE_ON
} catch (_: SecurityException) {
    false
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

@Composable
private fun BluetoothGate(
    onEnable: () -> Unit,
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
            text = stringResource(id = R.string.screen_neutrino_bluetooth_required),
            textAlign = TextAlign.Center,
        )
        Button(
            text = stringResource(id = R.string.screen_neutrino_bluetooth_enable),
            onClick = onEnable,
        )
        OutlinedButton(
            text = stringResource(id = R.string.screen_neutrino_ble_permission_settings),
            onClick = onOpenSettings,
        )
    }
}
