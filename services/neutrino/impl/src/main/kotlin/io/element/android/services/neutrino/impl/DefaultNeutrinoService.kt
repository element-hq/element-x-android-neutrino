/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import android.content.Context
import android.net.ConnectivityManager
import android.os.ParcelFileDescriptor
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.services.neutrino.api.NetworkAddressProvider
import io.element.android.services.neutrino.api.NeutrinoService
import io.element.neutrino.NeutrinoHandle
import timber.log.Timber

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<NeutrinoService>())
class DefaultNeutrinoService(
    @ApplicationContext private val context: Context,
    private val networkAddressProvider: NetworkAddressProvider,
) : NeutrinoService {
    var handle: NeutrinoHandle? = null

    // Held so its NetworkCallback is not garbage-collected. Registered once, on
    // first successful start; lives for the app-singleton's process lifetime.
    private var connectivityKicker: ConnectivityKicker? = null

    override fun start() {
        if (handle != null) {
            return
        }
        val host = selectLanServerHost(networkAddressProvider.currentAddresses())
        val endpoint = serverIdentity(host)
        Timber.i("Starting embedded Neutrino server as ${endpoint.serverName} (bind ${endpoint.bindAddr})")
        try {
            handle = io.element.neutrino.start(io.element.neutrino.NeutrinoConfig(
                serverName = endpoint.serverName,
                bindAddr = endpoint.bindAddr,
                // The single forced user. The login flow auto-logs-in as this localpart
                // (see LoginFlowNode's forced-provider path).
                localpart = "n",
                storageDir = context.filesDir.resolve("data").path,
                outboundConcurrency = 4u,
                // Run the in-process low-bandwidth (CoAP/UDP) federation sidecar on
                // the federation port; null would mean direct federation instead.
                lbFederationPort = NEUTRINO_FEDERATION_PORT.toUShort(),
            ))
        } catch (t: Throwable) {
            Timber.e(t, "Neutrino failed to start")
            return
        }
        // The server is up. Reset its outbound federation backoff whenever the
        // device regains connectivity, so a returning-online device reconnects
        // promptly instead of waiting out a long backoff.
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        connectivityKicker = ConnectivityKicker(connectivityManager) {
            Timber.i("Connectivity regained; sending KickBackoff to Neutrino")
            handle?.kickBackoff()
        }.apply { register() }
    }

    override fun isRunning(): Boolean {
        return handle != null
    }

    override fun attachTunnel(tunFd: Int, mtu: Int) {
        val handle = handle
        if (handle == null) {
            // Tun requires a running homeserver. Don't leak the fd the caller handed
            // us ownership of: adopt it and close it. (adoptFd takes ownership, so
            // close() releases the kernel fd.)
            Timber.w("Neutrino tunnel attach requested but server is not running; closing fd")
            ParcelFileDescriptor.adoptFd(tunFd).close()
            return
        }
        handle.startTunnel(tunFd, mtu.toUInt())
    }

    override fun detachTunnel() {
        handle?.stopTunnel()
    }
}
