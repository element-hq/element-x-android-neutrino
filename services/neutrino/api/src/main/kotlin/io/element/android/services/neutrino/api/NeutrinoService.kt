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
     * The fatal error the embedded homeserver exited with, or `null` while it is
     * starting or running normally. Startup can fail asynchronously a moment
     * after [start] returns — e.g. the `server_name` persisted in the database no
     * longer matches the identity the server is booting under — in which case
     * [awaitReady] gives up early and this returns the message to show the user.
     */
    fun lastError(): String?

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

    /**
     * Start mirroring federation datagrams into a Wireshark-readable pcap file in
     * host-owned external storage (so it can be pulled with `adb pull` without
     * root). The service owns the path. Re-arming while already capturing rotates
     * to a fresh file. Returns where it is writing, or why it could not start.
     */
    fun startCapture(): CaptureResult

    /**
     * Stop capturing and flush + close the file, which is ready to `adb pull` the
     * moment this returns. Returns the finalized file path, or `null` if no
     * capture was running.
     */
    fun stopCapture(): String?

    /**
     * Whether a federation pcap capture is currently running. Drives the toggle
     * state in developer settings.
     */
    fun isCapturing(): Boolean
}
