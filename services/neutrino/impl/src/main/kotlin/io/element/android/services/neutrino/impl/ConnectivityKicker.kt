/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import android.net.ConnectivityManager
import android.net.Network

/**
 * Fires [onReconnect] whenever the device's default network is *regained* (an
 * offline→online transition), so the embedded homeserver can reset its outbound
 * federation backoff and retry promptly instead of idling in a long backoff.
 *
 * Wraps a single [ConnectivityManager.NetworkCallback]. [register] is idempotent;
 * the edge logic lives in [ReconnectDetector] so it can be tested without the
 * framework `ConnectivityManager`/`Network` types.
 */
internal class ConnectivityKicker(
    private val connectivityManager: ConnectivityManager,
    private val onReconnect: () -> Unit,
) {
    private val detector = ReconnectDetector()
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (detector.onAvailable()) {
                onReconnect()
            }
        }

        override fun onLost(network: Network) {
            detector.onLost()
        }
    }

    /** Start listening on the default network. A second call is a no-op. */
    fun register() {
        if (registered) return
        registered = true
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    /**
     * Stop listening. Safe when not registered. Provided for teardown/testing;
     * the app-singleton owner runs for the process lifetime and never calls it.
     */
    fun unregister() {
        if (!registered) return
        registered = false
        connectivityManager.unregisterNetworkCallback(callback)
    }
}

/**
 * Turns raw default-network availability transitions into "kick now?" decisions.
 *
 * A kick fires only on a genuine regain — an `onAvailable` that follows an
 * `onLost`. The initial `onAvailable` delivered at registration (when a network
 * is already present) must NOT kick: the server is freshly at base with nothing
 * to shortcut. A seamless handover (`onAvailable` with no intervening `onLost`)
 * likewise must not kick.
 *
 * Not synchronised: [ConnectivityKicker] confines every call to the single
 * `ConnectivityManager` callback thread, which delivers callbacks serially.
 */
internal class ReconnectDetector {
    private var wasOffline = false

    /** Record that the default network was lost. */
    fun onLost() {
        wasOffline = true
    }

    /**
     * Record that a default network is available. Returns true iff this is a
     * regain (a prior [onLost]), clearing the offline flag in that case.
     */
    fun onAvailable(): Boolean {
        if (!wasOffline) return false
        wasOffline = false
        return true
    }
}
