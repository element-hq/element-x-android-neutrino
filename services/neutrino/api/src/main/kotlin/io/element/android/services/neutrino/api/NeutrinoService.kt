/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.api

/**
 * Interface to Neutrino, a lightweight, embedded homeserver.
 */
interface NeutrinoService {
    /**
     * Start the Neutrino embedded homeserver.
     */
    fun start()

    /**
     * Suspend until the embedded homeserver's client-server API is accepting
     * connections, or [timeoutMs] elapses.
     *
     * [start] returns as soon as the server thread is spawned, but the listener
     * binds asynchronously a moment later. Anything that makes a CS request
     * (auto-login, and then the profile/display-name write in onboarding) must
     * await this first, or it races the bind and fails with "connection refused".
     */
    suspend fun awaitReady(timeoutMs: Long = 15_000)

    /**
     * Returns true if the server is already running.
     */
    fun isRunning(): Boolean

    /**
     * The homeserver's federation `server_name` — its node identity (an ed25519
     * public key in hex), the domain of the local user's MXID. `null` until the
     * server has started and resolved its identity; stable for its lifetime after.
     */
    fun serverName(): String?

    /**
     * A single-shot snapshot of every peer discovered over the BLE mesh, sorted
     * by `(displayName, serverName)`. Not live — call again to refresh. A cheap
     * non-blocking in-memory read (like [serverName]). Empty on a build without
     * BLE discovery, before the first scan has landed any peers, or before the
     * server has started.
     */
    fun discoveredPeers(): List<DiscoveredPeer>
}
