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
 * Pick the socket the embedded homeserver's HTTP server binds.
 *
 * The federation `server_name` is no longer derived from the LAN address: the
 * homeserver derives it from its node identity (an ed25519 public key in hex) and
 * reports it back via [NeutrinoHandle.serverName][io.element.neutrino.NeutrinoHandle].
 * Peers are reached over the relay tunnel (BLE/LAN via iroh), not the LAN IPv4
 * address, so [host] only decides the bind scope: with a LAN [host] the server
 * binds all interfaces (`0.0.0.0`) so its CS-API is reachable for debugging; with
 * no LAN address it falls back to loopback. Either way the local client reaches
 * it over loopback and the in-process sidecar forwards inbound federation to it.
 */
internal fun selectBindAddr(host: String?, port: Int = NEUTRINO_PORT): String {
    return if (host == null) "localhost:$port" else "0.0.0.0:$port"
}
