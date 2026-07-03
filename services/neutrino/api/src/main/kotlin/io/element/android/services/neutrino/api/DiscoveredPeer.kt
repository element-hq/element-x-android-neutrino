/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.api

/**
 * A peer the embedded Neutrino homeserver has discovered out of band, over the
 * BLE mesh. Host-facing projection of the FFI `DiscoveredPeer`; the UI builds
 * user ids itself from [serverName], so the localpart is not carried.
 */
data class DiscoveredPeer(
    /**
     * The peer's federation `server_name` — its 64-char hex node id.
     */
    val serverName: String,
    /**
     * The display name the peer advertised over BLE.
     */
    val displayName: String,
    /**
     * Wall-clock milliseconds of the scan snapshot that last saw this peer.
     * Uniform across every peer in a single snapshot, so it doubles as the
     * "last scanned" time for the whole directory.
     */
    val lastSeenMs: Long,
)
