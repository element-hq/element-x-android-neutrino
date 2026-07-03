/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer.neutrino

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.services.neutrino.api.DiscoveredPeer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

open class NeutrinoPeersStateProvider : PreviewParameterProvider<NeutrinoPeersState> {
    override val values: Sequence<NeutrinoPeersState>
        get() = sequenceOf(
            aNeutrinoPeersState(),
            aNeutrinoPeersState(
                lastScannedMs = SCAN_TIME_MS,
                peers = aDiscoveredPeerList(),
            ),
        )
}

private const val SCAN_TIME_MS = 1_767_430_800_000L

private fun aDiscoveredPeerList(): ImmutableList<DiscoveredPeer> = listOf(
    DiscoveredPeer(
        serverName = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
        displayName = "Alice",
        lastSeenMs = SCAN_TIME_MS,
    ),
    DiscoveredPeer(
        serverName = "f0e1d2c3b4a5968778695a4b3c2d1e0ff0e1d2c3b4a5968778695a4b3c2d1e0f",
        displayName = "Bob",
        lastSeenMs = SCAN_TIME_MS,
    ),
).toImmutableList()

internal fun aNeutrinoPeersState(
    lastScannedMs: Long? = null,
    peers: ImmutableList<DiscoveredPeer> = persistentListOf(),
    eventSink: (NeutrinoPeersEvents) -> Unit = {},
) = NeutrinoPeersState(
    lastScannedMs = lastScannedMs,
    peers = peers,
    eventSink = eventSink,
)
