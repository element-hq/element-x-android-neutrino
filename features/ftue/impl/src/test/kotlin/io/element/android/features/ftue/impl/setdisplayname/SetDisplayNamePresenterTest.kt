/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.ftue.impl.setdisplayname

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.preferences.test.InMemorySessionPreferencesStore
import io.element.android.tests.testutils.WarmUpRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SetDisplayNamePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private var isFinished = false

    @Test
    fun `initial state has an empty name and cannot submit`() = runTest {
        val presenter = createPresenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.displayName).isEmpty()
            assertThat(initialState.canSubmit).isFalse()
            assertThat(initialState.isTooLong).isFalse()
        }
    }

    @Test
    fun `entering a valid name enables submit`() = runTest {
        val presenter = createPresenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem().eventSink(SetDisplayNameEvents.UpdateDisplayName("Alice"))
            val state = awaitItem()
            assertThat(state.displayName).isEqualTo("Alice")
            assertThat(state.isTooLong).isFalse()
            assertThat(state.canSubmit).isTrue()
        }
    }

    @Test
    fun `a name over the byte budget cannot be submitted`() = runTest {
        val presenter = createPresenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem().eventSink(
                SetDisplayNameEvents.UpdateDisplayName("this name is definitely too long")
            )
            val state = awaitItem()
            assertThat(state.isTooLong).isTrue()
            assertThat(state.canSubmit).isFalse()
        }
    }

    @Test
    fun `submit persists the name, marks the prompt done and finishes`() = runTest {
        val matrixClient = FakeMatrixClient()
        val preferences = InMemorySessionPreferencesStore()
        val presenter = createPresenter(matrixClient = matrixClient, sessionPreferencesStore = preferences)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem().eventSink(SetDisplayNameEvents.UpdateDisplayName("Alice"))
            awaitItem().eventSink(SetDisplayNameEvents.Submit)
            var state = awaitItem()
            while (state.submitAction !is AsyncAction.Success) {
                state = awaitItem()
            }
            assertThat(matrixClient.setDisplayNameCalled).isTrue()
            assertThat(preferences.isDisplayNamePromptCompleted().first()).isTrue()
            assertThat(isFinished).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        matrixClient: MatrixClient = FakeMatrixClient(),
        sessionPreferencesStore: SessionPreferencesStore = InMemorySessionPreferencesStore(),
    ) = SetDisplayNamePresenter(
        callback = object : SetDisplayNameNode.Callback {
            override fun onDisplayNameSet() {
                isFinished = true
            }
        },
        matrixClient = matrixClient,
        sessionPreferencesStore = sessionPreferencesStore,
    )
}
