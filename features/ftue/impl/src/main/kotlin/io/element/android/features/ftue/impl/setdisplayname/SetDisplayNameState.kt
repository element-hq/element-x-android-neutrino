/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.ftue.impl.setdisplayname

import io.element.android.libraries.architecture.AsyncAction

/**
 * @param displayName the name currently entered.
 * @param isTooLong whether [displayName] exceeds the advertised byte budget.
 * @param canSubmit whether the name is valid and can be submitted.
 * @param submitAction progress of persisting the name to the homeserver.
 * @param eventSink callback used to send [SetDisplayNameEvents] to the presenter.
 */
data class SetDisplayNameState(
    val displayName: String,
    val isTooLong: Boolean,
    val canSubmit: Boolean,
    val submitAction: AsyncAction<Unit>,
    val eventSink: (SetDisplayNameEvents) -> Unit,
)
