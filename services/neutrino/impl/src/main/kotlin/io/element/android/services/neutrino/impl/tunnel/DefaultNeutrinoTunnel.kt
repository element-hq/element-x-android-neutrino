/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl.tunnel

import android.content.Context
import android.content.Intent
import android.net.VpnService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.services.neutrino.api.NeutrinoTunnel

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<NeutrinoTunnel>())
class DefaultNeutrinoTunnel(
    @ApplicationContext private val context: Context,
) : NeutrinoTunnel {
    override fun consentIntent(): Intent? = VpnService.prepare(context)

    override fun start() = NeutrinoTunnelService.start(context)

    override fun stop() = NeutrinoTunnelService.stop(context)
}
