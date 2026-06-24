/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer

import android.content.Intent
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
    val packetTunnelEnabled: Boolean,
    // When non-null, the View must launch this VPN consent Intent and report the
    // result back via [DeveloperSettingsEvents.OnPacketTunnelConsentResult].
    val packetTunnelConsentIntent: Intent?,
    val eventSink: (DeveloperSettingsEvents) -> Unit
) {
    val showLoader = clearCacheAction is AsyncAction.Loading
}
