/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer.neutrino

import io.element.android.services.neutrino.api.DiscoveredPeer
import kotlinx.collections.immutable.ImmutableList

data class NeutrinoPeersState(
    // Wall-clock millis of the snapshot, shared by every peer in it (or null when
    // no peers have been discovered yet). Rendered as a single "Last Scanned" time.
    val lastScannedMs: Long?,
    val peers: ImmutableList<DiscoveredPeer>,
    val eventSink: (NeutrinoPeersEvents) -> Unit,
)
