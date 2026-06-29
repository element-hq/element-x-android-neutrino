/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.services.neutrino.api.NetworkAddressProvider
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Enumerates the live, non-loopback network interfaces. Requires no permission.
 * Any [java.net.SocketException] from a transient interface query collapses to an
 * empty list, which [selectBindAddr] treats as "offline" (loopback fallback).
 */
@ContributesBinding(AppScope::class, binding = binding<NetworkAddressProvider>())
class DefaultNetworkAddressProvider : NetworkAddressProvider {
    override fun currentAddresses(): List<InetAddress> {
        return runCatchingExceptions {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
        }.getOrDefault(emptyList())
    }
}
