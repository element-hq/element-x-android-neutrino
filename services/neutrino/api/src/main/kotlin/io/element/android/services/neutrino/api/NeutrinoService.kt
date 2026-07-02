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
     * Returns true if the server is already running.
     */
    fun isRunning(): Boolean

    /**
     * The homeserver's federation `server_name` — its node identity (an ed25519
     * public key in hex), the domain of the local user's MXID. `null` until the
     * server has started and resolved its identity; stable for its lifetime after.
     */
    fun serverName(): String?
}
