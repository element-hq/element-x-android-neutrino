/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.api

import android.content.Intent

/**
 * Controls the Neutrino packet tunnel: an Android `VpnService` that captures this
 * application's IP traffic on a TUN interface.
 *
 * This is the transport-agnostic capture layer. For now it only *logs* the packets
 * it reads from the TUN file descriptor; forwarding the packets (over BLE, or into
 * the embedded Neutrino homeserver) is intentionally not wired up yet.
 */
interface NeutrinoTunnel {
    /**
     * Returns the system VPN consent [Intent] that MUST be launched (via an
     * `ActivityResultLauncher` / `startActivityForResult`) before [start], or
     * `null` if the user has already granted consent. On an `OK` result, call
     * [start]. Typical caller:
     *
     * ```
     * val intent = neutrinoTunnel.consentIntent()
     * if (intent == null) neutrinoTunnel.start() else consentLauncher.launch(intent)
     * // in the launcher callback, on RESULT_OK: neutrinoTunnel.start()
     * ```
     */
    fun consentIntent(): Intent?

    /** Start the tunnel foreground service. A no-op if it is already running. */
    fun start()

    /** Stop the tunnel foreground service. */
    fun stop()

    /** Whether the tunnel is currently established. */
    fun isRunning(): Boolean
}
