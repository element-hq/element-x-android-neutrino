/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.ftue.impl.setdisplayname

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// The name is advertised over BLE as `node_id ‖ display_name`; the embedded
// server caps the advert at this many UTF-8 bytes.
private const val MAX_DISPLAY_NAME_BYTES = 20

@AssistedInject
class SetDisplayNamePresenter(
    @Assisted private val callback: SetDisplayNameNode.Callback,
    private val matrixClient: MatrixClient,
    private val sessionPreferencesStore: SessionPreferencesStore,
) : Presenter<SetDisplayNameState> {
    @AssistedFactory
    interface Factory {
        fun create(callback: SetDisplayNameNode.Callback): SetDisplayNamePresenter
    }

    @Composable
    override fun present(): SetDisplayNameState {
        var displayName by remember { mutableStateOf("") }
        val submitAction = remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        val coroutineScope = rememberCoroutineScope()

        val trimmed = displayName.trim()
        val isTooLong = trimmed.toByteArray(Charsets.UTF_8).size > MAX_DISPLAY_NAME_BYTES
        val canSubmit = trimmed.isNotEmpty() &&
            !isTooLong &&
            submitAction.value !is AsyncAction.Loading

        fun handleEvent(event: SetDisplayNameEvents) {
            when (event) {
                is SetDisplayNameEvents.UpdateDisplayName -> displayName = event.displayName
                SetDisplayNameEvents.Submit -> if (canSubmit) {
                    coroutineScope.submit(trimmed, submitAction)
                }
                SetDisplayNameEvents.ClearError -> submitAction.value = AsyncAction.Uninitialized
            }
        }

        return SetDisplayNameState(
            displayName = displayName,
            isTooLong = isTooLong,
            canSubmit = canSubmit,
            submitAction = submitAction.value,
            eventSink = ::handleEvent,
        )
    }

    // Persist the name on the (embedded) homeserver — which is what re-advertises
    // it over BLE — then remember that we've prompted so it isn't asked again, and
    // advance the FTUE. On failure the state carries the error for the view to show.
    private fun CoroutineScope.submit(
        name: String,
        action: MutableState<AsyncAction<Unit>>,
    ) = launch {
        suspend {
            matrixClient.setDisplayName(name).getOrThrow()
            sessionPreferencesStore.setDisplayNamePromptCompleted(true)
        }.runCatchingUpdatingState(action)
        if (action.value is AsyncAction.Success) {
            callback.onDisplayNameSet()
        }
    }
}
