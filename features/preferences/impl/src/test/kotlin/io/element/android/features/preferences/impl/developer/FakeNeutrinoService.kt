/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer

import io.element.android.services.neutrino.api.CaptureResult
import io.element.android.services.neutrino.api.DiscoveredPeer
import io.element.android.services.neutrino.api.NeutrinoService

class FakeNeutrinoService(
    private val serverNameResult: String? = "a1b2c3d4",
    private val lastErrorResult: String? = null,
    private val discoveredPeersResult: () -> List<DiscoveredPeer> = { emptyList() },
    private val startCaptureResult: () -> CaptureResult = { CaptureResult.Started(A_CAPTURE_PATH) },
    // The location stopCapture reports (the real service copies to Downloads, so
    // this need not match the started path).
    private val stopCaptureResult: String? = "Download/$A_CAPTURE_NAME",
) : NeutrinoService {
    private var capturing = false

    override fun start() = Unit

    override suspend fun awaitReady(timeoutMs: Long) = Unit

    override fun isRunning(): Boolean = false

    override fun serverName(): String? = serverNameResult

    override fun lastError(): String? = lastErrorResult

    override fun discoveredPeers(): List<DiscoveredPeer> = discoveredPeersResult()

    override fun startCapture(): CaptureResult = startCaptureResult().also { result ->
        if (result is CaptureResult.Started) {
            capturing = true
        }
    }

    override fun stopCapture(): String? {
        if (!capturing) return null
        capturing = false
        return stopCaptureResult
    }

    override fun isCapturing(): Boolean = capturing
}

private const val A_CAPTURE_NAME = "neutrino-fed.pcap"
private const val A_CAPTURE_PATH = "/storage/emulated/0/Android/data/pkg/files/$A_CAPTURE_NAME"
