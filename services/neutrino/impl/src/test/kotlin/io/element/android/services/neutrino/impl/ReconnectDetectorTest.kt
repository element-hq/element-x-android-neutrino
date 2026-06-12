/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.neutrino.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReconnectDetectorTest {
    @Test
    fun `initial onAvailable while already online does not kick`() {
        val detector = ReconnectDetector()
        assertThat(detector.onAvailable()).isFalse()
    }

    @Test
    fun `onLost then onAvailable kicks`() {
        val detector = ReconnectDetector()
        detector.onLost()
        assertThat(detector.onAvailable()).isTrue()
    }

    @Test
    fun `onAvailable with no preceding onLost does not kick`() {
        val detector = ReconnectDetector()
        detector.onLost()
        assertThat(detector.onAvailable()).isTrue() // consume the regain
        // A second onAvailable with no new loss (e.g. a handover) must not re-kick.
        assertThat(detector.onAvailable()).isFalse()
    }

    @Test
    fun `each offline-online cycle kicks exactly once`() {
        val detector = ReconnectDetector()

        detector.onLost()
        assertThat(detector.onAvailable()).isTrue()
        assertThat(detector.onAvailable()).isFalse()

        detector.onLost()
        assertThat(detector.onAvailable()).isTrue()
        assertThat(detector.onAvailable()).isFalse()
    }

    @Test
    fun `repeated onLost before reconnect still kicks once`() {
        val detector = ReconnectDetector()
        detector.onLost()
        detector.onLost()
        assertThat(detector.onAvailable()).isTrue()
        assertThat(detector.onAvailable()).isFalse()
    }
}
