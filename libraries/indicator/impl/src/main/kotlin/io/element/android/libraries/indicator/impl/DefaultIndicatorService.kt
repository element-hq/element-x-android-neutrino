/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.indicator.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.indicator.api.IndicatorService
import io.element.android.libraries.matrix.api.verification.SessionVerificationService

@ContributesBinding(SessionScope::class)
class DefaultIndicatorService(
    private val sessionVerificationService: SessionVerificationService,
) : IndicatorService {
    @Composable
    override fun showRoomListTopBarIndicator(): State<Boolean> {
        val canVerifySession by sessionVerificationService.needsSessionVerification.collectAsState(initial = false)
        val settingChatBackupIndicator = showSettingChatBackupIndicator()

        return remember {
            derivedStateOf {
                canVerifySession || settingChatBackupIndicator.value
            }
        }
    }

    @Composable
    override fun showSettingChatBackupIndicator(): State<Boolean> {
        // Neutrino runs the homeserver on the same device as the client, so key
        // backup and recovery — which exist to restore your keys after losing
        // access to all your devices — do not apply. Never surface the chat
        // backup / "Get recovery key" indicator (the red dot on the profile
        // bubble and on the Settings → Encryption row).
        return remember { derivedStateOf { false } }
    }
}
