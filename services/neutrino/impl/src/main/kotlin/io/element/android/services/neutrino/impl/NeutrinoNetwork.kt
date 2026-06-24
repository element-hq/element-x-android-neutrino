/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import java.net.Inet4Address
import java.net.InetAddress

/**
 * Port the embedded homeserver's HTTP listens on: the CS-API for the local
 * client (reached over loopback — see `DefaultEnterpriseService.defaultHomeserverList`)
 * and the loopback upstream the in-process sidecar forwards inbound federation to.
 */
internal const val NEUTRINO_PORT = 8008

/**
 * Public federation port the in-process low-bandwidth (CoAP/UDP) sidecar binds —
 * what peers' `server_name` resolves to. Distinct from [NEUTRINO_PORT] so the
 * sidecar ingress and the homeserver don't share a socket.
 */
internal const val NEUTRINO_FEDERATION_PORT = 8448

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
 * The advertised `server_name` is `host:federationPort` — the public port the
 * in-process CoAP sidecar's ingress binds and that peers resolve to. The
 * homeserver's own socket ([bindAddr]) stays on [port]; the local client reaches
 * it over loopback and the sidecar forwards inbound federation to it. With a LAN
 * [host] the server binds all interfaces (`0.0.0.0`) so peers can connect; with
 * no LAN address it falls back to loopback, so the local client still works
 * offline (no peer can reach it, but the device talks to its own server over
 * loopback regardless).
 */
internal fun serverIdentity(
    host: String?,
    port: Int = NEUTRINO_PORT,
    federationPort: Int = NEUTRINO_FEDERATION_PORT,
): NeutrinoEndpoint {
    return if (host == null) {
        NeutrinoEndpoint(serverName = "localhost:$federationPort", bindAddr = "localhost:$port")
    } else {
        NeutrinoEndpoint(serverName = "$host:$federationPort", bindAddr = "0.0.0.0:$port")
    }
}
