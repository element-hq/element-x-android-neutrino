/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer

import io.element.android.features.preferences.impl.developer.appsettings.AppDeveloperSettingsState
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import kotlinx.collections.immutable.ImmutableMap

data class DeveloperSettingsState(
    val appDeveloperSettingsState: AppDeveloperSettingsState,
    val cacheSize: AsyncData<String>,
    val databaseSizes: AsyncData<ImmutableMap<String, String>>,
    val clearCacheAction: AsyncAction<Unit>,
    val isEnterpriseBuild: Boolean,
    val showColorPicker: Boolean,
    // The embedded Neutrino homeserver's federation server_name (its node id), or
    // null until the server has started and resolved its identity.
    val neutrinoServerName: String?,
    // Whether a federation pcap capture is currently running (drives the toggle).
    val neutrinoCapturing: Boolean,
    // A human-readable status line for the capture toggle: where it is writing
    // while on, where the file was saved after stopping, or the failure reason.
    // Null before the toggle has been used this session.
    val neutrinoCaptureStatus: String?,
    val eventSink: (DeveloperSettingsEvents) -> Unit
) {
    val showLoader = clearCacheAction is AsyncAction.Loading
}
