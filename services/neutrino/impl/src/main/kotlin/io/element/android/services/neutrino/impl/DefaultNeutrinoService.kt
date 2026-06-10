/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.services.neutrino.api.NeutrinoService
import io.element.neutrino.NeutrinoHandle
import timber.log.Timber

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<NeutrinoService>())
class DefaultNeutrinoService(
    @ApplicationContext private val context: Context,
    private val networkAddressProvider: NetworkAddressProvider,
) : NeutrinoService {
    var handle: NeutrinoHandle? = null

    override fun start() {
        if (handle != null) {
            return
        }
        val host = selectLanServerHost(networkAddressProvider.currentAddresses())
        val endpoint = serverIdentity(host)
        Timber.i("Starting embedded Neutrino server as %s (bind %s)", endpoint.serverName, endpoint.bindAddr)
        try {
            handle = io.element.neutrino.start(io.element.neutrino.NeutrinoConfig(
                serverName = endpoint.serverName,
                bindAddr = endpoint.bindAddr,
                localpart = "alice",
                storageDir = context.filesDir.resolve("data").path,
                outboundConcurrency = 4u,
            ))
        } catch (t: Throwable) {
            Timber.e(t, "Neutrino failed to start")
        }
    }

    override fun isRunning(): Boolean {
        return handle != null
    }
}
