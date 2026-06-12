/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.api

import java.net.InetAddress

/**
 * Source of the device's current network addresses. Abstracted so the launch
 * logic in the Neutrino service can be exercised with fake candidates.
 */
interface NetworkAddressProvider {
    fun currentAddresses(): List<InetAddress>
}
