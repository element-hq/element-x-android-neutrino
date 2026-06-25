/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.developer.appsettings.AppDeveloperSettingsView
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import io.mhssn.colorpicker.ColorPickerDialog
import io.mhssn.colorpicker.ColorPickerType

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DeveloperSettingsView(
    state: DeveloperSettingsState,
    onOpenShowkase: () -> Unit,
    onPushHistoryClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.showLoader) {
        ProgressDialog()
    }
    // The packet tunnel needs system VPN consent before it can start. When the
    // presenter surfaces a consent Intent, launch it and report the result back.
    val packetTunnelConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        state.eventSink(DeveloperSettingsEvents.OnPacketTunnelConsentResult(result.resultCode == Activity.RESULT_OK))
    }
    LaunchedEffect(state.packetTunnelConsentIntent) {
        state.packetTunnelConsentIntent?.let { packetTunnelConsentLauncher.launch(it) }
    }
    // The embedded server's BLE federation transport needs the runtime Bluetooth
    // permissions (Android 12+). Requested when the tunnel is enabled; results are
    // observed via the native BLE smoke test's logcat output.
    val blePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op: granted perms take effect on the next BLE attempt */ }
    BackHandler(
        enabled = !state.showLoader,
        onBack = onBackClick,
    )
    PreferencePage(
        modifier = modifier,
        onBackClick = {
            if (!state.showLoader) {
                onBackClick()
            }
        },
        title = stringResource(id = CommonStrings.common_developer_options)
    ) {
        // Note: this is OK to hardcode strings in this debug screen.
        AppDeveloperSettingsView(
            state = state.appDeveloperSettingsState,
            onOpenShowkase = onOpenShowkase,
        )
        NotificationCategory(onPushHistoryClick)

        PreferenceCategory(title = "Neutrino") {
            ListItem(
                headlineContent = {
                    Text("Packet tunnel")
                },
                supportingContent = {
                    Text("Capture this app's traffic to the virtual subnet over a TUN interface (logs packets only)")
                },
                trailingContent = ListItemContent.Switch(
                    checked = state.packetTunnelEnabled,
                ),
                onClick = {
                    val enabling = !state.packetTunnelEnabled
                    if (enabling && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        blePermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_ADVERTISE,
                            )
                        )
                    }
                    state.eventSink(DeveloperSettingsEvents.SetPacketTunnelEnabled(enabling))
                }
            )
        }

        if (state.isEnterpriseBuild) {
            PreferenceCategory(title = "Theme") {
                ListItem(
                    headlineContent = {
                        Text("Change brand color")
                    },
                    onClick = {
                        state.eventSink(DeveloperSettingsEvents.SetShowColorPicker(true))
                    }
                )
                ListItem(
                    headlineContent = {
                        Text("Reset brand color")
                    },
                    onClick = {
                        state.eventSink(DeveloperSettingsEvents.ChangeBrandColor(null))
                    }
                )
            }
        }
        val cache = state.cacheSize
        PreferenceCategory(title = "Cache") {
            ListItem(
                headlineContent = { Text("Database sizes") },
                supportingContent = {
                    if (state.databaseSizes.isLoading()) {
                        Text("Computing...")
                    } else {
                        val dbSizes = state.databaseSizes.dataOrNull()
                        if (dbSizes != null && dbSizes.isNotEmpty()) {
                            Column {
                                for ((dbName, size) in dbSizes) {
                                    Text("$dbName: $size")
                                }
                            }
                        } else {
                            Text("Unknown")
                        }
                    }
                }
            )
            ListItem(
                headlineContent = {
                    Text("Vacuum stores")
                },
                onClick = {
                    state.eventSink(DeveloperSettingsEvents.VacuumStores)
                }
            )
            ListItem(
                headlineContent = {
                    Text("Clear cache")
                },
                trailingContent = if (state.cacheSize.isLoading() || state.clearCacheAction.isLoading()) {
                    ListItemContent.Custom {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .progressSemantics()
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    ListItemContent.Text(cache.dataOrNull().orEmpty())
                },
                onClick = {
                    if (state.clearCacheAction.isLoading().not()) {
                        state.eventSink(DeveloperSettingsEvents.ClearCache)
                    }
                }
            )
        }
    }
    ColorPickerDialog(
        show = state.showColorPicker,
        type = ColorPickerType.Classic(
            showAlphaBar = false,
        ),
        onDismissRequest = {
            state.eventSink(DeveloperSettingsEvents.SetShowColorPicker(false))
        },
        onPickedColor = {
            state.eventSink(DeveloperSettingsEvents.ChangeBrandColor(it))
        },
    )
}

@Composable
private fun NotificationCategory(onPushHistoryClick: () -> Unit) {
    PreferenceCategory(title = stringResource(id = R.string.screen_notification_settings_title)) {
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.troubleshoot_notifications_entry_point_push_history_title))
            },
            onClick = onPushHistoryClick,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun DeveloperSettingsViewPreview(
    @PreviewParameter(DeveloperSettingsStateProvider::class) state: DeveloperSettingsState
) = ElementPreview {
    DeveloperSettingsView(
        state = state,
        onOpenShowkase = {},
        onPushHistoryClick = {},
        onBackClick = {},
    )
}
