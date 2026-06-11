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

/**
 * The `server_name` + `bind_addr` pair for a Neutrino launch.
 *
 * - [serverName] is the federation identity baked into the user's MXID
 *   (`@localpart:serverName`). For the LAN demo it is a literal `ip:port`.
 * - [bindAddr] is the socket the server listens on.
 */
internal data class NeutrinoEndpoint(
    val serverName: String,
    val bindAddr: String,
)

/**
 * Build the federation endpoint for a launch.
 *
 * With a LAN [host] the server advertises that literal `host:port` identity and
 * binds all interfaces (`0.0.0.0`) so peers can connect. With no LAN address it
 * falls back to loopback, so the local client still works offline (no peer can
 * reach it, but the device talks to its own server over loopback regardless).
 */
internal fun serverIdentity(host: String?, port: Int = NEUTRINO_PORT): NeutrinoEndpoint {
    return if (host == null) {
        NeutrinoEndpoint(serverName = "localhost:$port", bindAddr = "localhost:$port")
    } else {
        NeutrinoEndpoint(serverName = "$host:$port", bindAddr = "0.0.0.0:$port")
    }
}
