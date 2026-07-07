/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.developer.neutrino

sealed interface NeutrinoPeersEvents {
    /**
     * Re-read the discovery snapshot from the embedded homeserver. The registry
     * is not live, so refreshing is an explicit user action.
     */
    data object Refresh : NeutrinoPeersEvents
}
