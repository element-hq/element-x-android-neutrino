/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.services.analyticsproviders.posthog

import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.meta.BuildMeta

@Inject
class PosthogEndpointConfigProvider(
    private val buildMeta: BuildMeta,
) {
    fun provide(): PosthogEndpointConfig? {
        return if (buildMeta.isEnterpriseBuild) {
            PosthogEndpointConfig(
                host = BuildConfig.POSTHOG_HOST,
                apiKey = BuildConfig.POSTHOG_APIKEY,
            ).takeIf {
                // Note that if the config is invalid, this module will not be included in the build.
                // So the configuration should be always valid.
                it.isValid
            }
        } else {
            null
        }
    }
}
