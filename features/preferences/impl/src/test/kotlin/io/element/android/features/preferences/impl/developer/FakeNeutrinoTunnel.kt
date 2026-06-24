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
) : NeutrinoTunnel {
    var startCount = 0
        private set
    var stopCount = 0
        private set

    override fun consentIntent(): Intent? = consentIntentResult

    override fun start() {
        startCount++
    }

    override fun stop() {
        stopCount++
    }
}
