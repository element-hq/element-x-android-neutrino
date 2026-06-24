/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer

import android.content.Intent
import io.element.android.services.neutrino.api.NeutrinoTunnel

class FakeNeutrinoTunnel(
    private val consentIntentResult: Intent? = null,
    initiallyRunning: Boolean = false,
) : NeutrinoTunnel {
    var startCount = 0
        private set
    var stopCount = 0
        private set
    private var running = initiallyRunning

    override fun consentIntent(): Intent? = consentIntentResult

    override fun start() {
        startCount++
        running = true
    }

    override fun stop() {
        stopCount++
        running = false
    }

    override fun isRunning(): Boolean = running
}
