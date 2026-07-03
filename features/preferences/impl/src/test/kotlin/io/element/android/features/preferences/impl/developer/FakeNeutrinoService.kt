/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer

import io.element.android.services.neutrino.api.DiscoveredPeer
import io.element.android.services.neutrino.api.NeutrinoService

class FakeNeutrinoService(
    private val serverNameResult: String? = "a1b2c3d4",
    private val discoveredPeersResult: () -> List<DiscoveredPeer> = { emptyList() },
) : NeutrinoService {
    override fun start() = Unit

    override suspend fun awaitReady(timeoutMs: Long) = Unit

    override fun isRunning(): Boolean = false

    override fun serverName(): String? = serverNameResult

    override fun discoveredPeers(): List<DiscoveredPeer> = discoveredPeersResult()
}
