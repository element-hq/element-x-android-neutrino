/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl.tunnel

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.InetAddress

class IpPacketTest {
    @Test
    fun `decodes an IPv4 UDP packet with ports`() {
        val packet = byteArrayOf(
            0x45.toByte(), 0x00, 0x00, 0x1C, // version/ihl, dscp, total length = 28
            0x00, 0x00, 0x00, 0x00, // id, flags/frag
            0x40, 0x11, 0x00, 0x00, // ttl, protocol = 17 (UDP), checksum
            0x0A, 0x00, 0x00, 0x02, // src 10.0.0.2
            0x01, 0x02, 0x03, 0x04, // dst 1.2.3.4
            0xCA.toByte(), 0x6C, 0x01, 0xBB.toByte(), // src port 51820, dst port 443
            0x00, 0x08, 0x00, 0x00, // udp length, checksum
        )
        assertThat(IpPacket.describe(packet, packet.size))
            .isEqualTo("IPv4 UDP 10.0.0.2:51820 -> 1.2.3.4:443 (28 bytes)")
    }

    @Test
    fun `decodes an IPv4 ICMP packet without ports`() {
        val packet = byteArrayOf(
            0x45.toByte(), 0x00, 0x00, 0x14,
            0x00, 0x00, 0x00, 0x00,
            0x40, 0x01, 0x00, 0x00, // protocol = 1 (ICMP)
            0x0A, 0x00, 0x00, 0x02,
            0x01, 0x02, 0x03, 0x04,
        )
        assertThat(IpPacket.describe(packet, packet.size))
            .isEqualTo("IPv4 ICMP 10.0.0.2 -> 1.2.3.4 (20 bytes)")
    }

    @Test
    fun `decodes an IPv6 UDP packet with ports`() {
        val src = byteArrayOf(0xFD.toByte(), 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01)
        val dst = byteArrayOf(0xFD.toByte(), 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02)
        val packet = byteArrayOf(
            0x60, 0x00, 0x00, 0x00, // version 6
            0x00, 0x08, 0x11, 0x40, // payload length = 8, next header = 17 (UDP), hop limit
        ) + src + dst + byteArrayOf(
            0xCA.toByte(), 0x6C, 0x01, 0xBB.toByte(), // src port 51820, dst port 443
            0x00, 0x08, 0x00, 0x00,
        )
        // Compute the expected address strings via the platform so the assertion is
        // robust to IPv6 textual formatting differences.
        val srcStr = InetAddress.getByAddress(src).hostAddress
        val dstStr = InetAddress.getByAddress(dst).hostAddress
        assertThat(IpPacket.describe(packet, packet.size))
            .isEqualTo("IPv6 UDP $srcStr:51820 -> $dstStr:443 (${packet.size} bytes)")
    }

    @Test
    fun `reports a truncated IPv4 packet`() {
        val packet = byteArrayOf(0x45.toByte(), 0x00, 0x00, 0x05)
        assertThat(IpPacket.describe(packet, packet.size))
            .isEqualTo("truncated IPv4 (4 bytes)")
    }

    @Test
    fun `reports an unknown IP version`() {
        val packet = byteArrayOf(0x00)
        assertThat(IpPacket.describe(packet, packet.size))
            .isEqualTo("unknown IP version 0 (1 bytes)")
    }
}
