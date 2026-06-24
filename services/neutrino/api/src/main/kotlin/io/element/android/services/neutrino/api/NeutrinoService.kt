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
     * Hand an established TUN file descriptor to the embedded homeserver, which reads
     * IP packets from it and logs a metadata-only summary of each.
     *
     * Ownership of [tunFd] transfers to native code, which closes it on
     * [detachTunnel] or when the server shuts down. The caller MUST therefore pass a
     * *detached* fd (`ParcelFileDescriptor.detachFd()`, not `.fd`) and MUST NOT close
     * it afterwards. The fd MUST also already be non-blocking.
     *
     * The reader's lifetime is bound to the server: this is a no-op (the fd is
     * closed) if the server is not running, and the reader is torn down if the server
     * later stops. Pair every [attachTunnel] with a [detachTunnel].
     *
     * @param tunFd a detached, non-blocking TUN file descriptor.
     * @param mtu the tunnel MTU.
     */
    fun attachTunnel(tunFd: Int, mtu: Int)

    /**
     * Stop reading from a previously [attachTunnel]ed descriptor and close it.
     * Idempotent: a no-op if no descriptor is attached.
     */
    fun detachTunnel()
}
