/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.ftue.impl.setdisplayname

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.architecture.AsyncAction

open class SetDisplayNameStateProvider : PreviewParameterProvider<SetDisplayNameState> {
    override val values: Sequence<SetDisplayNameState>
        get() = sequenceOf(
            aSetDisplayNameState(),
            aSetDisplayNameState(displayName = "Alice", canSubmit = true),
            aSetDisplayNameState(
                displayName = "A really quite long display name",
                isTooLong = true,
            ),
            aSetDisplayNameState(displayName = "Alice", canSubmit = true, submitAction = AsyncAction.Loading),
        )
}

fun aSetDisplayNameState(
    displayName: String = "",
    isTooLong: Boolean = false,
    canSubmit: Boolean = false,
    submitAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    eventSink: (SetDisplayNameEvents) -> Unit = {},
) = SetDisplayNameState(
    displayName = displayName,
    isTooLong = isTooLong,
    canSubmit = canSubmit,
    submitAction = submitAction,
    eventSink = eventSink,
)
