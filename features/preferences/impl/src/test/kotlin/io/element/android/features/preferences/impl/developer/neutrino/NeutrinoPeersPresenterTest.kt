/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer.neutrino

import com.google.common.truth.Truth.assertThat
import io.element.android.features.preferences.impl.developer.FakeNeutrinoService
import io.element.android.services.neutrino.api.DiscoveredPeer
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class NeutrinoPeersPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - snapshots the discovered peers and exposes their shared scan time`() = runTest {
        val peers = listOf(
            DiscoveredPeer(serverName = "aaa", displayName = "Alice", lastSeenMs = 1_000L),
            DiscoveredPeer(serverName = "bbb", displayName = "Bob", lastSeenMs = 1_000L),
        )
        val presenter = NeutrinoPeersPresenter(
            neutrinoService = FakeNeutrinoService(discoveredPeersResult = { peers }),
        )
        presenter.test {
            awaitItem().also { state ->
                assertThat(state.peers).containsExactlyElementsIn(peers).inOrder()
                assertThat(state.lastScannedMs).isEqualTo(1_000L)
            }
        }
    }

    @Test
    fun `present - empty registry has a null scan time`() = runTest {
        val presenter = NeutrinoPeersPresenter(
            neutrinoService = FakeNeutrinoService(discoveredPeersResult = { emptyList() }),
        )
        presenter.test {
            awaitItem().also { state ->
                assertThat(state.peers).isEmpty()
                assertThat(state.lastScannedMs).isNull()
            }
        }
    }

    @Test
    fun `present - Refresh re-reads the snapshot`() = runTest {
        val snapshots = ArrayDeque(
            listOf(
                emptyList(),
                listOf(DiscoveredPeer(serverName = "aaa", displayName = "Alice", lastSeenMs = 2_000L)),
            )
        )
        val presenter = NeutrinoPeersPresenter(
            neutrinoService = FakeNeutrinoService(
                discoveredPeersResult = { snapshots.removeFirst() },
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.peers).isEmpty()
            initialState.eventSink(NeutrinoPeersEvents.Refresh)
            awaitItem().also { state ->
                assertThat(state.peers).hasSize(1)
                assertThat(state.lastScannedMs).isEqualTo(2_000L)
            }
        }
    }
}
