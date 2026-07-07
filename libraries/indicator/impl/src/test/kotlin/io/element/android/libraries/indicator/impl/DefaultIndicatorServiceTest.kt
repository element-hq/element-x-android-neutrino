/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.indicator.impl

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.verification.FakeSessionVerificationService
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultIndicatorServiceTest {
    @Test
    fun `test - showRoomListTopBarIndicator only reflects session verification`() = runTest {
        val sessionVerificationService = FakeSessionVerificationService()
        val sut = DefaultIndicatorService(
            sessionVerificationService = sessionVerificationService,
        )
        moleculeFlow(RecompositionMode.Immediate) {
            sut.showRoomListTopBarIndicator().value
        }.test {
            // The fake requires session verification by default; collapse the
            // leading pre-collection frame(s) and assert the settled value.
            assertThat(expectMostRecentItem()).isTrue()
            sessionVerificationService.emitNeedsSessionVerification(false)
            assertThat(awaitItem()).isFalse()
            sessionVerificationService.emitNeedsSessionVerification(true)
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `test - showSettingChatBackupIndicator is always suppressed on Neutrino`() = runTest {
        val sessionVerificationService = FakeSessionVerificationService()
        val sut = DefaultIndicatorService(
            sessionVerificationService = sessionVerificationService,
        )
        moleculeFlow(RecompositionMode.Immediate) {
            sut.showSettingChatBackupIndicator().value
        }.test {
            // Key backup / recovery don't apply to an on-device homeserver, so
            // the indicator is never shown.
            assertThat(awaitItem()).isFalse()
        }
    }
}
