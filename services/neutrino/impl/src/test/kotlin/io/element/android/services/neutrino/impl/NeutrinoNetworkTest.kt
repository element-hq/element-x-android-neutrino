/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.InetAddress

class NeutrinoNetworkTest {
    @Test
    fun `selectLanServerHost prefers a site-local IPv4 over loopback`() {
        val candidates = listOf(
            InetAddress.getByName("127.0.0.1"),
            InetAddress.getByName("192.168.1.5"),
        )
        assertThat(selectLanServerHost(candidates)).isEqualTo("192.168.1.5")
    }

    @Test
    fun `selectLanServerHost skips link-local addresses`() {
        val candidates = listOf(
            InetAddress.getByName("169.254.1.1"),
            InetAddress.getByName("10.0.0.4"),
        )
        assertThat(selectLanServerHost(candidates)).isEqualTo("10.0.0.4")
    }

    @Test
    fun `selectLanServerHost skips IPv6 even when it is listed first`() {
        val candidates = listOf(
            InetAddress.getByName("2001:4860:4860::8888"),
            InetAddress.getByName("192.168.0.2"),
        )
        assertThat(selectLanServerHost(candidates)).isEqualTo("192.168.0.2")
    }

    @Test
    fun `selectLanServerHost returns null when only loopback is available`() {
        assertThat(selectLanServerHost(listOf(InetAddress.getByName("127.0.0.1")))).isNull()
    }

    @Test
    fun `selectLanServerHost returns null for an empty list`() {
        assertThat(selectLanServerHost(emptyList())).isNull()
    }

    @Test
    fun `serverIdentity advertises the LAN host on the federation port and binds all interfaces`() {
        val endpoint = serverIdentity(host = "192.168.1.5", port = 8008, federationPort = 8448)
        // server_name (the MXID federation identity) is the public CoAP ingress
        // port; the homeserver's own socket stays on the homeserver port.
        assertThat(endpoint.serverName).isEqualTo("192.168.1.5:8448")
        assertThat(endpoint.bindAddr).isEqualTo("0.0.0.0:8008")
    }

    @Test
    fun `serverIdentity falls back to loopback when there is no LAN host`() {
        val endpoint = serverIdentity(host = null, port = 8008, federationPort = 8448)
        assertThat(endpoint.serverName).isEqualTo("localhost:8448")
        assertThat(endpoint.bindAddr).isEqualTo("localhost:8008")
    }
}
