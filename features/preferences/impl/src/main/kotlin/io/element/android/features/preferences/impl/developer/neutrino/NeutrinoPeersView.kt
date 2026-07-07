/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer.neutrino

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Note: this is a debug screen, so hardcoded strings are OK (see DeveloperSettingsView).
@Composable
fun NeutrinoPeersView(
    state: NeutrinoPeersState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = "Discovered peers",
    ) {
        PreferenceCategory(title = "Discovery") {
            ListItem(
                headlineContent = { Text("Last Scanned") },
                supportingContent = {
                    Text(state.lastScannedMs?.let(::formatScanTime) ?: "never")
                },
            )
            ListItem(
                headlineContent = { Text("Refresh") },
                onClick = { state.eventSink(NeutrinoPeersEvents.Refresh) },
            )
        }
        PreferenceCategory(title = "Peers (${state.peers.size})") {
            if (state.peers.isEmpty()) {
                ListItem(
                    headlineContent = { Text("No peers discovered") },
                )
            } else {
                for (peer in state.peers) {
                    ListItem(
                        headlineContent = { Text(peer.displayName) },
                        supportingContent = { Text(peer.serverName) },
                    )
                }
            }
        }
    }
}

private fun formatScanTime(millis: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
}

@PreviewsDayNight
@Composable
internal fun NeutrinoPeersViewPreview(
    @PreviewParameter(NeutrinoPeersStateProvider::class) state: NeutrinoPeersState
) = ElementPreview {
    NeutrinoPeersView(
        state = state,
        onBackClick = {},
    )
}
