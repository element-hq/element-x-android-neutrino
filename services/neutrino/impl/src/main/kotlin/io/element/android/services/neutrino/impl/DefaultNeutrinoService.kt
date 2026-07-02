/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import android.content.Context
import android.net.ConnectivityManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.services.neutrino.api.NetworkAddressProvider
import io.element.android.services.neutrino.api.NeutrinoService
import io.element.neutrino.NeutrinoHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket

private const val READINESS_POLL_INTERVAL_MS = 100L
private const val READINESS_CONNECT_TIMEOUT_MS = 500

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
        val bindAddr = selectBindAddr(host)
        Timber.i("Starting embedded Neutrino server (bind $bindAddr)")
        // Bring the BLE backend up before starting the server: the server binds its
        // iroh-over-BLE federation transport during start, so blew must be
        // initialised first. The caller (the startup splash) has already gated this
        // on the BLE runtime permissions being granted.
        initBleNativeOnce()
        try {
            handle = io.element.neutrino.start(io.element.neutrino.NeutrinoConfig(
                // server_name is no longer supplied: the homeserver derives it from
                // its node identity and reports it back via handle.serverName().
                bindAddr = bindAddr,
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
        Timber.i("Neutrino server started as ${handle?.serverName()}")
        // The server is up. Reset its outbound federation backoff whenever the
        // device regains connectivity, so a returning-online device reconnects
        // promptly instead of waiting out a long backoff.
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        connectivityKicker = ConnectivityKicker(connectivityManager) {
            Timber.i("Connectivity regained; sending KickBackoff to Neutrino")
            handle?.kickBackoff()
        }.apply { register() }
    }

    override suspend fun awaitReady(timeoutMs: Long) {
        if (handle == null) return
        val ready = withTimeoutOrNull(timeoutMs) {
            while (!withContext(Dispatchers.IO) { isCsPortOpen() }) {
                delay(READINESS_POLL_INTERVAL_MS)
            }
            true
        } ?: false
        if (ready) {
            Timber.i("Neutrino client-server API is accepting connections")
        } else {
            Timber.w("Neutrino client-server API not reachable after ${timeoutMs}ms")
        }
    }

    // The listener binds asynchronously after `start()` returns; probe the CS port
    // with a short-timeout TCP connect ("connection refused" until it's bound).
    private fun isCsPortOpen(): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("localhost", NEUTRINO_PORT), READINESS_CONNECT_TIMEOUT_MS)
        }
        true
    } catch (t: Throwable) {
        // Expected while the listener is still binding (e.g. connection refused).
        Timber.v(t, "Neutrino CS port not open yet")
        false
    }

    override fun isRunning(): Boolean {
        return handle != null
    }

    override fun serverName(): String? = handle?.serverName()

    // Bootstrap blew's Android backend once, replicating what its Tauri
    // `BlewPlugin.load()` does (we don't use the Tauri plugin):
    //  1. NativeBle.initialise — registers the JavaVM + app Context with native
    //     `ndk_context` and runs `init_jvm` (caches the manager classes).
    //  2. BleCentralManager/BlePeripheralManager.init(context) — hands the app
    //     Context to the Kotlin managers, which their static
    //     `areBlePermissionsGranted()` reads; without this the permission check
    //     runs against a null context and fails even when perms are granted.
    // Failures are logged, not fatal — the server still runs (federation just has
    // no BLE path).
    private var bleNativeInitialised = false

    private fun initBleNativeOnce() {
        if (bleNativeInitialised) return
        try {
            val appContext = context.applicationContext
            io.element.neutrino.NativeBle.initialise(appContext)
            org.jakebot.blew.BleCentralManager.init(appContext)
            org.jakebot.blew.BlePeripheralManager.init(appContext)
            bleNativeInitialised = true
        } catch (t: Throwable) {
            Timber.e(t, "BLE native init failed")
        }
    }
}
