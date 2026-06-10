/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import java.net.Inet4Address
import java.net.InetAddress

/** Port the embedded homeserver listens on, both for the local client and federation. */
internal const val NEUTRINO_PORT = 8008

/**
 * Pick the address other devices on the same LAN can reach this device on.
 *
 * Returns the first site-local IPv4 host (e.g. "192.168.1.5"), or null when only
 * loopback / link-local / IPv6 addresses are available (e.g. offline, or behind
 * carrier NAT with no private address). IPv6 is skipped so the resulting MXID
 * stays free of `[...]` bracket escaping. When several site-local IPv4 addresses
 * exist (e.g. WiFi + VPN) the first enumerated one wins — acceptable for the demo.
 */
internal fun selectLanServerHost(candidates: List<InetAddress>): String? {
    return candidates
        .asSequence()
        .filterIsInstance<Inet4Address>()
        .filterNot { it.isLoopbackAddress }
        .filterNot { it.isLinkLocalAddress }
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}
