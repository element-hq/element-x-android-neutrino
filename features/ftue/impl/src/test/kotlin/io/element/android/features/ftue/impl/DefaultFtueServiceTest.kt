/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.ftue.impl

import android.os.Build
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.ftue.api.state.FtueState
import io.element.android.features.ftue.impl.state.DefaultFtueService
import io.element.android.features.ftue.impl.state.FtueStep
import io.element.android.features.ftue.impl.state.InternalFtueState
import io.element.android.features.lockscreen.api.LockScreenService
import io.element.android.features.lockscreen.test.FakeLockScreenService
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.test.verification.FakeSessionVerificationService
import io.element.android.libraries.permissions.api.PermissionStateProvider
import io.element.android.libraries.permissions.test.FakePermissionStateProvider
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.preferences.test.InMemorySessionPreferencesStore
import io.element.android.services.toolbox.test.sdk.FakeBuildVersionSdkIntProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test

class DefaultFtueServiceTest {
    @Test
    fun `given any check being false and session verification state being loaded, FtueState is Incomplete`() = runTest {
        val sessionVerificationService = FakeSessionVerificationService().apply {
            emitVerifiedStatus(SessionVerifiedStatus.Unknown)
        }
        val service = createDefaultFtueService(
            sessionVerificationService = sessionVerificationService,
        )

        service.state.test {
            // Verification state is unknown, we don't display the flow yet
            assertThat(awaitItem()).isEqualTo(FtueState.Unknown)

            // Verification state is known, we should display the flow if any check is false
            sessionVerificationService.emitVerifiedStatus(SessionVerifiedStatus.NotVerified)
            assertThat(awaitItem()).isEqualTo(FtueState.Incomplete)
        }
    }

    @Test
    fun `given all checks being true, FtueState is Complete`() = runTest {
        val sessionVerificationService = FakeSessionVerificationService()
        val permissionStateProvider = FakePermissionStateProvider(permissionGranted = true)
        val lockScreenService = FakeLockScreenService()
        val service = createDefaultFtueService(
            sessionVerificationService = sessionVerificationService,
            permissionStateProvider = permissionStateProvider,
            lockScreenService = lockScreenService,
        )

        sessionVerificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        permissionStateProvider.setPermissionGranted()
        lockScreenService.setIsPinSetup(true)
        service.updateFtueStep()
        service.state.test {
            assertThat(awaitItem()).isEqualTo(FtueState.Unknown)
            assertThat(awaitItem()).isEqualTo(FtueState.Complete)
        }
    }

    @Test
    @Ignore("Analytics no longer takes part in the Ftue flow, so this is now the same test as above.")
    fun `given all checks being true with no analytics, FtueState is Complete`() = runTest {
        val sessionVerificationService = FakeSessionVerificationService()
        val permissionStateProvider = FakePermissionStateProvider(permissionGranted = true)
        val lockScreenService = FakeLockScreenService()
        val service = createDefaultFtueService(
            sessionVerificationService = sessionVerificationService,
            permissionStateProvider = permissionStateProvider,
            lockScreenService = lockScreenService,
        )

        sessionVerificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        permissionStateProvider.setPermissionGranted()
        lockScreenService.setIsPinSetup(true)
        service.updateFtueStep()
        service.state.test {
            assertThat(awaitItem()).isEqualTo(FtueState.Unknown)
            assertThat(awaitItem()).isEqualTo(FtueState.Complete)
        }
    }

    @Test
    fun `display name prompt is shown until completed`() = runTest {
        val sessionVerificationService = FakeSessionVerificationService()
        val permissionStateProvider = FakePermissionStateProvider(permissionGranted = true)
        val lockScreenService = FakeLockScreenService()
        val preferences = InMemorySessionPreferencesStore(isDisplayNamePromptCompleted = false)
        val service = createDefaultFtueService(
            sessionVerificationService = sessionVerificationService,
            permissionStateProvider = permissionStateProvider,
            lockScreenService = lockScreenService,
            sessionPreferencesStore = preferences,
        )

        // Make every other check pass, so the display-name prompt is the only
        // remaining step.
        sessionVerificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        permissionStateProvider.setPermissionGranted()
        lockScreenService.setIsPinSetup(true)

        service.ftueStepStateFlow.test {
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Unknown)
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Incomplete(FtueStep.SetDisplayName))
            // Completing the prompt lets the flow finish.
            preferences.setDisplayNamePromptCompleted(true)
            service.updateFtueStep()
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Complete)
        }
    }

    @Test
    fun `traverse flow`() = runTest {
        val sessionVerificationService = FakeSessionVerificationService().apply {
            emitVerifiedStatus(SessionVerifiedStatus.NotVerified)
        }
        val permissionStateProvider = FakePermissionStateProvider(permissionGranted = false)
        val lockScreenService = FakeLockScreenService()
        val service = createDefaultFtueService(
            sessionVerificationService = sessionVerificationService,
            permissionStateProvider = permissionStateProvider,
            lockScreenService = lockScreenService,
        )

        service.ftueStepStateFlow.test {
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Unknown)
            // Session verification
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Incomplete(FtueStep.SessionVerification))
            sessionVerificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
            // User completes verification
            service.onUserCompletedSessionVerification()
            // Notifications opt in
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Incomplete(FtueStep.NotificationsOptIn))
            permissionStateProvider.setPermissionGranted()
            // Simulate event from NotificationsOptInNode.Callback.onNotificationsOptInFinished
            service.updateFtueStep()
            // Entering PIN code
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Incomplete(FtueStep.LockscreenSetup))
            lockScreenService.setIsPinSetup(true)
            // Simulate event from LockScreenEntryPoint.Callback.onSetupDone()
            service.updateFtueStep()
            // Final step: the analytics opt in step is never displayed
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Complete)
        }
    }

    @Test
    fun `if a check for a step is true, start from the next one`() = runTest {
        val sessionVerificationService = FakeSessionVerificationService()
        val permissionStateProvider = FakePermissionStateProvider(permissionGranted = false)
        val lockScreenService = FakeLockScreenService()
        val service = createDefaultFtueService(
            sessionVerificationService = sessionVerificationService,
            permissionStateProvider = permissionStateProvider,
            lockScreenService = lockScreenService,
        )

        // Skip first 3 steps
        sessionVerificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        permissionStateProvider.setPermissionGranted()
        lockScreenService.setIsPinSetup(true)

        service.ftueStepStateFlow.test {
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Unknown)
            // No analytics opt in step: the flow is over
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Complete)
        }
    }

    @Test
    fun `if version is older than 13 we don't display the notification opt in screen`() = runTest {
        val sessionVerificationService = FakeSessionVerificationService()
        val lockScreenService = FakeLockScreenService()

        val service = createDefaultFtueService(
            sdkIntVersion = Build.VERSION_CODES.M,
            sessionVerificationService = sessionVerificationService,
            lockScreenService = lockScreenService,
        )

        sessionVerificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        lockScreenService.setIsPinSetup(true)

        service.ftueStepStateFlow.test {
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Unknown)
            // The notification permission is neither granted nor denied, but the step is skipped
            assertThat(awaitItem()).isEqualTo(InternalFtueState.Complete)
        }
    }
}

internal fun TestScope.createDefaultFtueService(
    sessionVerificationService: SessionVerificationService = FakeSessionVerificationService(),
    permissionStateProvider: PermissionStateProvider = FakePermissionStateProvider(permissionGranted = false),
    lockScreenService: LockScreenService = FakeLockScreenService(),
    // Default to "prompt already done" so tests unrelated to the display-name step
    // skip it; the dedicated test below flips this to exercise the step.
    sessionPreferencesStore: SessionPreferencesStore = InMemorySessionPreferencesStore(
        isDisplayNamePromptCompleted = true,
    ),
    // First version where notification permission is required
    sdkIntVersion: Int = Build.VERSION_CODES.TIRAMISU,
) = DefaultFtueService(
    sessionCoroutineScope = backgroundScope,
    sessionVerificationService = sessionVerificationService,
    sdkVersionProvider = FakeBuildVersionSdkIntProvider(sdkIntVersion),
    permissionStateProvider = permissionStateProvider,
    lockScreenService = lockScreenService,
    sessionPreferencesStore = sessionPreferencesStore,
)
