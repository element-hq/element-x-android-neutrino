/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer.neutrino

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.services.neutrino.api.NeutrinoService
import kotlinx.collections.immutable.toImmutableList

@Inject
class NeutrinoPeersPresenter(
    private val neutrinoService: NeutrinoService,
) : Presenter<NeutrinoPeersState> {
    @Composable
    override fun present(): NeutrinoPeersState {
        // Bumping this re-runs the snapshot read. The registry is not live, so a
        // snapshot is taken when the screen opens and again on each Refresh.
        var refreshCount by remember { mutableIntStateOf(0) }
        val peers = remember(refreshCount) {
            neutrinoService.discoveredPeers().toImmutableList()
        }

        fun handleEvent(event: NeutrinoPeersEvents) {
            when (event) {
                NeutrinoPeersEvents.Refresh -> refreshCount++
            }
        }

        return NeutrinoPeersState(
            // Every peer in a snapshot shares the same timestamp, so any one will do.
            lastScannedMs = peers.firstOrNull()?.lastSeenMs,
            peers = peers,
            eventSink = ::handleEvent,
        )
    }
}
