/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.ftue.impl.state

import android.Manifest
import android.os.Build
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.ftue.api.state.FtueService
import io.element.android.features.ftue.api.state.FtueState
import io.element.android.features.lockscreen.api.LockScreenService
import io.element.android.libraries.core.coroutine.mapState
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.permissions.api.PermissionStateProvider
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.services.toolbox.api.sdk.BuildVersionSdkIntProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultFtueService(
    private val sdkVersionProvider: BuildVersionSdkIntProvider,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    private val permissionStateProvider: PermissionStateProvider,
    private val lockScreenService: LockScreenService,
    private val sessionVerificationService: SessionVerificationService,
    private val sessionPreferencesStore: SessionPreferencesStore,
) : FtueService {
    private val userNeedsToConfirmSessionVerificationSuccess = MutableStateFlow(false)

    val ftueStepStateFlow = MutableStateFlow<InternalFtueState>(InternalFtueState.Unknown)

    override val state = ftueStepStateFlow
        .mapState {
            when (it) {
                is InternalFtueState.Unknown -> FtueState.Unknown
                is InternalFtueState.Incomplete -> FtueState.Incomplete
                is InternalFtueState.Complete -> FtueState.Complete
            }
        }

    init {
        combine(
            sessionVerificationService.sessionVerifiedStatus.onEach { sessionVerifiedStatus ->
                if (sessionVerifiedStatus == SessionVerifiedStatus.NotVerified) {
                    // Ensure we wait for the user to confirm the session verified screen before going further
                    userNeedsToConfirmSessionVerificationSuccess.value = true
                }
            },
            userNeedsToConfirmSessionVerificationSuccess,
        ) { _, _ ->
            updateFtueStep()
        }
            .launchIn(sessionCoroutineScope)
    }

    fun updateFtueStep() = sessionCoroutineScope.launch {
        val step = getNextStep(null)
        ftueStepStateFlow.value = when (step) {
            null -> InternalFtueState.Complete
            else -> InternalFtueState.Incomplete(step)
        }
    }

    private suspend fun getNextStep(completedStep: FtueStep? = null): FtueStep? =
        when (completedStep) {
            null -> if (!isSessionVerificationStateReady()) {
                FtueStep.WaitingForInitialState
            } else {
                getNextStep(FtueStep.WaitingForInitialState)
            }
            FtueStep.WaitingForInitialState -> if (shouldSetDisplayName()) {
                FtueStep.SetDisplayName
            } else {
                getNextStep(FtueStep.SetDisplayName)
            }
            FtueStep.SetDisplayName -> if (isSessionNotVerified() || userNeedsToConfirmSessionVerificationSuccess.value) {
                FtueStep.SessionVerification
            } else {
                getNextStep(FtueStep.SessionVerification)
            }
            FtueStep.SessionVerification -> if (shouldAskNotificationPermissions()) {
                FtueStep.NotificationsOptIn
            } else {
                getNextStep(FtueStep.NotificationsOptIn)
            }
            FtueStep.NotificationsOptIn -> if (shouldDisplayLockscreenSetup()) {
                FtueStep.LockscreenSetup
            } else {
                getNextStep(FtueStep.LockscreenSetup)
            }
            // The analytics opt-in prompt is never displayed.
            FtueStep.LockscreenSetup -> getNextStep(FtueStep.AnalyticsOptIn)
            FtueStep.AnalyticsOptIn -> null
        }

    private fun isSessionVerificationStateReady(): Boolean {
        return sessionVerificationService.sessionVerifiedStatus.value != SessionVerifiedStatus.Unknown
    }

    private suspend fun isSessionNotVerified(): Boolean {
        return sessionVerificationService.sessionVerifiedStatus.value == SessionVerifiedStatus.NotVerified && !canSkipVerification()
    }

    private suspend fun canSkipVerification(): Boolean {
        return sessionPreferencesStore.isSessionVerificationSkipped().first()
    }

    // Prompt for a display name once, at first launch: it is embedded into the
    // BLE discovery advertisement (so nearby peers can find this user by name)
    // and persisted on the embedded homeserver via the profile API.
    private suspend fun shouldSetDisplayName(): Boolean {
        return !sessionPreferencesStore.isDisplayNamePromptCompleted().first()
    }

    private suspend fun shouldAskNotificationPermissions(): Boolean {
        return if (sdkVersionProvider.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val isPermissionDenied = permissionStateProvider.isPermissionDenied(permission).first()
            val isPermissionGranted = permissionStateProvider.isPermissionGranted(permission)
            !isPermissionGranted && !isPermissionDenied
        } else {
            false
        }
    }

    private suspend fun shouldDisplayLockscreenSetup(): Boolean {
        return lockScreenService.isSetupRequired().first()
    }

    fun onUserCompletedSessionVerification() {
        userNeedsToConfirmSessionVerificationSuccess.value = false
    }
}

sealed interface FtueStep {
    data object WaitingForInitialState : FtueStep
    data object SetDisplayName : FtueStep
    data object SessionVerification : FtueStep
    data object NotificationsOptIn : FtueStep
    data object AnalyticsOptIn : FtueStep
    data object LockscreenSetup : FtueStep
}
