/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl.tunnel

import java.net.InetAddress

/**
 * Minimal read-only decoder for the IP packets read off the TUN file descriptor.
 *
 * Produces a one-line, log-safe summary of packet *metadata* only: IP version, L4
 * protocol, source/destination address and (for TCP/UDP) ports, and length. It
 * never reads or logs payload bytes, so no user content is exposed. IP addresses
 * and ports are network metadata and safe to log.
 *
 * For IPv6 it only reads ports when the fixed header's Next Header is directly
 * TCP/UDP; packets carrying extension headers report the extension header number
 * and omit ports. That is sufficient for the current logging-only spike.
 */
internal object IpPacket {
    private const val PROTO_ICMP = 1
    private const val PROTO_TCP = 6
    private const val PROTO_UDP = 17
    private const val PROTO_ICMPV6 = 58

    private const val IPV4_MIN_HEADER = 20
    private const val IPV6_HEADER = 40

    fun describe(buffer: ByteArray, length: Int): String {
        if (length < 1) return "empty packet"
        return when (val version = (buffer[0].toInt() ushr 4) and 0x0F) {
            4 -> describeIpv4(buffer, length)
            6 -> describeIpv6(buffer, length)
            else -> "unknown IP version $version ($length bytes)"
        }
    }

    private fun describeIpv4(buffer: ByteArray, length: Int): String {
        if (length < IPV4_MIN_HEADER) return "truncated IPv4 ($length bytes)"
        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        val protocol = buffer[9].toInt() and 0xFF
        val src = address(buffer, offset = 12, size = 4)
        val dst = address(buffer, offset = 16, size = 4)
        return format(protocol, "IPv4", src, dst, l4Offset = headerLength, buffer = buffer, length = length)
    }

    private fun describeIpv6(buffer: ByteArray, length: Int): String {
        if (length < IPV6_HEADER) return "truncated IPv6 ($length bytes)"
        val nextHeader = buffer[6].toInt() and 0xFF
        val src = address(buffer, offset = 8, size = 16)
        val dst = address(buffer, offset = 24, size = 16)
        return format(nextHeader, "IPv6", src, dst, l4Offset = IPV6_HEADER, buffer = buffer, length = length)
    }

    private fun format(
        protocol: Int,
        version: String,
        src: String,
        dst: String,
        l4Offset: Int,
        buffer: ByteArray,
        length: Int,
    ): String {
        val ports = l4Ports(buffer, l4Offset, protocol, length)
        val srcStr = ports?.let { "$src:${it.first}" } ?: src
        val dstStr = ports?.let { "$dst:${it.second}" } ?: dst
        return "$version ${protocolName(protocol)} $srcStr -> $dstStr ($length bytes)"
    }

    private fun l4Ports(buffer: ByteArray, l4Offset: Int, protocol: Int, length: Int): Pair<Int, Int>? {
        if (protocol != PROTO_TCP && protocol != PROTO_UDP) return null
        if (length < l4Offset + 4) return null
        return u16(buffer, l4Offset) to u16(buffer, l4Offset + 2)
    }

    private fun u16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    // The length guards in describeIpv4/describeIpv6 guarantee [size] is exactly 4
    // or 16 here, so InetAddress.getByAddress never throws UnknownHostException.
    private fun address(buffer: ByteArray, offset: Int, size: Int): String =
        InetAddress.getByAddress(buffer.copyOfRange(offset, offset + size)).hostAddress ?: "?"

    private fun protocolName(protocol: Int): String = when (protocol) {
        PROTO_ICMP -> "ICMP"
        PROTO_TCP -> "TCP"
        PROTO_UDP -> "UDP"
        PROTO_ICMPV6 -> "ICMPv6"
        else -> "proto $protocol"
    }
}
